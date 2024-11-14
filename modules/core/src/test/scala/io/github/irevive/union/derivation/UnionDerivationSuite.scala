package io.github.irevive.union.derivation

class UnionDerivationSuite extends munit.FunSuite {

  trait SimpleTrait[A]

  trait MultipleMethods[A] {
    def magic(a: A): Int
    def show(a: A): String
  }

  trait MultipleParamsSameType[A] {
    def magic(a: A, b: Int, c: A): Int
  }

  trait NoParams[A] {
    def magic: A
  }

  trait UnusedTypeParam[A] {
    def magic(a: Int): String
  }

  trait Curried[A] {
    def magic(a: A)(b: Int): String
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
        |error:
        |UnionDerivation cannot derive an instance of trait Typeclass for the type `scala.Int`.
        |Reason: only Union type is supported.
        |
        |    assertNoDiff(compileErrors("UnionDerivation.derive[Typeclass, Int]"), expected)
        |                             ^
        |
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[Typeclass, Int]"), expected)
  }

  test("fail derivation if an instance of a typeclass is missing for a member type") {
    val expected =
      """
        |error:
        |UnionDerivation cannot derive an instance of trait Typeclass for the type `scala.Int | scala.Predef.String | scala.Double`.
        |Reason: no implicit values were found that match type UnionDerivationSuite.this.Typeclass[Double]
        |
        |    assertNoDiff(compileErrors("UnionDerivation.derive[Typeclass, Int | String | Double]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[Typeclass, Int | String | Double]"), expected)
  }

  test("fail derivation if a typeclass has more than one abstract methods") {
    val expected =
      """
        |error:
        |UnionDerivation cannot derive an instance of trait MultipleMethods for the type `scala.Int | scala.Predef.String`.
        |Reason: more than one abstract method is detected: magic, show.
        |
        |    assertNoDiff(compileErrors("UnionDerivation.derive[MultipleMethods, Int | String]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[MultipleMethods, Int | String]"), expected)
  }

  test("fail derivation if a typeclass does not have abstract methods") {
    val expected =
      """
        |error:
        |UnionDerivation cannot derive an instance of trait SimpleTrait for the type `scala.Predef.String | scala.Int`.
        |Reason: cannot detect an abstract method in the typeclass.
        |Hint: `scalacOptions += "-Yretain-trees"` may solve the issue.
        |
        |    assertNoDiff(compileErrors("UnionDerivation.derive[SimpleTrait, String | Int]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[SimpleTrait, String | Int]"), expected)
  }

  test("fail derivation if a typeclass function has multiple polymorphic params of the same type") {
    val expected =
      """
        |error:
        |UnionDerivation cannot derive an instance of trait MultipleParamsSameType for the type `scala.Predef.String | scala.Int`.
        |Reason: the abstract method has multiple polymorphic params of the same parametrized type: a, c.
        |Hint: check the example below where the instance cannot be derived
        |
        |trait Typeclass[A] {
        |  def magic(a1: A, b: Int, a2: A): String
        |  //            ^              ^
        |  // Polymorphic type A appears in two positions
        |}
        |
        |    assertNoDiff(compileErrors("UnionDerivation.derive[MultipleParamsSameType, String | Int]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[MultipleParamsSameType, String | Int]"), expected)
  }

  test("fail derivation if a typeclass function doesn't have params") {
    val expected =
      """
        |error:
        |UnionDerivation cannot derive an instance of trait NoParams for the type `scala.Predef.String | scala.Int`.
        |Reason: the abstract method without the polymorphic param isn't supported.
        |Hint: check the example below where the instance cannot be derived
        |
        |trait Typeclass[A] {
        |  def magic: String
        |  //       ^
        |  // Polymorphic param of type A is missing
        |}
        |
        |    assertNoDiff(compileErrors("UnionDerivation.derive[NoParams, String | Int]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[NoParams, String | Int]"), expected)
  }

  test("fail derivation if a typeclass function doesn't use type parameter") {
    val expected =
      """
        |error:
        |UnionDerivation cannot derive an instance of trait UnusedTypeParam for the type `scala.Predef.String | scala.Int`.
        |Reason: the abstract method without the polymorphic param isn't supported.
        |Hint: check the example below where the instance cannot be derived
        |
        |trait Typeclass[A] {
        |  def magic(a: Int): String
        |  //              ^
        |  // Polymorphic param of type A is missing
        |}
        |
        |    assertNoDiff(compileErrors("UnionDerivation.derive[UnusedTypeParam, String | Int]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[UnusedTypeParam, String | Int]"), expected)
  }

