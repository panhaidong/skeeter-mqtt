package org.zhutou.skeeter

import org.slf4s.Logging
import redis.clients.jedis.{JedisPubSub, Jedis}
import scala.actors.Actor

abstract class PubSub {
  def subscribe(clientId: String, topicNames: List[String])

  def unSubscribe(clientId: String, topicNames: List[String])

  def unSubscribeAll(clientId: String)

  def publish(message: MQTTPublishMessage)

  def startDispatch()
}

object RedisPubSub extends PubSub with Logging {
  val jedis = new Jedis(Config.redisAddress, Config.redisPort)
  val storage: Storage = RedisStorage

  private def getRedisTopicKey(topicName: String) = (Config.redisKeyPrefix + ":ptopic:" + topicName)

  override def publish(message: MQTTPublishMessage) = {
    log.debug("publish")
    val id = storage.save(message)
    jedis.publish(getRedisTopicKey(message.mTopicName), id)
  }

  override def unSubscribeAll(clientId: String) = {
    val topicNames = storage.getSubscribedTopics(clientId)
    storage.unSubscribe(clientId)
    lsnr.unsubscribe(topicNames.filter(storage.getSubscribers(_).size == 0).map(getRedisTopicKey(_)): _*)
  }

  override def unSubscribe(clientId: String, topicNames: List[String]) = {
    storage.unSubscribe(clientId, topicNames)
    lsnr.unsubscribe(topicNames.filter(storage.getSubscribers(_).size == 0).map(getRedisTopicKey(_)): _*)
  }

  override def subscribe(clientId: String, topicNames: List[String]) = {
    log.debug("subscribe")
    storage.subscribe(clientId, topicNames)
    lsnr.subscribe(topicNames.map(getRedisTopicKey(_)): _*)
  }

  override def startDispatch = {
    Subscriber.start
    Subscriber ! "start"
  }

  private object lsnr extends JedisPubSub {
    override def onUnsubscribe(channel: String, subscribedChannels: Int): Unit = {}

    override def onSubscribe(channel: String, subscribedChannels: Int): Unit = {}

    override def onMessage(topic: String, id: String): Unit = {
      val topicName = topic.substring(getRedisTopicKey("").size)
      log.debug("onMessage topicName=" + topicName + ", messageId=" + id)
      PubSubActor !(PubSubActor.Dispatch, topicName, id)
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
            new Jedis(Config.redisAddress, Config.redisPort).subscribe(lsnr, getRedisTopicKey("a"))
        }
      }
    }
  }

}

object PubSubActor extends Actor with Logging {
  val pubsub: PubSub = RedisPubSub
  val storage: Storage = RedisStorage

  override def start = {
    pubsub.startDispatch
    super.start
  }

  case object Publish

  case object Subscribe

  case object UnSubscribe

  case object UnSubscribeAll

  case object Dispatch

  def act() {
    loop {
      react {
        case (Publish, topicName: String, message: MQTTPublishMessage) => pubsub.publish(message)
        case (Subscribe, clientId: String, topicNames: List[String]) => pubsub.subscribe(clientId, topicNames)
        case (UnSubscribe, clientId: String, topicNames: List[String]) => pubsub.unSubscribe(clientId, topicNames)
        case (UnSubscribeAll, clientId: String) => pubsub.unSubscribeAll(clientId)
        case (Dispatch, topicName: String, messageId: String) =>
          val message0 = storage.load(messageId)
          val message = new MQTTPublishMessage(false, MessageQoSLevel.AT_MOST_ONCE, false, topicName, 1, message0.mPayload)
          log.debug("Dispatch subscribers=" + storage.getSubscribers(topicName))
          for (clientId <- storage.getSubscribers(topicName))
            Container.activeChannels.get(clientId) match {
              case Some(client) => client ! message
              case None => if (message0.mQoSLevel > MessageQoSLevel.AT_MOST_ONCE) {
                log.error("inflight message:" + clientId + "," + messageId)
                storage.addToInbox(clientId, messageId)
              }
            }
        case _ => log.error("unmatch message")
      }
    }
  }
}