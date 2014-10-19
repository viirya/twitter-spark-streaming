import AssemblyKeys._

name := "Twitter Stream on Spark Streaming"

version := "1.0"

scalaVersion := "2.10.4"

resolvers ++= Seq("clojars" at "http://clojars.org/repo/",
                  "clojure-releases" at "http://build.clojure.org/releases")
 
resolvers += "Akka Repository" at "http://repo.akka.io/releases/"
 
libraryDependencies += "org.apache.spark" %% "spark-core" % "1.1.0"

libraryDependencies += "org.apache.spark" %% "spark-streaming" % "1.1.0"

libraryDependencies += "org.apache.spark" %% "spark-streaming-twitter" % "1.1.0"

libraryDependencies += "org.json4s" %% "json4s-jackson" % "3.2.10"

libraryDependencies += "net.debasishg" %% "redisclient" % "2.13"

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

