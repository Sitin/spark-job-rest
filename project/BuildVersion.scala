import sbt.TaskKey

import scala.util.Try

/**
 * The version of Spark-Job-REST build is produced from the three parts:
 *    <releaseVersion>[-<buildMark>-SNAPSHOT]
 *  1. releaseVersion is a semantic version like `0.1.2-rc.1`
 *  2. buildMark is short string that represents build context.
 */
object BuildVersion {
  lazy val projectVersion = TaskKey[Unit]("projectVersion", "Shows project version")

  lazy val projectVersionTask = projectVersion := {
    println(buildVersion)
  }

  lazy val isSnapshot = Try(System.getenv("IS_RELEASE").trim != "true").getOrElse(true)
  lazy val releaseVersion = "0.4.0-DR"

  lazy val buildMark = Try(System.getenv("BUILD_MARK").trim).getOrElse("")
  lazy val snapshotMark = if (isSnapshot) "SNAPSHOT" else ""
  lazy val calculatedVersion = List(releaseVersion, buildMark, snapshotMark).filter(!_.isEmpty).mkString("-")

  lazy val buildVersion = Try(System.getenv("SJR_VERSION").trim).getOrElse(calculatedVersion)
}