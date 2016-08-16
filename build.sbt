import com.typesafe.sbt.SbtScalariform.ScalariformKeys

import scalariform.formatter.preferences._

// sbt-docker
import com.typesafe.sbt.packager.docker._

// Resolvers
resolvers ++= Seq(
  Resolver.jcenterRepo,
  Resolver.sonatypeRepo("snapshots"),
  "sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)

val Versions = new {
  val Scala = "2.11.8"
  val ScalaBinary = "2.11"
  val Slf4j = "1.7.12"
  val Akka = "2.4.7"
  val Circe = "0.4.1"
  val Specs2 = "3.8.2"
}

// Dependencies
val compilerPlugins = Seq(
  compilerPlugin("org.spire-math"  %% "kind-projector"      % "0.7.1")
)

val rootDependencies = Seq(
  "org.slf4j"                      % "slf4j-api"               % Versions.Slf4j,
  "org.slf4j"                      % "slf4j-log4j12"           % Versions.Slf4j,
  "com.iheart"                     %% "ficus"                  % "1.2.5",
  "com.typesafe.akka"              %% "akka-slf4j"             % Versions.Akka,
  "com.typesafe.akka"              %% "akka-stream-kafka"      % "0.11-M3",
  "io.circe"                       %% "circe-core"             % Versions.Circe,
  "io.circe"                       %% "circe-generic"          % Versions.Circe,
  "io.circe"                       %% "circe-parser"           % Versions.Circe,
  "io.circe"                       %% "circe-java8"            % Versions.Circe,
  "io.circe"                       %% "circe-optics"           % Versions.Circe
)

val httpInterfaceDependencies = Seq(
  "com.typesafe.akka" %% "akka-http-experimental" % Versions.Akka
)

val testDependencies = Seq (
  "org.apache.kafka"               %% "kafka"                    % "0.9.0.1"       % "test",
  "com.typesafe.akka"              %% "akka-stream-testkit"      % Versions.Akka   % "test",
  "com.typesafe.akka"              %% "akka-http-testkit"        % Versions.Akka   % "test",
  "org.scalatest"                  %% "scalatest"                % "2.2.6"         % "test",
  "org.specs2"                     %% "specs2-core"              % Versions.Specs2 % "test",
  "org.specs2"                     %% "specs2-scalacheck"        % Versions.Specs2 % "test",
  "org.specs2"                     %% "specs2-mock"              % Versions.Specs2 % "test",
  "net.manub"                      %% "scalatest-embedded-kafka" % "0.6.1"         % "test"
)

val dependencies =
  compilerPlugins ++
    rootDependencies ++
    testDependencies

// Settings
//
val compileSettings = Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-language:_",
  "-target:jvm-1.8",
  "-unchecked",
  "-Ybackend:GenBCode",
  "-Ydelambdafy:method",
  "-Xfuture",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
//  "-Ywarn-unused-import",
  "-Ywarn-value-discard"
)

val coverageSettings = Seq(

)

val dockerSettings = Seq(
  dockerRepository := Some("10.210.201.187/pdiloreto"),
  defaultLinuxInstallLocation in Docker := "/opt/patricknoir",
  packageName in Docker := packageName.value,
  version in Docker := version.value,
  dockerCommands := Seq(
    Cmd("FROM", "java:openjdk-8-jdk-alpine"),
    Cmd("RUN", "apk upgrade --update && apk add libstdc++ && apk add bash && rm -rf /var/cache/apk/*"),
    Cmd("ADD", "opt /opt"),
    ExecCmd("RUN", "mkdir", "-p", "/var/logs/patricknoir/kafka-reactive-system"),
    ExecCmd("ENTRYPOINT", "/opt/patricknoir/bin/kafka-reactive-system")
  )
)

val forkedJvmOption = Seq(
  "-server",
  "-Dfile.encoding=UTF8",
  "-Duser.timezone=GMT",
  "-Xss1m",
  "-Xms2048m",
  "-Xmx2048m",
  "-XX:+CMSClassUnloadingEnabled",
  "-XX:ReservedCodeCacheSize=256m",
  "-XX:+DoEscapeAnalysis",
  "-XX:+UseConcMarkSweepGC",
  "-XX:+UseParNewGC",
  "-XX:+UseCodeCacheFlushing",
  "-XX:+UseCompressedOops"
)

