package io.github.irevive.union.derivation

class IsUnionSuite extends munit.FunSuite {

  test("derive IsUnion for a union type") {
    type UnionType = Int | String | Long

    summon[IsUnion[Int | String]]
    summon[IsUnion[UnionType]]
    summon[IsUnion[UnionType | Int & Double | UnionType]]
  }

  test("fail for a non-union type") {
    val expected =
      """
        |error:
        |Int is not a union type.
        |I found:
        |
        |    io.github.irevive.union.derivation.IsUnion.derived[Int]
        |
        |But macro expansion was stopped.
        |summon[IsUnion[Int]]
        |                   ^
        |""".stripMargin

    assertNoDiff(compileErrors("summon[IsUnion[Int]]"), expected)
  }

}
