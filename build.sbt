// compiler plugins
addCompilerPlugin("org.scalameta" % "semanticdb-scalac" % "4.7.8" cross CrossVersion.full)

name := "scalac-scapegoat-plugin"
organization := "com.sksamuel.scapegoat"
description := "Scala compiler plugin for static code analysis"
homepage := Some(url("https://github.com/sksamuel/scapegoat"))
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/sksamuel/scapegoat"),
    "scm:git@github.com:sksamuel/scapegoat.git",
    Some("scm:git@github.com:sksamuel/scapegoat.git")
  )
)
developers := List(
  Developer(
    "sksamuel",
    "sksamuel",
    "@sksamuel",
    url("https://github.com/sksamuel")
  )
)

scalaVersion := "2.13.10"
crossScalaVersions := Seq("2.12.16", "2.12.17", "2.13.9", "2.13.10")
autoScalaLibrary := false
crossVersion := CrossVersion.full
crossTarget := {
  // workaround for https://github.com/sbt/sbt/issues/5097
  target.value / s"scala-${scalaVersion.value}"
}

// https://github.com/sksamuel/scapegoat/issues/298
ThisBuild / useCoursier := false

val scalac13Options = Seq(
  "-Xlint:inaccessible",
  "-Xlint:infer-any",
  "-Xlint:strict-unsealed-patmat",
  "-Yrangepos",
  "-Ywarn-unused",
  "-Xsource:3"
)
val scalac12Options = Seq(
  "-Ywarn-inaccessible",
  "-Ywarn-infer-any",
  "-Xlint:nullary-override",
  "-Xmax-classfile-name",
  "254"
)

scalacOptions := {
  val common = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-encoding",
    "utf8",
    "-Xlint",
    "-Xlint:adapted-args",
    "-Xlint:nullary-unit"
  )
  common ++ (scalaBinaryVersion.value match {
    case "2.12" => scalac12Options
    case "2.13" => scalac13Options
  })
}
javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

// because that's where "PluginRunner" is
Compile / console / fullClasspath ++= (Test / fullClasspath).value
console / initialCommands := s"""
import com.sksamuel.scapegoat._
def check(code: String) = {
  val runner = new PluginRunner { val inspections = ScapegoatConfig.inspections }
  // Not sufficient for reuse, not sure why.
  // runner.reporter.reset
  val c = runner compileCodeSnippet code
  val feedback = c.scapegoat.feedback
  feedback.warnings map (x => "%-40s %s %s".format(x.text, x.explanation, x.snippet.getOrElse(""))) foreach println
  feedback
}
"""

libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect"  % scalaVersion.value % "provided",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
  "org.scala-lang.modules" %% "scala-xml" % "2.1.0" excludeAll ExclusionRule(organization = "org.scala-lang"),
  "org.scala-lang.modules" %% "scala-collection-compat" % "2.10.0" excludeAll ExclusionRule(organization =
    "org.scala-lang"
  ),
  "org.scala-lang" % "scala-compiler" % scalaVersion.value % "test",
  "org.scalatest" %% "scalatest"      % "3.2.16"           % "test",
  "org.mockito"    % "mockito-all"    % "1.10.19"          % "test",
  "joda-time"      % "joda-time"      % "2.12.5"           % "test",
  "org.joda"       % "joda-convert"   % "2.2.3"            % "test",
  "org.slf4j"      % "slf4j-api"      % "2.0.7"            % "test"
)

// Test
Test / run / fork := true
Test / logBuffered := false
Test / parallelExecution := false

// ScalaTest reporter config:
// -o - standard output,
// D - show all durations,
// T - show reminder of failed and cancelled tests with short stack traces,
// F - show full stack traces.
Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oDTF")

// Assembly
// include the scala xml and compat modules into the final jar, shaded
assembly / assemblyShadeRules := Seq(
  ShadeRule.rename("scala.xml.**" -> "scapegoat.xml.@1").inAll,
  ShadeRule.rename("scala.collection.compat.**" -> "scapegoat.compat.@1").inAll,
  // scala-collection-compat has classes outside of the previous shade path, move them as well.
  ShadeRule.rename("scala.util.control.compat.**" -> "scapegoat.util.@1").inAll
)
Compile / packageBin := crossTarget.value / (assembly / assemblyJarName).value
makePom := makePom.dependsOn(assembly).value
assembly / test := {} // do not run tests during assembly
Test / publishArtifact := false

// Scalafix
ThisBuild / scalafixDependencies += "com.nequissimus" %% "sort-imports" % "0.6.1"
addCommandAlias("fix", "all Compile / scalafix Test / scalafix; fixImports")
addCommandAlias("fixImports", "Compile / scalafix SortImports; Test / scalafix SortImports")
addCommandAlias("fixCheck", "Compile / scalafix --check; Test / scalafix --check; fixCheckImports")
addCommandAlias(
  "fixCheckImports",
  "Compile / scalafix --check SortImports; Test / scalafix --check SortImports"
)

// Scalafmt
ThisBuild / scalafmtOnCompile := sys.env.get("GITHUB_ACTIONS").forall(_.toLowerCase == "false")
