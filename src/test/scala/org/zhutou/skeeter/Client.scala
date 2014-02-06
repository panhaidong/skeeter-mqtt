package org.zhutou.skeeter

import org.eclipse.paho.client.mqttv3._
import java.sql.Timestamp

object Client extends App{

    val conOpt = new MqttConnectOptions()
    //conOpt.setConnectionTimeout(1000)
    conOpt.setKeepAliveInterval(5)
    val client = new MqttClient("tcp://localhost:1980", "cid2", null)
    //client.setTimeToWait(10)
    client.setCallback(new MqttCallback {
      override def connectionLost(cause: Throwable): Unit = {
        System.out.println("Connection lost!" + cause);
        System.exit(1);
      }

      override def messageArrived(topic: String, message: MqttMessage): Unit = {
        val time = new Timestamp(System.currentTimeMillis()).toString();
        System.out.println("Time:\t" + time +
          "  Topic:\t" + topic +
          "  Message:\t" + new String(message.getPayload()) +
          "  QoS:\t" + message.getQos());
      }

      override def deliveryComplete(token: IMqttDeliveryToken): Unit = {
        System.out.println("sent:" + token);
      }
    })

    client.connect(conOpt);
    client.subscribe("TEST");
    val msg = new MqttMessage();
    msg.setQos(0)
    msg.setPayload("O^oo^O".getBytes("UTF-8"))
    client.publish("TEST", msg);

}
