package io.github.irevive.union.derivation

class UnionDerivationSuite extends munit.FunSuite {

  trait SimpleTrait[A]

  trait MultipleMethods[A] {
    def magic(a: A): Int
    def show(a: A): String
  }

  trait Typeclass[A] {
    def magic(a: A): Int
  }

  given Typeclass[Int]    = _ => 42
  given Typeclass[String] = _ => 22

  inline def deriveUnion[F[_], A]: F[A] = UnionDerivation.derive[F, A]

  test("derive Typeclass for a union type") {
    type UnionType = Int | String
    val unionTypeGiven: Typeclass[UnionType] = UnionDerivation.derive[Typeclass, UnionType]

    assertEquals(unionTypeGiven.magic(1), 42)
    assertEquals(unionTypeGiven.magic("some-string-value"), 22)
  }

  test("fail derivation for a non-union type") {
    val expected =
      """
        |error: Cannot derive a typeclass for the scala.Int. Only Union type is supported
        |    assertNoDiff(compileErrors("UnionDerivation.derive[Typeclass, Int]"), expected)
        |                             ^
        |
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[Typeclass, Int]"), expected)
  }

  test("fail derivation if an instance of a typeclass is missing for a member type") {
    val expected =
      """
        |error: no implicit values were found that match type UnionDerivationSuite.this.Typeclass[Double]
        |    assertNoDiff(compileErrors("UnionDerivation.derive[Typeclass, Int | String | Double]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[Typeclass, Int | String | Double]"), expected)
  }

  test("fail derivation if a typeclass has more than one abstract methods") {
    val expected =
      """
        |error: More than one abstract method detected in trait MultipleMethods: magic, show. Automatic derivation is impossible
        |    assertNoDiff(compileErrors("UnionDerivation.derive[MultipleMethods, Int | String]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[MultipleMethods, Int | String]"), expected)
  }

  test("fail derivation if a typeclass does not have abstract methods") {
    val expected =
      """
        |error: Cannot detect an abstract method in trait SimpleTrait. `scalacOptions += "-Yretain-trees"` may solve the issue
        |    assertNoDiff(compileErrors("UnionDerivation.derive[SimpleTrait, String | Int]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[SimpleTrait, String | Int]"), expected)
  }

}
