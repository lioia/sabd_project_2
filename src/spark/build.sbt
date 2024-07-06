val scala2Version = "2.12.19"

lazy val root = project
  .in(file("."))
  .settings(
    name := "SABD Project 2 Spark",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala2Version,
    libraryDependencies += "org.apache.spark" %% "spark-core" % "3.5.1",
    libraryDependencies += "org.apache.spark" %% "spark-sql" % "3.5.1",
    libraryDependencies += "org.apache.spark" %% "spark-streaming" % "3.5.1",
    libraryDependencies += "org.apache.spark" %% "spark-streaming-kafka-0-10" % "3.5.1",
    scalacOptions += "-Ywarn-unused-import"
  )

assembly / assemblyJarName := "sabd-assembly.jar"

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}
