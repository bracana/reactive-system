package org.patricknoir.kafka.reactive.client.integration

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import com.typesafe.config.ConfigFactory
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }
import org.patricknoir.kafka.reactive.server.ReactiveRoute
import org.specs2.SpecificationLike

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Created by patrick on 17/03/2017.
 */
abstract class BaseISpec extends TestKit(ActorSystem("TestKit", ConfigFactory.parseString(BaseISpec.configString))) with SpecificationLike {

  def getKafkaPort() = 9092
  def getZooKeeperPort() = 2181

  implicit val materializer = ActorMaterializer()
  implicit val kafkaConfig = EmbeddedKafkaConfig(kafkaPort = getKafkaPort(), zooKeeperPort = getZooKeeperPort())

  def startKafka(): Unit = {
    EmbeddedKafka.start()
  }

  def startAtMostOnceServer(route: ReactiveRoute) = KafkaService.atMostOnce(route).run()
  def startAtLeastOnceServer(route: ReactiveRoute) = KafkaService.atLeastOnce(route).run()

  def stopKafka(): Unit = {
    EmbeddedKafka.stop()
  }

}

object BaseISpec {
  val configString = """
                       |akka {
                       |  log-config-on-start = off
                       |
                       |  loggers = ["akka.testkit.TestEventListener"]
                       |  loglevel = "INFO"
                       |  stdout-loglevel = "INFO"
                       |
                       |  logger-startup-timeout = 10s
                       |  jvm-exit-on-fatal-error = off
                       |
                       |  log-dead-letters = on
                       |  log-dead-letters-during-shutdown = on
                       |
                       |  actor {
                       |    debug {
                       |      autoreceive = on
                       |      receive = on
                       |      lifecycle = on
                       |      fsm = on
                       |      event-stream = on
                       |      unhandled = on
                       |    }
                       |
                       |    custom {
                       |      dispatchers {
                       |        bounded-fork-join-dispatcher {
                       |          type = Dispatcher
                       |          executor = "fork-join-executor"
                       |          mailbox-requirement = "akka.dispatch.BoundedMessageQueueSemantics"
                       |        }
                       |      }
                       |    }
                       |  }
                       |}
                     """.stripMargin
}

