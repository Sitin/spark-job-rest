import Build._

net.virtualvoid.sbt.graph.Plugin.graphSettings

name := "spark-job-REST"

lazy val sparkVersion = "1.4.0"

lazy val sprayVersion = "1.3.1"

lazy val httpComponentsVersion = "4.3.2"

lazy val akkaVersion = "2.3.4"

lazy val typesafeConfigVersion = "1.2.1"

resolvers += "Maven Repository" at "http://repo1.maven.org/maven2"

resolvers += "Cloudera Releases" at "https://repository.cloudera.com/artifactory/repo/"

resolvers += "Akka Releases" at "http://repo.akka.io/releases"

lazy val root = project.in(file("."))
  .aggregate(
    `spark-job-rest`,
    `spark-job-rest-api`,
    `spark-job-rest-client`,
    `spark-job-rest-sql`
  )
  .dependsOn(
    `spark-job-rest`,
    `spark-job-rest-api`,
    `spark-job-rest-client`,
    `spark-job-rest-sql`
  )
  .disablePlugins(AssemblyPlugin)

lazy val commonSettings = Seq(
  version := "0.3.3",
  organization := "com.xpatterns",
  scalaVersion := "2.10.5",
  scalaBinaryVersion := "2.10",
  libraryDependencies ++= commonTestDependencies
)

lazy val assemblySettings = Seq(
  test in assembly := {}
)

lazy val `spark-job-rest` = project.in(file("spark-job-rest"))
  .dependsOn(`spark-job-rest-api`, `spark-job-rest-client`)
  .settings(commonSettings: _*)
  .settings(assemblySettings: _*)
  .settings(
    assemblyJarName in assembly := s"spark-job-rest-${version.value}.jar",
    fork in test := true,
    libraryDependencies ++= sparkDependencies,
    libraryDependencies ++= Seq(
      "io.spray" % "spray-client" % sprayVersion,
      "io.spray" % "spray-caching" % sprayVersion,
      "io.spray" % "spray-can" % sprayVersion,
      "io.spray" % "spray-routing" % sprayVersion
    ),
    libraryDependencies ++= Seq(
      "org.apache.httpcomponents" % "httpclient" % httpComponentsVersion,
      "org.apache.httpcomponents" % "httpcore" % httpComponentsVersion
    ),
    libraryDependencies ++= jodaTimeDependencies,
    libraryDependencies ++= Seq(
      "com.h2database" % "h2" % "1.4.187",
      "com.typesafe.slick" %% "slick" % "3.0.0",
      "com.github.tototoshi" %% "slick-joda-mapper" % "2.0.0"
    ),
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
  .settings(
    libraryDependencies ++= sparkDependencies ++ jodaTimeDependencies ++ Seq(
      "io.spray" %% "spray-json" % sprayVersion
    )
  )

lazy val `spark-job-rest-sql` = project.in(file("spark-job-rest-sql"))
  .settings(commonSettings: _*)
  .settings(assemblySettings: _*)
  .dependsOn(`spark-job-rest-api`)
  .settings(
    assemblyJarName in assembly := "spark-job-rest-sql.jar",
    libraryDependencies ++= sparkDependencies ++ asProvided(Seq(
      "org.apache.spark" %% "spark-sql" % sparkVersion,
      "org.apache.spark" %% "spark-hive" % sparkVersion,
      "com.typesafe" % "config" % typesafeConfigVersion
    ))
  )

lazy val `spark-job-rest-client` = project.in(file("spark-job-rest-client"))
  .disablePlugins(AssemblyPlugin)
  .settings(commonSettings: _*)
  .dependsOn(`spark-job-rest-api`)
  .settings(
    libraryDependencies ++= Seq(
      "io.spray" % "spray-client" % sprayVersion,
      "com.typesafe" % "config" % typesafeConfigVersion,
      "org.slf4j" % "slf4j-api" % "1.7.10",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion
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