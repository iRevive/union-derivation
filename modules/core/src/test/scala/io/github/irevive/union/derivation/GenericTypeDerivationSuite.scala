package io.github.irevive.union.derivation

class GenericTypeDerivationSuite extends munit.FunSuite {

  import GenericTypeDerivationSuite.*

  test("derive Typeclass for a simple union type") {
    type UnionType = Int | String
    val unionTypeGiven: Typeclass[UnionType] = UnionDerivation.derive[Typeclass, UnionType]

    assertEquals(unionTypeGiven.magic(42), "42")
    assertEquals(unionTypeGiven.magic("some-string-value"), "some-string-value")
  }

  test("derive Typeclass for a nested type Int") {
    type UnionType = GenericType[Int]
    val unionTypeGiven: Typeclass[UnionType] = summon[Typeclass[UnionType]]

    assertEquals(unionTypeGiven.magic(GenericType(42)), "42-generic")

  }
  test("derive Typeclass for a nested union type") {
    type UnionType = Int | String
    given Typeclass[UnionType]                       = UnionDerivation.derive[Typeclass, UnionType]
    val typeclass: Typeclass[GenericType[UnionType]] = summon[Typeclass[GenericType[UnionType]]]

    assertEquals(typeclass.magic(GenericType(42)), "42-generic")
    assertEquals(typeclass.magic(GenericType("some-string-value")), "some-string-value-generic")
  }

  test("derive Typeclass for a union of Int and GenericType[String]") {
    type UnionType = Int | GenericType[String]
    val typeclass: Typeclass[UnionType] = UnionDerivation.derive[Typeclass, UnionType]

    assertEquals(typeclass.magic(42), "42")
    assertEquals(typeclass.magic(GenericType("some-string-value")), "some-string-value-generic")
  }

}

object GenericTypeDerivationSuite {

  trait Typeclass[A] {
    def magic(a: A): String
  }

  given Typeclass[Int]    = _.toString
  given Typeclass[String] = identity(_)

  case class GenericType[A: Typeclass](value: A)
  given [A: Typeclass]: Typeclass[GenericType[A]] =
    a => summon[Typeclass[A]].magic(a.value) + "-generic"

}
