name := "linoleum-ltlss"

organization := "es.ucm.fdi.demiourgoi"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.15"

crossScalaVersions := Seq("2.13.15")

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

lazy val sscheckVersion = "0.5.1-SNAPSHOT"

libraryDependencies += "es.ucm.fdi.demiourgoi" %% "sscheck-core" % sscheckVersion excludeAll(
  ExclusionRule(organization = "org.slf4j"),
)

// https://github.com/grpc/grpc-java/blob/master/examples/example-opentelemetry/build.gradle
lazy val grpcVersion = "1.70.0"
libraryDependencies += "io.grpc" % "grpc-protobuf" % grpcVersion
libraryDependencies += "io.grpc" % "grpc-stub" % grpcVersion
libraryDependencies += "io.grpc" % "grpc-netty-shaded" % grpcVersion
libraryDependencies += "javax.annotation" % "javax.annotation-api" % "1.3.2"

resolvers ++= Seq(
  "MVN Repository.com" at "https://mvnrepository.com/artifact/",
)
