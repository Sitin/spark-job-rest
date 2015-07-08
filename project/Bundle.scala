import sbt.IO._
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._

object Bundle {
  lazy val bundle = TaskKey[File]("bundle", "Bundles assembly, scripts and resources to zip achive.")

  lazy val bundleTask = bundle := {
    val log = streams.value.log

    val assemblyFileName = (assemblyJarName in assembly).value
    val assemblyJar = new File(s"${crossTarget.value.getPath}/$assemblyFileName")
    val destJarName = s"${name.value}.jar"

    val artifact = new File(s"${crossTarget.value.getPath}/${name.value}.zip")

    val srcBaseDir = sourceDirectory.value.getAbsolutePath

    val srcResources = s"$srcBaseDir/main/resources"
    val destResources = "resources"
    val resourceFiles = listFiles(new File(srcResources), "*.*")
    val resourceDests = resourceFiles.map(file => s"$destResources/${file.name}")

    val scriptsSrc = s"$srcBaseDir/main/scripts"
    val scriptsDest = "bin"
    val scriptFiles = listFiles(new File(scriptsSrc), "*_server.sh")
    val scriptDests = scriptFiles.map(file => s"$scriptsDest/${file.name}")

    val filesToArchive = resourceFiles.zip(resourceDests) ++ scriptFiles.zip(scriptDests) ++ Array((assemblyJar, destJarName))

    zip(filesToArchive, artifact)
    for ((src, dst) <- filesToArchive)
      log.info(s"Package $src as ./$dst")

    log.info(s"Created $artifact")

    // Return artifact
    artifact
  }

  lazy val bundleIsDependsOnAssembly = Seq(bundle <<= bundle.dependsOn(assembly))

  def bundleArtifact(artifactName: String) =
    addArtifact(Artifact(artifactName, "bundle", "zip"), bundle) ++ bundleTask ++ bundleIsDependsOnAssembly
}