  test("fail derivation if a typeclass function is curried") {
    val expected =
      """
        |error:
        |UnionDerivation cannot derive an instance of trait Curried for the type `scala.Predef.String | scala.Int`.
        |Reason: the curried abstract method isn't supported.
        |Hint: check the example below where the instance cannot be derived
        |
        |trait Typeclass[A] {
        |  def magic(a: A)(b: Int): String
        |  //             ^
        |  // Curried functions aren't supported
        |}
        |
        |    assertNoDiff(compileErrors("UnionDerivation.derive[Curried, String | Int]"), expected)
        |                             ^
        |""".stripMargin

    assertNoDiff(compileErrors("UnionDerivation.derive[Curried, String | Int]"), expected)
  }

  test("multi param - derive a typeclass for a union type") {
    trait MultipleParams[A] {
      def multipleParams(a: A, b: String, c: Int): String
    }

    given MultipleParams[Int]    = (a, b, c) => a.toString + "->" + b + "->" + c
    given MultipleParams[String] = (a, b, c) => a + "=>" + b + "=>" + c

    type UnionType = Int | String
    val unionTypeGiven: MultipleParams[UnionType] = UnionDerivation.derive[MultipleParams, UnionType]

    assertEquals(unionTypeGiven.multipleParams(1, "!", 42), "1->!->42")
    assertEquals(unionTypeGiven.multipleParams("some-string-value", "?", 42), "some-string-value=>?=>42")
  }

  test("multi param - polymorphic param in the end") {
    trait MultipleParams[A] {
      def multipleParams(b: String, c: Int, a: A): String
    }

    given MultipleParams[Int]    = (a, b, c) => a + "->" + b + "->" + c.toString
    given MultipleParams[String] = (a, b, c) => a + "=>" + b + "=>" + c

    type UnionType = Int | String
    val unionTypeGiven: MultipleParams[UnionType] = UnionDerivation.derive[MultipleParams, UnionType]

    assertEquals(unionTypeGiven.multipleParams("!", 42, 1), "!->42->1")
    assertEquals(unionTypeGiven.multipleParams("?", 42, "@"), "?=>42=>@")
  }

  test("derive an instance of a typeclass with an extension method") {
    trait Extension[A] {
      extension (a: A) def show: String
    }

    given Extension[Int]    = a => a.toString
    given Extension[String] = a => a.toString

    type UnionType = Int | String
    given unionTypeGiven: Extension[UnionType] = UnionDerivation.derive[Extension, UnionType]

    assertEquals((42: UnionType).show, "42")
    assertEquals(("42": UnionType).show, "42")
  }

  test("derive an instance of a typeclass with polymorphic type in the result position") {
    trait Poly[A] {
      def magic(a: A): A
    }

    given Poly[Int]    = a => a
    given Poly[String] = a => a

    type UnionType = Int | String
    given unionTypeGiven: Poly[UnionType] = UnionDerivation.derive[Poly, UnionType]

    assertEquals(unionTypeGiven.magic(42), 42)
    assertEquals(unionTypeGiven.magic("42"), "42")
  }

  test("derive an instance of a typeclass with function type in the result position") {
    trait Func[A] {
      def magic(a: A): Int => String
    }

    given Func[Int] with {
      def magic(a: Int): Int => String = (x: Int) => s"a->$a;x->$x"
    }
    given Func[String] with {
      def magic(a: String): Int => String = (x: Int) => s"a=>$a;x=>$x"
    }

    type UnionType = Int | String
    given unionTypeGiven: Func[UnionType] = UnionDerivation.derive[Func, UnionType]

    assertEquals(unionTypeGiven.magic(42)(1), "a->42;x->1")
    assertEquals(unionTypeGiven.magic("42")(1), "a=>42;x=>1")
  }

  test("derive an instance of a type class with polymorphic function type in the result position") {
    trait PolyFunctionType[A] {
      def magic(a: A): [B] => B => String
    }

    given PolyFunctionType[Int] with {
      def magic(a: Int): [B] => B => String =
        [B] => (b: B) => a.toString + "->" + b.toString
    }
    given PolyFunctionType[String] with {
      def magic(a: String): [B] => B => String =
        [B] => (b: B) => a + "=>" + b.toString
    }

    type UnionType = Int | String
    given unionTypeGiven: PolyFunctionType[UnionType] = UnionDerivation.derive[PolyFunctionType, UnionType]

    assertEquals(unionTypeGiven.magic(42).apply(1L), "42->1")
    assertEquals(unionTypeGiven.magic("42").apply(1L), "42=>1")
  }

}
