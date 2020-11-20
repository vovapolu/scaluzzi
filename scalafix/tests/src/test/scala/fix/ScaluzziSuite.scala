package fix

import org.scalatest.FunSpecLike
import scalafix.testkit.AbstractSemanticRuleSuite

class ScaluzziSuite extends AbstractSemanticRuleSuite with FunSpecLike {
  runAllTests()
}
