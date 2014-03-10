package org.zhutou.skeeter

import com.typesafe.config.ConfigFactory

object Config {
  val conf = ConfigFactory.load()

  def serverPort = conf.getInt("server-port")
  def redisAddress = conf.getString("redis.address")
  def redisPort = conf.getInt("redis.port")
  def redisKeyPrefix = conf.getString("redis.key-prefix")

}


