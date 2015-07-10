import BuildVersion._
import Bundle._
import DependenciesSugar._

net.virtualvoid.sbt.graph.Plugin.graphSettings

name := "spark-job-rest"

lazy val sparkVersion = "1.4.0"

lazy val sprayVersion = "1.3.1"

lazy val httpComponentsVersion = "4.3.2"

lazy val akkaVersion = "2.3.4"

lazy val typesafeConfigVersion = "1.2.1"

lazy val nexusURL = "http://nexus.hq.datarobot.com:8081"

resolvers ++= Seq(
  "Maven Repository" at "http://repo1.maven.org/maven2",
  "Cloudera Releases" at "https://repository.cloudera.com/artifactory/repo/",
  "Akka Releases" at "http://repo.akka.io/releases",
  "Nexus" at s"$nexusURL/content/groups/public"
) ++ Seq("snapshots", "releases").map(Resolver.sonatypeRepo)

lazy val root = project.in(file("."))
  .aggregate(
    `spark-job-rest-server`,
    `spark-job-rest-api`,
    `spark-job-rest-client`,
    `spark-job-rest-sql`,
    `example-job`,
    `s3-download-job`
  )
  .disablePlugins(AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(bundleDeployScriptArtifact)
  .settings(projectVersionTask)

lazy val commonSettings = Seq(
  version := buildVersion,
  organization := "com.xpatterns",
  scalaVersion := "2.10.5",
  scalaBinaryVersion := "2.10",
  updateOptions := updateOptions.value.withCachedResolution(true)
)

lazy val publishSettings = Seq(
  credentials += Credentials("Sonatype Nexus Repository Manager",
    "nexus.hq.datarobot.com", "admin", "admin123"),
  publishTo <<= version { v: String =>
    if (BuildVersion.isSnapshot)
      Some("snapshots" at s"$nexusURL/content/repositories/snapshots")
    else
      Some("releases" at s"$nexusURL/content/repositories/releases")
  }
)

lazy val noPublishSettings = Seq(
  publish := {}
)

lazy val assemblySettings = Seq(
  test in assembly := {}
)

lazy val `spark-job-rest-server` = project.in(file("spark-job-rest"))
  .dependsOn(`spark-job-rest-api`, `spark-job-rest-client`)
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(assemblySettings: _*)
  .settings(assemblyBundleArtifact("spark-job-rest-server"))
  .settings(
    fork in test := true,
    libraryDependencies ++=
      sparkDependencies
      ++ sprayDependencies
      ++ httpConponentDependencies
      ++ jodaTimeDependencies
      ++ persistenceDependencies
      ++ commonTestDependencies,
    libraryDependencies ++= Seq(
        "com.google.code.gson" % "gson" % "2.3.1",
        "com.google.code.findbugs" % "jsr305" % "2.0.3",
        "com.fasterxml.jackson.core" % "jackson-annotations" % "2.4.4",
        "commons-cli" % "commons-cli" % "1.2",
        "log4j" % "log4j" % "1.2.17",
        "com.typesafe" % "config" % typesafeConfigVersion
      ),
    libraryDependencies ++= asTest(Seq(
      "io.spray" % "spray-testkit" % "1.2.1"
        exclude("com.typesafe", "config")
        exclude("com.typesafe.akka", "akka-actor_2.10"),
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion
    ))
  )

lazy val `spark-job-rest-api` = project.in(file("spark-job-rest-api"))
  .disablePlugins(AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    libraryDependencies ++= sparkDependencies ++ jodaTimeDependencies ++ commonTestDependencies ++ Seq(
      "io.spray" %% "spray-json" % sprayVersion
    )
  )

lazy val `spark-job-rest-sql` = project.in(file("spark-job-rest-sql"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(assemblySettings: _*)
  .dependsOn(`spark-job-rest-api`)
  .settings(assemblyBundleArtifact("spark-job-rest-sql"))
  .settings(
    libraryDependencies ++= sparkDependencies ++ commonTestDependencies ++ asSparkSqlDependencies(asProvided(Seq(
      "org.apache.spark" %% "spark-sql" % sparkVersion,
      "org.apache.spark" %% "spark-hive" % sparkVersion,
      "com.typesafe" % "config" % typesafeConfigVersion
    )))
  )

lazy val `spark-job-rest-client` = project.in(file("spark-job-rest-client"))
  .disablePlugins(AssemblyPlugin)
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .dependsOn(`spark-job-rest-api`)
  .settings(
    libraryDependencies ++= Seq(
      "io.spray" % "spray-client" % sprayVersion,
      "com.typesafe" % "config" % typesafeConfigVersion,
      "org.slf4j" % "slf4j-api" % "1.7.10",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion
    )
  )

lazy val `example-job` = project.in(file("examples/example-job"))
  .dependsOn(`spark-job-rest-api`)
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    libraryDependencies ++= sparkDependencies
  )

lazy val `s3-download-job` = project.in(file("examples/s3-download-job"))
  .dependsOn(`spark-job-rest-api`, `spark-job-rest-client`)
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    libraryDependencies ++= sparkDependencies ++ httpConponentDependencies ++ Seq(
      "com.amazonaws" % "aws-java-sdk" % "1.8.3"
    )
  )

lazy val sparkDependencies = asProvided(Seq(
  "org.apache.spark" %% "spark-core" % sparkVersion
))

lazy val jodaTimeDependencies = Seq(
  "joda-time" % "joda-time" % "2.7",
  "org.joda" % "joda-convert" % "1.7"
)

lazy val persistenceDependencies = Seq(
  "com.h2database" % "h2" % "1.4.187",
  "com.typesafe.slick" %% "slick" % "3.0.0",
  "com.github.tototoshi" %% "slick-joda-mapper" % "2.0.0"
)

lazy val commonTestDependencies = asTest(Seq(
  "junit" % "junit" % "4.4",
  "org.scalatest" %% "scalatest" % "2.2.4"
))

lazy val httpConponentDependencies = Seq(
  "org.apache.httpcomponents" % "httpclient" % httpComponentsVersion,
  "org.apache.httpcomponents" % "httpcore" % httpComponentsVersion
)

lazy val sprayDependencies = Seq(
  "io.spray" % "spray-client" % sprayVersion,
  "io.spray" % "spray-caching" % sprayVersion,
  "io.spray" % "spray-can" % sprayVersion,
  "io.spray" % "spray-routing" % sprayVersion
)