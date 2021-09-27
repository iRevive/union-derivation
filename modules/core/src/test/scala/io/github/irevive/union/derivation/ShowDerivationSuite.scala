package io.github.irevive.union.derivation

import scala.quoted.Quotes

class ShowDerivationSuite extends munit.FunSuite {

  test("derive Show for a union type") {
    type UnionType = Int | String | Long
    val unionTypeGiven: Show[UnionType] = Show.deriveUnion[UnionType]

    assertEquals(unionTypeGiven.show(1), "Int(1)")
    assertEquals(unionTypeGiven.show(2L), "Long(2)")
    assertEquals(unionTypeGiven.show("some-string-value"), "String(some-string-value)")
  }

  test("fail derivation for a non-union type") {
    val expected =
      """
        |error: Cannot derive a typeclass for the scala.Int. Only Union type is supported
        |    assertNoDiff(compileErrors("Show.deriveUnion[Int]"), expected)
        |                             ^
        |
        |""".stripMargin

    assertNoDiff(compileErrors("Show.deriveUnion[Int]"), expected)
  }

  test("fail derivation if an instance of a typeclass is missing for a member type") {
    val expected =
      """
        |error: no implicit values were found that match type io.github.irevive.union.derivation.Show[Double]
        |    assertNoDiff(compileErrors("Show.deriveUnion[Int | String | Double]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("Show.deriveUnion[Int | String | Double]"), expected)
  }

}

trait Show[A] {
  def show(a: A): String
}

object Show {
  given Show[Int]    = v => s"Int($v)"
  given Show[Long]   = v => s"Long($v)"
  given Show[String] = v => s"String($v)"

  inline def deriveUnion[A]: Show[A] = UnionDerivation.derive[Show, A]
}
