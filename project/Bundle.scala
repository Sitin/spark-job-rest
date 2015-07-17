import sbt.IO._
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._

object Bundle {
  private lazy val scriptsPath = "main/scripts"
  private lazy val resourcesPath = "main/resources"

  lazy val bundle = TaskKey[File]("bundle", "Bundles assembly, scripts and resources to zip achive.")

  lazy val bundleAssemblyTask = bundle := {
    val log = streams.value.log

    val assemblyFileName = (assemblyJarName in assembly).value
    val assemblyJar = new File(s"${crossTarget.value.getPath}/$assemblyFileName")
    val destJarName = s"${name.value}.jar"

    val artifact = new File(s"${crossTarget.value.getPath}/${name.value}.zip")

    val srcBaseDir = sourceDirectory.value.getAbsolutePath

    val log4jPropertiesFileName = "log4j.properties"
    val srcResources = s"$srcBaseDir/$resourcesPath"
    val destResources = "resources"
    val resourceFiles = listFiles(new File(srcResources), "*.*")
      .filter(file => file.name != log4jPropertiesFileName)  // Filter out log4j.properties
    val resourceDests = resourceFiles.map(file => s"$destResources/${file.name}")
    val log4jProperties = new File(s"$srcResources/$log4jPropertiesFileName")
    val log4jPropertiesDest = log4jPropertiesFileName

    val scriptsSrc = s"$srcBaseDir/$scriptsPath"
    val scriptsDest = "bin"
    val scriptFiles = listFiles(new File(scriptsSrc), "*.sh")
    val scriptDests = scriptFiles.map(file => s"$scriptsDest/${file.name}")

    val filesToArchive = resourceFiles.zip(resourceDests) ++
      scriptFiles.zip(scriptDests) ++
      Array((assemblyJar, destJarName)) ++
      Array((log4jProperties, log4jPropertiesDest))

    zip(filesToArchive, artifact)
    for ((src, dst) <- filesToArchive)
      log.info(s"Package $src as ./$dst")

    log.info(s"Created $artifact")

    // Return artifact
    artifact
  }

  lazy val bundleDeployScriptTask = bundle := {
    val log = streams.value.log

    val srcBaseDir = sourceDirectory.value.getAbsolutePath
    val deployScript = new File(s"$srcBaseDir/$resourcesPath/deploy.sh")
    val artifact = new File(s"${target.value.getPath}/deploy.sh")

    copyFile(deployScript, artifact, preserveLastModified = true)
    log.info(s"Copied $deployScript to $artifact")

    artifact
  }

  lazy val bundleIsDependsOnAssembly = Seq(bundle <<= bundle.dependsOn(assembly))

  def assemblyBundleArtifact(artifactName: String) =
    addArtifact(Artifact(artifactName, "bundle", "zip"), bundle) ++ bundleAssemblyTask ++ bundleIsDependsOnAssembly

  lazy val bundleDeployScriptArtifact = addArtifact(Artifact("spark-job-rest-deploy", "script", "sh"), bundle) ++ bundleDeployScriptTask
}