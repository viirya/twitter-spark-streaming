
# Twitter Stream Application based on Spark Streaming

An example application of Twitter Stream based on Spark Streaming. The initial codes are based on the Spark example TwitterPopularTags. The purpose of this application is to demonstrate how to implement a Spark Streaming application with some predictive analysis capability. It would be a re-written implementation of previous Storm version. 

# Build

    sbt/sbt assembly

# Usage

    bin/spark-submit --class org.viirya.data.streaming.TwitterStream --master <spark master> --executor-memory <executor memory> --num-executors <number of executors> <built assembly jar file> <consumer key> <consumer secret> <access token> <access token secret> [<filters>]


