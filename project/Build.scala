import sbt._

object Build extends Build {
  type Dependencies = Seq[ModuleID]

  def asProvided(dependencies: Dependencies): Dependencies = dependencies.map({
    case dependency if sys.env.getOrElse("INTELLIJ_IDEA", "false") == "true" => dependency
    case dependency => dependency % "provided"
  })
  
  def asTest(dependencies: Dependencies): Dependencies = dependencies.map({
    case dependency => dependency % "test"
  })

  def sqlDependencies(dependencies: Dependencies): Dependencies = dependencies.map({
    case dependency => dependency exclude("io.netty", "netty-all") excludeAll ExclusionRule(organization = "org.codehaus.jackson")
  })
}