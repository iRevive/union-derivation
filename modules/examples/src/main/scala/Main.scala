object Main {
  def main(args: Array[String]): Unit = {
    type UnionType = Int | Long | String

    val show: Show[UnionType] = Show.deriveUnion[UnionType]

    println(show.show(1))
    println(show.show(2L))
    println(show.show("3"))
  }
}
