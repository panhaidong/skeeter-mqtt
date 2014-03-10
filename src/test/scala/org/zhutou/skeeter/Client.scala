package org.zhutou.skeeter

import org.eclipse.paho.client.mqttv3._
import java.sql.Timestamp
import io.netty.channel._
import io.netty.util.concurrent.EventExecutor
import io.netty.buffer.ByteBufAllocator
import java.net.SocketAddress
import io.netty.util.{Attribute, AttributeKey}

object Client extends App {
  val conOpt = new MqttConnectOptions()
  //conOpt.setConnectionTimeout(1000)
  conOpt.setKeepAliveInterval(5)
  val client = new MqttClient("tcp://localhost:1980", "cid1", null)
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
  client.subscribe("TEST1",2);
  client.subscribe("TEST2",2);
//  val msg = new MqttMessage();
//  msg.setQos(1)
//  msg.setPayload("O^oo^O1212".getBytes("UTF-8"))
//  client.publish("TEST1", msg);
}
