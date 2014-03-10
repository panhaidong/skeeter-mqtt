package org.zhutou.skeeter

import scala.actors._
import Actor._
import org.scalatest._
import org.eclipse.paho.client.mqttv3._

class MQTTSpec extends FunSpec {
  val conOpt = new MqttConnectOptions()

  private def createClient(clientId: String) = new MqttClient("tcp://localhost:1980", clientId, null)

  describe("CONN") {
    it("should abort if clientId's length > 23") {
      intercept[MqttException] {
        val client = createClient("123456789012345678901234")
        client.connect()
      }
    }

    it("should passed a normal processes") {
      val client = createClient("asdf")
      client.connect(conOpt)
      assertResult(true, "connected") {
        client.isConnected
      }
      client.subscribe("topic1")
      val receiver = self
      client.setCallback(new MqttCallback {
        override def connectionLost(cause: Throwable): Unit = {}

        override def messageArrived(topic: String, message: MqttMessage): Unit = {
          receiver !(topic, message)
        }

        override def deliveryComplete(token: IMqttDeliveryToken): Unit = {}
      })
      client.publish("topic1", "m".getBytes("UTF-8"), 0, false)

      receiveWithin(1000) {
        case (topic: String, message: MqttMessage) =>
          println("receive" + topic)
          assertResult("topic1", "should receive for topic") {
            topic
          }

          assertResult("m", "should receive") {
            new String(message.getPayload, "UTF-8")
          }
        case TIMEOUT => assertResult(true) {
          false
        }
      }

      client.unsubscribe("topic1")
      client.disconnect()
      assertResult(false, "disconnected") {
        client.isConnected
      }

    }
  }
}
