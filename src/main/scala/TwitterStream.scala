
package org.viirya.data.streaming

import org.apache.spark.streaming.{Seconds, StreamingContext}
import StreamingContext._
import org.apache.spark.SparkContext._
import org.apache.spark.streaming.twitter._
import org.apache.spark.SparkConf
import org.apache.spark.Logging

import collection.mutable.{Map, HashMap}

import org.apache.log4j.{Level, Logger}

import org.json4s._
import org.json4s.jackson.JsonMethods._

import redis.clients.jedis.Jedis;

import org.viirya.data.ml.nlp.TwitterSentimentClassifier


object TwitterStream {
  def main(args: Array[String]) {
    if (args.length < 10) {                                                 
      System.err.println("Usage: TwitterStream <redis address> <model path> <fmap path> <lmap path>" +
        "<feature path> <classifier path> <consumer key> <consumer secret> " +
        "<access token> <access token secret> [<filters>]")
      System.exit(1)
    }

    StreamingExamples.setStreamingLogLevels()

    val Array(redisAddr, modelPath, fmapPath, lmapPath, featurePath, classifierPath, consumerKey, consumerSecret, accessToken, accessTokenSecret) = args.take(10)
    val filters = args.takeRight(args.length - 10)

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


    val classifier = new TweetClassifier(new Pub().init(redisAddr), modelPath, fmapPath, lmapPath, featurePath, classifierPath)

    geoGrouped.foreachRDD(rdd => {
      println("\nTweets in last 60 seconds (%s total):".format(rdd.count()))
      rdd.foreach{
        case (geoKey, Some(content)) =>
          println("geoKey: %s lat:%f lng:%f".format(geoKey, content._1, content._2))
          classifier.process(geoKey, content._1, content._2, content._3)
        case (geoKey, None) =>
      }
    })

    ssc.start()
    ssc.awaitTermination()
  }
}
 
class Pub extends Serializable {

  var r: Jedis = null
  
  def init(address: String) = {
    r = new Jedis(address, 6379) with Serializable
    this
  }

  def publish(channel: String, message: String) = {
    if (r != null) {
      r.publish(channel, message)
    }
  }
}

class TweetClassifier(val publisher: Pub, val modelPath: String, val fmapPath: String, val lmapPath: String, val featurePath: String, val classifierPath: String) extends Serializable {

  import nak.NakContext._
  import nak.core._
  import nak.data._
  import nak.liblinear.LiblinearConfig
  import nak.util.ConfusionMatrix

  import java.io.File


  var average_lat: Map[String, List[Double]] = _
  var average_lng: Map[String, List[Double]] = _
  var insert_time: Map[String, List[Long]] = _
  var grp_tweets: Map[String, List[String]] = _

  val stopwords = Set("the","a","an","of","in","for","by","on")
  var featurizer: Featurizer[String, String] = _
  var classifier: IndexedClassifier[String] with FeaturizedClassifier[String, String] = null

  var tweet_classifier: TwitterSentimentClassifier = _

  average_lat = new HashMap[String, List[Double]]().withDefaultValue(List())
  average_lng = new HashMap[String, List[Double]]().withDefaultValue(List())
  insert_time = new HashMap[String, List[Long]]().withDefaultValue(List())
  grp_tweets = new HashMap[String, List[String]]().withDefaultValue(List())

  setup_classifier()

  def setup_classifier() = {
 
    featurizer = new BowFeaturizer(stopwords) with Serializable
   
    val modelFile = new File(modelPath) 
    val fmapFile = new File(fmapPath)
    val lmapFile = new File(lmapPath)

    classifier = loadClassifier(modelFile, fmapFile, lmapFile, featurizer)

    tweet_classifier = new TwitterSentimentClassifier(featurePath, classifierPath)

  }

  def predict_tweets(tweets: String) = {

    val maxLabelNews = maxLabel(classifier.labels) _
    maxLabelNews(classifier.evalRaw(tweets))
  }

  def predict_tweets_sentiment(tweets: String): String = {
    tweet_classifier.classify(tweets) match {
      case java.lang.Boolean.TRUE => return "positive"
      case java.lang.Boolean.FALSE => return "negative"
      case _ => return "unknown"
    }
  }

  def group_publish(elem_key: String) = {
 
      var all_lat = 0.0
      var all_lng = 0.0
 
      average_lat(elem_key).foreach((lat) => all_lat += lat)
      average_lng(elem_key).foreach((lng) => all_lng += lng)

      if (average_lat(elem_key).length > 0) {
        all_lat /= average_lat(elem_key).length
        all_lng /= average_lng(elem_key).length
      }

      var concated_txt = ""
      grp_tweets(elem_key).foreach((txt) => concated_txt += " " + txt)

      val predict_label = predict_tweets(concated_txt)
      var sentiment_label = predict_tweets_sentiment(concated_txt)

      if (all_lat != 0.0 || all_lng != 0.0 || average_lat(elem_key).length == 0) {
        if (publisher != null) {
          publisher.publish("tweets", elem_key + ":" + all_lat.toString() + ":" + all_lng.toString() + ":" + average_lat(elem_key).length + "\t" + concated_txt + "\t" + predict_label + "\t" + sentiment_label)
        }
      }

      if (average_lat(elem_key).length == 0) {
        average_lat.remove(elem_key)
        average_lng.remove(elem_key)
        insert_time.remove(elem_key)
        grp_tweets.remove(elem_key)
      }
 
  } 

  def deQueue() = {

    val current_time = System.currentTimeMillis
    average_lat.foreach((elem) => {
      var count = 0
      average_lat(elem._1) = elem._2.filterNot((tweet) => {
        count += 1
        (current_time - insert_time(elem._1)(count - 1)) / 1000 > 5
      })
    })
    
    average_lng.foreach((elem) => {
      var count = 0
      average_lng(elem._1) = elem._2.filterNot((tweet) => {
        count += 1
        (current_time - insert_time(elem._1)(count - 1)) / 1000 > 5
      })
    })
    
    grp_tweets.foreach((elem) => {
      var count = 0
      grp_tweets(elem._1) = elem._2.filterNot((tweet) => {
        count += 1
        (current_time - insert_time(elem._1)(count - 1)) / 1000 > 5
      })
    })
    
    insert_time.foreach((elem) => {
      insert_time(elem._1) = elem._2.filterNot((record_time) => {
        (current_time - record_time) / 1000 > 5
      })
    }) 
    
    average_lat.foreach((elem) => {
      if (elem._2.length == 0) {
        group_publish(elem._1)
      }
    })

  }

  def process(geoKey: String, lat: Double, lng: Double, text: String) = {
      
      average_lat(geoKey) = lat :: average_lat(geoKey)
      average_lng(geoKey) = lng :: average_lng(geoKey)
      insert_time(geoKey) = System.currentTimeMillis :: insert_time(geoKey)
      grp_tweets(geoKey) = text :: grp_tweets(geoKey)

      group_publish(geoKey)

      if (publisher != null) {
        publisher.publish("ori_tweets", lat.toString() + ":" + lng.toString())
      }
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
