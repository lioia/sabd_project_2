val scala2Version = "2.12.19"

lazy val root = project
  .in(file("."))
  .settings(
    name := "SABD Project 2",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala2Version,
    libraryDependencies += "org.apache.flink" % "flink-streaming-java" % "1.19.1",
    libraryDependencies += "org.apache.flink" % "flink-clients" % "1.19.1",
    libraryDependencies += "org.apache.flink" % "flink-connector-kafka" % "3.2.0-1.19",
    libraryDependencies += "org.apache.kafka" % "kafka-clients" % "3.7.1",
    libraryDependencies += "org.apache.flink" % "flink-connector-files" % "1.19.1",
    libraryDependencies += "org.apache.flink" % "flink-csv" % "1.19.1",
    libraryDependencies += "org.apache.flink" % "flink-json" % "1.19.1",
    libraryDependencies += "org.apache.flink" % "flink-metrics-prometheus" % "1.19.1"
  )

assembly / assemblyJarName := "sabd-assembly.jar"

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}
