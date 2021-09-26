package io.github.irevive.union.derivation

import scala.quoted.Quotes

class ShowDerivationSuite extends munit.FunSuite {

  test("derive show for union type") {
    type UnionType = Int | String | Long
    val unionTypeGiven: Show[UnionType] = Show.deriveUnion[UnionType]

    assertEquals(unionTypeGiven.show(1), "Int(1)")
    assertEquals(unionTypeGiven.show(2L), "Long(2)")
    assertEquals(unionTypeGiven.show("some-string-value"), "String(some-string-value)")
  }

  test("fail compilation for non-union members") {
    val expected =
      """
        |error:
        |Found:    (1.0d : Double)
        |Required: Int | String | Long
        |
        |The following import might make progress towards fixing the problem:
        |
        |  import munit.Clue.generate
        |
        |Show.deriveUnion[Int | String | Long].show(1.0)
        |                                          ^
        |""".stripMargin

    assertNoDiff(compileErrors("Show.deriveUnion[Int | String | Long].show(1.0)"), expected)
  }

}
