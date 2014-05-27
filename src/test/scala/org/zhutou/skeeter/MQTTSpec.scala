package org.zhutou.skeeter

import akka.actor._
import Actor._
import org.scalatest._
import org.eclipse.paho.client.mqttv3._
import org.slf4s.Logging
import akka.util.Timeout
import akka.actor
import scala.concurrent.Await

class MQTTSpec extends FunSpec with Logging {
  val conOpt = new MqttConnectOptions()

  private def createClient(clientId: String) = new MqttClient("tcp://localhost:1980", clientId, null)

//  describe("CONN") {
//    it("should abort if clientId's length > 23") {
//      intercept[MqttException] {
//        val client = createClient("123456789012345678901234")
//        client.connect()
//      }
//    }
//
//    it("should passed a normal processes") {
//      val client = createClient("asdf")
//      client.connect(conOpt)
//      assertResult(true, "connected") {
//        client.isConnected
//      }
//      client.subscribe("topic0")
//
//      implicit val timeout = Timeout(5 seconds)
//
//      client.setCallback(new MqttCallback {
//        override def connectionLost(cause: Throwable): Unit = {}
//
//        override def messageArrived(topic: String, message: MqttMessage): Unit = {
//          val future = actor ? msg
//        }
//
//        override def deliveryComplete(token: IMqttDeliveryToken): Unit = {}
//      })
//      client.publish("topic0", "m".getBytes("UTF-8"), 0, false)
//
//      context.setReceiveTimeout(1000)
//
//      val result = Await.result(future, timeout.duration).asInstanceOf[String]
//
//      def receive = {
//        case (topic: String, message: MqttMessage) =>
//          assertResult("topic0", "should receive for topic") {
//            topic
//          }
//
//          assertResult("m", "should receive") {
//            new String(message.getPayload, "UTF-8")
//          }
//        case ReceiveTimeout => assertResult(true) {
//          false
//        }
//      }
//
//      client.unsubscribe("topic1")
//      client.disconnect()
//      assertResult(false, "disconnected") {
//        client.isConnected
//      }
//    }
//  }
//
//
//  it("should passed WillMessage") {
//    val client1 = createClient("client1")
//    client1.connect(conOpt)
//    client1.subscribe("will_topic", 1)
//    val receiver = self
//    client1.setCallback(new MqttCallback {
//      override def connectionLost(cause: Throwable): Unit = {}
//
//      override def messageArrived(topic: String, message: MqttMessage): Unit = {
//        log.debug("Will:" + topic)
//        receiver !(topic, message)
//      }
//
//      override def deliveryComplete(token: IMqttDeliveryToken): Unit = {}
//    })
//
//    val client2 = createClient("client2")
//    val conOpt2 = new MqttConnectOptions()
//    conOpt2.setWill("will_topic", "will_message".getBytes("UTF-8"), 0, false)
//    client2.connect(conOpt2)
//
//    val client3 = createClient("client2")
//    client3.connect(conOpt2)
//    client3.disconnect()
//
//
//    context.setReceiveTimeout(1000)
//    def receive = {
//      case (topic: String, message: MqttMessage) =>
//        assertResult("will_topic", "should receive for topic") {
//          topic
//        }
//
//        assertResult("will_message", "should receive") {
//          new String(message.getPayload, "UTF-8")
//        }
//      case ReceiveTimeout => assertResult(true) {
//        false
//      }
//    }
//    client1.disconnect()
//  }
//
//  it("should passed QoS1") {
//
//    val client3 = createClient("client3")
//    client3.connect(conOpt)
//    client3.subscribe("topic3", 1)
//    client3.disconnect()
//
//    val client4 = createClient("client4")
//    client4.connect(conOpt)
//    client4.publish("topic3", "QoS1Message".getBytes("UTF-8"), 1, false)
//    client4.disconnect()
//
//    Thread.sleep(500)
//
//    val receiver = self
//    client3.setCallback(new MqttCallback {
//      override def connectionLost(cause: Throwable): Unit = {}
//
//      override def messageArrived(topic: String, message: MqttMessage): Unit = {
//        log.debug("QoS1" + topic)
//        receiver !(topic, message)
//      }
//
//      override def deliveryComplete(token: IMqttDeliveryToken): Unit = {}
//    })
//
//    val conOpt2 = new MqttConnectOptions()
//    conOpt2.setCleanSession(false)
//
//    client3.connect(conOpt2)
//    context.setReceiveTimeout(1000)
//    def receive = {
//      case (topic: String, message: MqttMessage) =>
//        assertResult("topic3", "should receive for topic") {
//          topic
//        }
//
//        assertResult("QoS1Message", "should receive") {
//          val s = new String(message.getPayload, "UTF-8")
//          s
//        }
//      case ReceiveTimeout => assertResult(true) {
//        false
//      }
//    }
//    client3.disconnect()
//  }
//
//  it("should passed RetainMessage") {
//    val client5 = createClient("client5")
//    client5.connect(conOpt)
//    client5.publish("retain_topic", "RetainMessage".getBytes("UTF-8"), 1, true)
//    client5.disconnect()
//
//    val client6 = createClient("client6")
//    val receiver = self
//    client6.setCallback(new MqttCallback {
//      override def connectionLost(cause: Throwable): Unit = {}
//
//      override def messageArrived(topic: String, message: MqttMessage): Unit = {
//        log.debug("QoS1" + topic)
//        receiver !(topic, message)
//      }
//
//      override def deliveryComplete(token: IMqttDeliveryToken): Unit = {}
//    })
//
//    client6.connect(conOpt)
//    client6.subscribe("retain_topic")
//
//    context.setReceiveTimeout(2000)
//    def receive = {
//      case (topic: String, message: MqttMessage) =>
//        assertResult("retain_topic", "should receive for topic") {
//          topic
//        }
//
//        assertResult("RetainMessage", "should receive") {
//          val s = new String(message.getPayload, "UTF-8")
//          s
//        }
//      case ReceiveTimeout => assertResult(true) {
//        false
//      }
//    }
//
//    client6.disconnect()
//
//
//  }
}