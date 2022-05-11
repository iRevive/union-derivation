lazy val `union-derivation` = project
  .in(file("."))
  .settings(commonSettings)
  .settings(noPublishSettings)
  .aggregate(core, examples)

lazy val core = project
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(
    name                := "union-derivation-core",
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
  )

lazy val examples = project
  .in(file("modules/examples"))
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    name := "union-derivation-examples"
  )
  .dependsOn(core)

lazy val docs = project
  .in(file("modules/docs"))
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    name    := "union-derivation-docs",
    mdocIn  := baseDirectory.value / "src" / "main" / "mdoc" / "index.md",
    mdocOut := file("README.md"),
    mdocVariables := Map(
      "VERSION" -> version.value.replaceFirst("\\+.*", "")
    )
  )
  .dependsOn(core)

lazy val commonSettings = Seq(
  scalaVersion := "3.1.2",
  scalacOptions ++= Seq(
    "-source:future",
    "-no-indent", // let's be conservative for a while
    "-old-syntax",
    "-Yretain-trees"
  )
)

lazy val noPublishSettings = Seq(
  publish         := {},
  publishLocal    := {},
  publishArtifact := false,
  publish / skip  := true
)

inThisBuild(
  Seq(
    organization := "io.github.irevive",
    homepage     := Some(url("https://github.com/iRevive/union-derivation")),
    licenses     := List("MIT" -> url("https://opensource.org/licenses/MIT")),
    developers   := List(Developer("iRevive", "Maksim Ochenashko", "", url("https://github.com/iRevive")))
  )
)
