import sbt._

object DependenciesSugar {
  type Dependencies = Seq[ModuleID]

  def asProvided(dependencies: Dependencies): Dependencies = dependencies.map({
    case dependency => dependency % "provided"
  })
  
  def asTest(dependencies: Dependencies): Dependencies = dependencies.map({
    case dependency => dependency % "test"
  })

  def asSparkSqlDependencies(dependencies: Dependencies): Dependencies = dependencies.map({
    case dependency =>
      dependency
        .exclude("io.netty", "netty-all")
        .exclude("commons-net", "commons-net")
        .exclude("junit", "junit")
        .exclude("org.apache.avro", "avro")
        .exclude("org.harmcrest", "harmcrest")
        .exclude("org.jboss.netty", "netty")
        .excludeAll(ExclusionRule(organization = "org.codehaus.jackson"))
  })
}