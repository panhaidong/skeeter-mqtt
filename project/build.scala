import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtNativePackager._


object Build extends sbt.Build {


  lazy val root = Project(
    id = "skeeter",
    base = file("."),
    settings = Defaults.defaultSettings
      ++ Seq(
      name := "skeeter-mqtt",
      version := "0.0.1",
      scalaVersion := "2.10.3",

      resolvers += "Eclipse Paho Release" at "https://repo.eclipse.org/content/repositories/paho-releases/",
      resolvers += "OSChina" at "http://maven.oschina.net/content/groups/public/",
      libraryDependencies ++= Seq(
        "org.scala-lang" % "scala-library" % "2.10.3",
        "org.scala-lang" % "scala-actors" % "2.10.3",
        "org.springframework.data" % "spring-data-redis" % "1.2.0.RELEASE",
        "io.netty" % "netty-common" % "4.0.15.Final",
        "io.netty" % "netty-buffer" % "4.0.15.Final",
        "io.netty" % "netty-handler" % "4.0.15.Final",
        "io.netty" % "netty-transport" % "4.0.15.Final",
        "io.netty" % "netty-codec" % "4.0.15.Final",
        "redis.clients" % "jedis" % "2.2.1",
        "org.slf4s" %% "slf4s-api" % "1.7.6",
        "ch.qos.logback" % "logback-classic" % "1.1.1",
        "com.typesafe" % "config" % "1.2.0",
        "org.eclipse.paho" % "mqtt-client" % "0.4.0" % "test",
        "org.scalatest" % "scalatest_2.10" % "2.0" % "test"
      )

      //      mappings in Universal += {
      //        file("/Users/haidong/Projects/source/skeeter-mqtt/src/main/resources/application.conf") -> "conf/application.conf"
      //        file("/Users/haidong/Projects/source/skeeter-mqtt/src/main/resources/logback.xml") -> "conf/logback.xml"
      //      }
      //      mappings in Universal <++= (resourceDirectory in Compile) map {
      //        (resDir) ⇒
      //          println(resDir)
      //          resDir.listFiles.toSeq.collect {
      //            case f: File if f.getName.endsWith(".xml") ⇒ f
      //            case f: File if f.getName.endsWith(".conf") ⇒ f
      //            case f: File if f.getName.endsWith(".properties") ⇒ f
      //          }.map {
      //            f ⇒ f → ("conf/" + f.getName)
      //          }
      //      }
    )
  ).settings(packagerSettings ++ packageArchetype.java_server ++ mapGenericFilesToLinux: _*)


}
