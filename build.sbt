ThisBuild / organization := "com.github.takapi327"
ThisBuild / startYear := Some(2023)
ThisBuild / version := "1.0.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(
    name := "lambda-env",
    scalaVersion := "2.13.11",
    run / fork := true,
    run / javaOptions ++= Seq(
      "-Dconfig.resource=application.conf",
    ),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings",
      "-Xlint",
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe" % "config" % "1.4.3",
      "com.amazonaws" % "aws-lambda-java-events" % "3.11.1",
      "com.amazonaws" % "aws-lambda-java-runtime-interface-client" % "2.3.1",
      "com.amazonaws" % "aws-java-sdk-sns" % "1.12.583",
      "com.amazonaws" % "aws-java-sdk-ssm" % "1.12.583",
    ),
    Universal / mappings += {
      ((Compile / resourceDirectory).value / s"application.conf") -> "application.conf"
    }
  )
  .enablePlugins(EcrPlugin)
  .enablePlugins(JavaAppPackaging)

Docker / maintainer := "takahiko.tominaga@nextbeat.net"
Docker / packageName := (root / name).value
dockerBaseImage := "amazoncorretto:11"
Docker / daemonUserUid := None
Docker / daemonUser := "daemon"
dockerEntrypoint := Seq(
  "java",
  "-cp",
  "/opt/docker/lib/*",
  "com.amazonaws.services.lambda.runtime.api.client.AWSLambda"
)

import com.amazonaws.regions.{ Region, Regions }
Ecr / packageName      :=  (root / name).value
Ecr / region           :=  Region.getRegion(Regions.AP_NORTHEAST_1)
Ecr / repositoryName   :=  (Docker / packageName).value
Ecr / localDockerImage :=  (Docker / packageName).value  + ":" + (Docker / version).value
Ecr / repositoryTags   ++= Seq(version.value)
Ecr / repositoryDomain :=  Some("573320908463.dkr.ecr.ap-northeast-1.amazonaws.com")
Ecr / registryIds      ++= Seq("573320908463")

// Publisher setting
import ReleaseTransformations._
releaseProcess := {
  Seq[ReleaseStep] (
    runClean,
    runTest,
    ReleaseStep(state => Project.extract(state).runTask(Docker / publishLocal, state)._1),
    ReleaseStep(state => Project.extract(state).runTask(Ecr / login, state)._1),
    ReleaseStep(state => Project.extract(state).runTask(Ecr / push, state)._1),
  )
}
