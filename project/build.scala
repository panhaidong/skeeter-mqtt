import sbt._
import sbt.Keys._
import xerial.sbt.Pack._

object Build extends sbt.Build {

  lazy val root = Project(
    id = "skeeter",
    base = file("."),
    settings = Defaults.defaultSettings
      ++ packSettings // This settings add pack and pack-archive commands to sbt
      ++ Seq(
      name := "skeeter-mqtt",
      version := "0.0.1",
      scalaVersion := "2.10.3",

      // sbt-pack
      packMain := Map("run" -> "org.zhutou.skeeter.Server"),
      packJvmOpts := Map("run" -> Seq("-Xmx512m")),
      // [Optional] Extra class paths to look when launching a program
      packExtraClasspath := Map("skeeter" -> Seq("${PROG_HOME}/etc")),
      packGenerateWindowsBatFile := false,


      resolvers += "Eclipse Paho Release" at "https://repo.eclipse.org/content/repositories/paho-releases/",
      resolvers += "OSChina" at "http://maven.oschina.net/content/groups/public/",
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-library" % "2.10.3",
        "org.scala-lang" % "scala-actors" % "2.10.3",
        "io.netty" % "netty-common" % "4.0.15.Final",
        "io.netty" % "netty-buffer" % "4.0.15.Final",
        "io.netty" % "netty-handler" % "4.0.15.Final",
        "io.netty" % "netty-transport" % "4.0.15.Final",
        "io.netty" % "netty-codec" % "4.0.15.Final",
        "redis.clients" % "jedis" % "2.2.1",
        "org.redisson" % "redisson" % "1.0.2",
        "org.slf4s" %% "slf4s-api" % "1.7.6",
        "ch.qos.logback" % "logback-classic" % "1.1.1",
        "com.typesafe" % "config" % "1.2.0",
        "org.eclipse.paho" % "mqtt-client" % "0.4.0"
      )
    )
    // To publish tar.gz archive to the repository, add the following line (since 0.3.6)
    // ++ publishPackArchive
    // Before 0.3.6, use below:
    // ++ addArtifact(Artifact("myprog", "arch", "tar.gz"), packArchive).settings
  )
}