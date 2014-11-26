
# Twitter Stream Application based on Spark Streaming

An example application of Twitter Stream based on Spark Streaming. The initial codes are based on the Spark example TwitterPopularTags. The purpose of this application is to demonstrate how to implement a Spark Streaming application with some predictive analysis capability. It would be a re-written implementation of previous Storm version. 

# Build

    sbt/sbt assembly

# Usage

    bin/spark-submit --class org.viirya.data.streaming.TwitterStream --master <spark master> --executor-memory <executor memory> --num-executors <number of executors> <built assembly jar file> <redis address> <classifier model file> <classifier fmap file> <classifier lmap file> <feature extractor json file> <classifier json file> <consumer key> <consumer secret> <access token> <access token secret> [<filters>]

# Tweet Classification

The tweet sentiment classification is provided by [Trident-ML](https://github.com/pmerienne/trident-ml). The necessary parameters are given as `<feature extractor json file>` and `<classifier json file>`.

The tweets stream is also classified using using the well-known 20 news groups data and the [Nak Machine Learning Library](https://github.com/scalanlp/nak). A Nak pre-trained classifier is given at the parameters as `<classifier model file>` and `<classifier fmap file>` and `<classifier lmap file>`.

# Custom Redis Library

Because the user given functions performed on RDDs in Spark would be serialized and deserialized to run on worker nodes, the classes and objects from third-party libraries should be serializable. This application sends geo-grouped tweets and tweet classification results to clients by Redis Pub/Sub mechanism. Due to the serialization issue, we choose the Redis library and modify its codes to let it implements Serializable interface.

The Redis library is tested with Redis 2.8.17.


