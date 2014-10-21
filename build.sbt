import AssemblyKeys._

name := "Twitter Stream on Spark Streaming"

version := "1.0"

scalaVersion := "2.10.4"

compileOrder := CompileOrder.JavaThenScala

resolvers ++= Seq("clojars" at "http://clojars.org/repo/",
                  "clojure-releases" at "http://build.clojure.org/releases")
 
resolvers += "Akka Repository" at "http://repo.akka.io/releases/"
 
libraryDependencies += "org.apache.spark" %% "spark-core" % "1.1.0"

libraryDependencies += "org.apache.spark" %% "spark-streaming" % "1.1.0"

libraryDependencies += "org.apache.spark" %% "spark-streaming-twitter" % "1.1.0"

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.10"

libraryDependencies += "net.debasishg" %% "redisclient" % "2.13"

libraryDependencies +=  "net.debasishg" %% "sjson" % "0.19"

libraryDependencies += "org.jblas" % "jblas" % "1.2.3"

libraryDependencies += "org.apache.lucene" % "lucene-analyzers" % "3.6.2"


assemblySettings

mergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) => {
    case PathList("META-INF", "ECLIPSEF.RSA" ) => MergeStrategy.discard
    case PathList("META-INF", "mailcap" ) => MergeStrategy.discard
    case PathList("com", "esotericsoftware", "minlog", ps ) if ps.startsWith("Log") => MergeStrategy.discard
    case PathList("org", "apache", "commons", ps @ _* ) => MergeStrategy.first
    case PathList("akka", ps @ _* ) => MergeStrategy.first
    case PathList("plugin.properties" ) => MergeStrategy.discard
    case x => old(x)
  } 
}

