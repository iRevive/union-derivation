package io.github.irevive.union.derivation

class ShowDerivationSuite extends munit.FunSuite {
  import ShowDerivationSuite.*

  test("derive Show for a union type") {
    type UnionType = Int | String | Long
    val unionTypeGiven: Show[UnionType] = Show.deriveUnion[UnionType]

    assertEquals(unionTypeGiven.show(1), "Int(1)")
    assertEquals(unionTypeGiven.show(2L), "Long(2)")
    assertEquals(unionTypeGiven.show("some-string-value"), "String(some-string-value)")
  }

  test("derive Show for a product type") {
    import Show.deriving.given

    final case class User(name: String, age: Int, extra: Int | String | Long)

    val user = User("Pablo", 22, 3L)
    assertEquals(summon[Show[User]].show(user), "User(name = String(Pablo), age = Int(22), extra = Long(3))")
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
        |error: no implicit values were found that match type io.github.irevive.union.derivation.ShowDerivationSuite.Show[Double]
        |    assertNoDiff(compileErrors("Show.deriveUnion[Int | String | Double]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("Show.deriveUnion[Int | String | Double]"), expected)
  }

  test("fail derivation if an instance of a typeclass is missing for a case class member") {
    val expected =
      "no given instance of type io.github.irevive.union.derivation.ShowDerivationSuite.Show[User] was found for parameter x of method summon in object Predef."

    val code =
      """
        import Show.deriving.given;
        final case class User(name: String, age: Int, extra: Int | Double | Long);
        summon[Show[User]]
      """

    assert(compileErrors(code).contains(expected))
  }

}

object ShowDerivationSuite {

  trait Show[A] {
    def show(a: A): String
  }

  object Show {
    given Show[Int]    = v => s"Int($v)"
    given Show[Long]   = v => s"Long($v)"
    given Show[String] = v => s"String($v)"

    inline def deriveUnion[A]: Show[A] = UnionDerivation.derive[Show, A]

    object deriving extends LowPriority {
      import scala.deriving.*
      import scala.compiletime.{erasedValue, summonInline}

      inline given derived[A](using m: Mirror.Of[A]): Show[A] = {
        val elemInstances = summonAll[m.MirroredElemTypes]
        inline m match {
          case s: Mirror.SumOf[A]     => showSum(s, elemInstances)
          case p: Mirror.ProductOf[A] => showProduct(p, elemInstances)
        }
      }

      private inline def summonAll[A <: Tuple]: List[Show[?]] =
        inline erasedValue[A] match {
          case _: EmptyTuple => Nil
          case _: (t *: ts)  => summonInline[Show[t]] :: summonAll[ts]
        }

      private def showA[A](a: A, show: Show[?]): String =
        show.asInstanceOf[Show[A]].show(a)

      private def showSum[A](s: Mirror.SumOf[A], elems: List[Show[?]]): Show[A] =
        new Show[A] {
          def show(a: A): String = showA(a, elems(s.ordinal(a)))
        }

      private def showProduct[A](p: Mirror.ProductOf[A], elems: List[Show[?]]): Show[A] =
        new Show[A] {
          def show(a: A): String = {
            val product = a.asInstanceOf[Product]

            product.productIterator
              .zip(product.productElementNames)
              .zip(elems.iterator)
              .map { case ((field, name), show) => s"$name = ${showA[Any](field, show)}" }
              .mkString(product.productPrefix + "(", ", ", ")")
          }
        }
    }

    trait LowPriority {
      inline given derivedUnion[A](using IsUnion[A]): Show[A] = UnionDerivation.derive[Show, A]
    }
  }

}
