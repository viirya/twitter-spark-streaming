
package org.viirya.data.streaming

import org.apache.spark.streaming.{Seconds, StreamingContext}
import StreamingContext._
import org.apache.spark.SparkContext._
import org.apache.spark.streaming.twitter._
import org.apache.spark.SparkConf
import org.apache.spark.Logging

import org.apache.log4j.{Level, Logger}

import scala.io.Codec

import org.json4s._
import org.json4s.jackson.JsonMethods._

import com.redis._
import akka.actor.{ ActorSystem, Props }

import com.github.pmerienne.trident.ml.nlp.TwitterSentimentClassifier


object TwitterStream {
  def main(args: Array[String]) {
    if (args.length < 4) {
      System.err.println("Usage: TwitterStream <consumer key> <consumer secret> " +
        "<access token> <access token secret> [<filters>]")
      System.exit(1)
    }

    StreamingExamples.setStreamingLogLevels()

    val Array(consumerKey, consumerSecret, accessToken, accessTokenSecret) = args.take(4)
    val filters = args.takeRight(args.length - 4)

    // Set the system properties so that Twitter4j library used by twitter stream
    // can use them to generat OAuth credentials
    System.setProperty("twitter4j.oauth.consumerKey", consumerKey)
    System.setProperty("twitter4j.oauth.consumerSecret", consumerSecret)
    System.setProperty("twitter4j.oauth.accessToken", accessToken)
    System.setProperty("twitter4j.oauth.accessTokenSecret", accessTokenSecret)

    val sparkConf = new SparkConf().setAppName("TwitterStream")
    val ssc = new StreamingContext(sparkConf, Seconds(2))
    val stream = TwitterUtils.createStream(ssc, None, filters)

    val tweets = stream.map(status => {
      val text = status.getText
      val geo = status.getGeoLocation
      if (geo != null) {
        Some((geo.getLatitude, geo.getLongitude, text))
      } else {
        None
      }
    })

    val geoGrouped = tweets.map({
      case Some(tweet) =>
        val lat = tweet._1
        val lng = tweet._2
        val text = tweet._3
        (math.floor(lat * 10000).toString + math.floor(lng * 10000).toString, Some((lat, lng, text)))
      case None =>
        ("", None)        
    }).reduceByKeyAndWindow((first, second) => {
      first
    }, Seconds(60))

    geoGrouped.foreachRDD(rdd => {
      println("\nTweets in last 60 seconds (%s total):".format(rdd.count()))
      rdd.foreach{
        case (geoKey, Some(content)) => println("geoKey: %s lat:%f lng:%f".format(geoKey, content._1, content._2))
        case (geoKey, None) =>
      }
    })

    ssc.start()
    ssc.awaitTermination()
  }
}

object StreamingExamples extends Logging {

  /** Set reasonable logging levels for streaming if the user has not configured log4j. */
  def setStreamingLogLevels() {
    val log4jInitialized = Logger.getRootLogger.getAllAppenders.hasMoreElements
    if (!log4jInitialized) {
      // We first log something to initialize Spark's default logging, then we override the
      // logging level.
      logInfo("Setting log level to [WARN] for streaming example." +
        " To override add a custom log4j.properties to the classpath.")
      Logger.getRootLogger.setLevel(Level.WARN)
    }
  }
}
