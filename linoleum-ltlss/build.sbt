name := "linoleum-ltlss"

organization := "es.ucm.fdi.demiourgoi"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.15"

crossScalaVersions := Seq("2.13.15")

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

// https://mvnrepository.com/artifact/org.specs2/specs2-core_2.13
lazy val specs2Version = "4.20.9"

resolvers ++= Seq(
  "MVN Repository.com" at "https://mvnrepository.com/artifact/",
)
