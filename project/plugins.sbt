import _root_.sbt._

logLevel := Level.Warn

scalaVersion := "2.10.3"

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.6.4")
