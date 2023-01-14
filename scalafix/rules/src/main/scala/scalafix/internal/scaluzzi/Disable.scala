package scalafix.internal.scaluzzi

import metaconfig.{Conf, Configured}

import scala.meta._
import scala.meta.transversers.Traverser
import scalafix.internal.util.SymbolOps
import scalafix.v1._

import scala.annotation.tailrec

object Disable {

  /**
    * A tree traverser to collect values with a custom context.
    * At every tree node, either builds a new Context or returns a new Value to accumulate.
    * To collect all accumulated values, use result(Tree).
    */
  class ContextTraverser[Value, Context](initContext: Context)(
      fn: PartialFunction[(Tree, Context), Either[Value, Context]])
      extends Traverser {
    private var context: Context = initContext
    private val buf = scala.collection.mutable.ListBuffer[Value]()

    private val liftedFn = fn.lift

    override def apply(tree: Tree): Unit = {
      liftedFn((tree, context)) match {
        case Some(Left(res)) =>
          buf += res
        case Some(Right(newContext)) =>
          val oldContext = context
          context = newContext
          super.apply(tree)
          context = oldContext
        case None =>
          super.apply(tree)
      }
    }

    def result(tree: Tree): List[Value] = {
      context = initContext
      buf.clear()
      apply(tree)
      buf.toList
    }
  }

  final class DisableSymbolMatcher(symbols: List[DisabledSymbol]) {
    def findMatch(symbol: Symbol): Option[DisabledSymbol] =
      symbols.find(_.matches(symbol))

    def unapply(tree: Tree)(implicit doc: SemanticDocument): Option[(Tree, DisabledSymbol)] =
      findMatch(tree.symbol).map(ds => (tree, ds))

    def unapply(symbol: Symbol): Option[(Symbol, DisabledSymbol)] =
      findMatch(symbol).map(ds => (symbol, ds))
  }
}

case class DisableDiagnostic(symbol: Symbol, details: String, position: Position) extends Diagnostic {
  override def message: String =
    s"${symbol.structure} is disabled $details"

}


final case class Disable(config: DisableConfig)
    extends SemanticRule("Disable") {

  import Disable._

  override def description: String =
    "Linter that reports an error on a configurable set of symbols."

  override def withConfiguration(config: Configuration): Configured[Rule] = {
    // should this use DisableConfig.default?
    config.conf.getOrElse("disable", "Disable")(this.config).map { newConfig => Disable(newConfig) }
  }

  private val safeBlocks = new DisableSymbolMatcher(config.allSafeBlocks)
  private val disabledSymbolInSynthetics =
    new DisableSymbolMatcher(config.ifSynthetic)

  private def createLintMessage(
      symbol: Symbol,
      disabled: DisabledSymbol,
      pos: Position,
      details: String = ""): Diagnostic = {
    //    val message = disabled.message.getOrElse(
    //      s"${symbol.signature.name} is disabled$details")
    //
    //    val id = disabled.id.getOrElse(symbol.signature.name)
    //
    //    errorCategory
    //      .copy(id = id)
    //      .at(message, pos)
    DisableDiagnostic(symbol, details = details, position = pos)
  }

  private def checkTree(implicit doc: SemanticDocument): Seq[Patch] = {
    def filterBlockedSymbolsInBlock(
        blockedSymbols: List[DisabledSymbol],
        block: Tree): List[DisabledSymbol] = {
       val symbolBlock = block.symbol
       val symbolsInMatchedBlocks =
         config.unlessInside.flatMap(
           u =>
             if (u.safeBlocks.exists(_.matches(symbolBlock))) u.symbols
             else List.empty)
       blockedSymbols.filterNot(symbolsInMatchedBlocks.contains)
     }

    @tailrec
    def skipTermSelect(term: Term): Boolean = term match {
      case _: Term.Name => true
      case Term.Select(q, _) => skipTermSelect(q)
      case _ => false
    }

    def handleName(t: Name, blockedSymbols: List[DisabledSymbol])
      : Either[Patch, List[DisabledSymbol]] = {
      val isBlocked = new DisableSymbolMatcher(blockedSymbols)
      val s = t.symbol
      isBlocked.findMatch(s).map { disabled =>
        SymbolOps.normalize(s) match {
          case g: Symbol if g.info.get.signature.toString() != "<init>" =>
            Left(Patch.lint(DisableDiagnostic(s, "", t.pos)).atomic) // XXX this is incorrect
          case _ => Right(blockedSymbols)
        }
      }.getOrElse {
        Right(blockedSymbols)
      }
    }

    new ContextTraverser(config.allDisabledSymbols)({
      case (_: Import, _) => Right(List.empty)
      case (Term.Select(q, name), blockedSymbols) if skipTermSelect(q) =>
        handleName(name, blockedSymbols)
      case (Type.Select(q, name), blockedSymbols) if skipTermSelect(q) =>
        handleName(name, blockedSymbols)
      case (
          Term.Apply(
            Term.Select(block @ safeBlocks(_, _), Term.Name("apply")),
            _
          ),
          blockedSymbols
          ) =>
        Right(filterBlockedSymbolsInBlock(blockedSymbols, block)) // <Block>.apply
      case (Term.Apply(block @ safeBlocks(_, _), _), blockedSymbols) =>
        Right(filterBlockedSymbolsInBlock(blockedSymbols, block)) // <Block>(...)
      case (_: Defn.Def, _) =>
        Right(config.allDisabledSymbols) // reset blocked symbols in def
      case (_: Term.Function, _) =>
        Right(config.allDisabledSymbols) // reset blocked symbols in (...) => (...)
      case (t: Name, blockedSymbols) =>
        handleName(t, blockedSymbols)
    }).result(doc.tree)
  }

  // XXX what goes here?
  //  private def checkSynthetics(implicit doc: SemanticDocument): Seq[Patch] = {
  //    for {
  //      synthetic <- ctx.index.synthetics
  //      ResolvedName(
  //        pos,
  //        disabledSymbolInSynthetics(symbol @ Symbol.Global(_, _), disabled),
  //        false
  //      ) <- synthetic.names
  //    } yield {
  //      val (details, caret) = pos.input match {
  //        case synth @ Input.Stream(InputSynthetic(_, input, start, end), _) =>
  //          // For synthetics the caret should point to the original position
  //          // but display the inferred code.
  //          s" and it got inferred as `${synth.text}`" ->
  //            Position.Range(input, start, end)
  //        case _ =>
  //          "" -> pos
  //      }
  //      Patch.lint(createLintMessage(symbol, disabled, caret, details)).atomic
  //    }
  //  }
  
  override def fix(implicit doc: SemanticDocument): Patch = {
    (checkTree).asPatch
  }
}