val jmxJvmOption = Seq (
  "-Dcom.sun.management.jmxremote",
  "-Dcom.sun.management.jmxremote.port=9199",
  "-Dcom.sun.management.jmxremote.rmi.port=9199",
  "-Dcom.sun.management.jmxremote.local.only=false",
  "-Dcom.sun.management.jmxremote.authenticate=false",
  "-Dcom.sun.management.jmxremote.ssl=false"
)

val pluginsSettings =
  coverageSettings ++
  dockerSettings ++
  scalariformSettings

val commonSettings = Seq(
  organization := "org.patricknoir.kafka",
  version := "0.2.0-SNAPSHOT",
  scalaVersion := "2.11.8",
  fork in run := true,
  fork in Test := true,
  fork in testOnly := true,
  connectInput in run := true,
  javaOptions in run ++= forkedJvmOption ++ jmxJvmOption,
  javaOptions in Test ++= forkedJvmOption,
  scalacOptions := compileSettings,
  ScalariformKeys.preferences := PreferencesImporterExporter.loadPreferences((file(".") / "formatter.preferences").getPath)
)

val settings = commonSettings ++ Seq(
  name := "kafka-reactive-service",
  libraryDependencies ++= dependencies,
  unmanagedClasspath in Runtime += baseDirectory.value / "env/local",
  unmanagedClasspath in Compile += baseDirectory.value / "env/console",
  scriptClasspath += "../conf/",
  //mainClass in (Compile, run) := Option("org.patricknoir.kafka.service.Boot"),
  initialCommands in console :=
    """
    | import org.patricknoir.kafka.reactive.client.config._
    | import scala.concurrent.ExecutionContext.Implicits.global
    | import scala.concurrent.duration._
    | import akka.util.Timeout
    | import org.patricknoir.kafka.reactive.client._
    | import akka.actor.ActorSystem
    | import com.typesafe.config.ConfigFactory
    |
    | val akkaConfig = ConfigFactory.load("application.conf")
    |
    | implicit val timeout = Timeout(2 minutes)
    | implicit val system = ActorSystem("testConsole", akkaConfig)
    |
    | val config = KafkaRClientSettings.default
    | val client = new KafkaReactiveClient(config)
    |
    | def quickRequest(dest: String, message: String) = client.request[String, String](s"kafka:$dest/echo", message).onComplete(println)
    |
    """.stripMargin
)

val environment = System.getProperties.getProperty("stage", "local")
val kafkaRSSettings = Seq(
  mappings in Universal ++= {
    (baseDirectory.value / "env" / environment * "*").get.map { f =>
      f -> s"conf/${f.name}"
    }
  }
)

lazy val kafkaRS =
  project
    .in(file("kafka-reactive-system"))
    .settings(
       pluginsSettings ++ kafkaRSSettings ++ settings:_*
    )
    .enablePlugins(AshScriptPlugin,JavaServerAppPackaging, UniversalDeployPlugin)

lazy val httpInterface =
  project
    .in(file("http-interface"))
    .dependsOn(kafkaRS)
    .settings(commonSettings: _*)
    .settings(
      libraryDependencies ++= httpInterfaceDependencies
    )
    .settings(
      name := "http-interface"
    )

lazy val examplesServer =
  project
    .in(file("examples/server"))
    .dependsOn(kafkaRS)
    .settings(commonSettings: _*)
    .settings(
      name := "examples-server"
    )

lazy val examplesClient = 
  project
    .in(file("examples/client"))
    .dependsOn(kafkaRS)
    .settings(commonSettings: _*)
    .settings(
      name := "examples-client"
    )

lazy val root =
  project
    .in(file("."))
    .settings(moduleName := "reactive-system")
    .aggregate(kafkaRS, httpInterface, examplesServer, examplesClient)