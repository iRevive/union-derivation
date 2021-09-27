# union-derivation

[![Build Status](https://github.com/iRevive/union-derivation/workflows/CI/badge.svg)](https://github.com/iRevive/union-derivation/actions)
[![Maven Version](https://maven-badges.herokuapp.com/maven-central/io.github.irevive/union-derivation-core_3/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.irevive/union-derivation-core_3)

A micro-library to derive a typeclass for Scala 3 [Union types](https://dotty.epfl.ch/docs/reference/new-types/union-types.html).

## Quick start

To use `union-derivation` in an existing SBT project with Scala **3** or a later version, add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "io.github.irevive" %% "union-derivation-core" % "<version>"
scalacOptions += "-Yretain-trees" // important for the detection of an abstract method in a trait
```

## Usage example

```scala
import io.github.irevive.union.derivation.*

trait Show[A] {
  def show(a: A): String
}

object Show {
  given Show[Int]    = v => s"Int($v)"
  given Show[Long]   = v => s"Long($v)"
  given Show[String] = v => s"String($v)"

  inline def deriveUnion[A]: Show[A] = UnionDerivation.derive[Show, A]
}

object Main {
  def main(args: Array[String]): Unit = {
    type UnionType = Int | Long | String
    val show: Show[UnionType] = Show.deriveUnion[UnionType] // or UnionDerivation.derive[Show, UnionType]
    println(show.show(1))
    println(show.show(2L))
    println(show.show("3"))
  }
}
```

## Generated code

The library creates a set of if-else statements for the known types of the union.

The simplified version of the generated code:
```scala
val instance: Show[Int | String | Long] = UnionDerivation.derive[Show, Int | String | Long]

// expands into
val instance: Show[Int | String | Long] = { (value: Int | String | Long) =>
  if (value.isInstanceOf[Int]) summon[Show[Int]].show(value)
  else if (value.isInstanceOf[String]) summon[Show[String]].show(value)
  else if (value.isInstanceOf[Long]) summon[Show[Long]].show(value)
  else sys.error("Impossible")
}
```
