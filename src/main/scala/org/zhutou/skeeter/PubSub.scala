package org.zhutou.skeeter

import org.slf4s.Logging
import redis.clients.jedis.{JedisPubSub, Jedis}
import java.nio.ByteBuffer
import scala.actors.Actor

abstract class PubSub {
  def subscribe(clientId: String, topicNames: List[String])

  def unSubscribe(clientId: String, topicNames: List[String])

  def unSubscribe(clientId: String)

  def publish(topicName: String, payload: Array[Byte])

  def startDispatch()
}

object RedisPubSub extends PubSub with Logging {
  val jedis = new Jedis(Config.redisAddress, Config.redisPort)
  val storage: Storage = RedisStorage

  override def publish(topicName: String, payload: Array[Byte]) = {
    val id = storage.save(payload)
    jedis.publish(topicName, id)
  }

  override def unSubscribe(clientId: String) = {
    val topicNames = storage.getSubscribedTopics(clientId)
    storage.unSubscribe(clientId)
    lsnr.unsubscribe(topicNames.filter(storage.getSubscribers(_).size == 0): _*)
  }

  override def unSubscribe(clientId: String, topicNames: List[String]) = {
    storage.unSubscribe(clientId, topicNames)
    lsnr.unsubscribe(topicNames.filter(storage.getSubscribers(_).size == 0): _*)
  }

  override def subscribe(clientId: String, topicNames: List[String]) = {
    storage.subscribe(clientId, topicNames)
    lsnr.subscribe(topicNames: _*)
  }

  override def startDispatch = {
    Subscriber.start
    Subscriber ! "start"
  }

  private object lsnr extends JedisPubSub {
    override def onUnsubscribe(channel: String, subscribedChannels: Int): Unit = {}

    override def onSubscribe(channel: String, subscribedChannels: Int): Unit = {}

    override def onMessage(topicName: String, id: String): Unit = {
      log.debug("onMessage:" + id)
      val bytes = storage.load(id)

      val message = new MQTTPublishMessage(new MessageHeader(MessageType.PUBLISH, false, MessageQoSLevel.AT_LEAST_ONCE, false, 0), topicName, 1, ByteBuffer.wrap(bytes))

      for (clientId <- storage.getSubscribers(topicName)) {
        for (client <- Server.clientIdMapper.get(clientId.asInstanceOf[String])) {
          client ! message
        }
      }

    }

    override def onPSubscribe(pattern: String, subscribedChannels: Int): Unit = {}

    override def onPUnsubscribe(pattern: String, subscribedChannels: Int): Unit = {}

    override def onPMessage(pattern: String, channel: String, message: String): Unit = {}
  }

  object Subscriber extends Actor {
    def act() {
      loop {
        react {
          case _ =>
            log.debug("Subscriber started...")
            //will block here
            new Jedis(Config.redisAddress, Config.redisPort).subscribe(lsnr, "a")
        }
      }
    }
  }

}