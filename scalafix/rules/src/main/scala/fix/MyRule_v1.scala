package fix

import scalafix.v1._
import scala.meta._

class MyRule_v1
    extends SemanticRule("MyRule_v1") {

  override def fix(implicit doc: SemanticDoc): Patch = {
    println(s"Tree.syntax: " + doc.tree.syntax)
    println(s"Tree.structure: " + doc.tree.structure)
    Patch.empty
  }

}
