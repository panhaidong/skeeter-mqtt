package org.zhutou.skeeter

import redis.clients.jedis.Jedis
import collection.JavaConversions._

abstract class Storage {
  def save(payload: Array[Byte]): String

  def load(id: String): Array[Byte]

  def subscribe(clientId: String, topicNames: List[String])

  def unSubscribe(clientId: String, topicNames: List[String])

  def unSubscribe(clientId: String)

  def getSubscribers(topicName: String): List[String]

  def getSubscribedTopics(clientId:String) : List[String]
}

object RedisStorage extends Storage {
  val jedis = new Jedis(Config.redisAddress, Config.redisPort)

  def generateId(): String = jedis.incr("K").toString

  override def save(payload: Array[Byte]): String = {
    val id = generateId()
    jedis.set(id.getBytes("UTF-8"), payload)
    id
  }

  override def load(id: String): Array[Byte] = jedis.get(id.getBytes("UTF-8"))

  override def subscribe(clientId: String, topicNames: List[String]) = {
    jedis.sadd(clientId, topicNames: _*)
    for (topicName <- topicNames) {
      jedis.sadd(topicName, clientId)
    }
  }

  override def unSubscribe(clientId: String, topicNames: List[String]) = {
    jedis.srem(clientId, topicNames: _*)
    for (topicName <- topicNames) {
      jedis.srem(topicName, clientId)
    }
  }

  override def unSubscribe(clientId: String) = {
    for (topicName <- jedis.smembers(clientId)) {
      jedis.srem(topicName, clientId)
    }
    jedis.del(clientId)

  }

  override def getSubscribers(topicName: String) = jedis.smembers(topicName).toList
  override def getSubscribedTopics(clientId: String) = jedis.smembers(clientId).toList
}

