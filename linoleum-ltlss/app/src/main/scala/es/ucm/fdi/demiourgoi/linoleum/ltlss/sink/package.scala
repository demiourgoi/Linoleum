package es.ucm.fdi.demiourgoi.linoleum.ltlss

import org.apache.flink.connector.mongodb.sink.MongoSink
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.connector.base.DeliveryGuarantee
import com.mongodb.client.model.InsertOneModel

import org.slf4j.LoggerFactory

import config.LinoleumConfig

package object sink {
  type EvaluatedTraceSink = MongoSink[EvaluatedTrace]
}

package sink {
  object LinoleumSink {
    private val log = LoggerFactory.getLogger(classOf[LinoleumSink])
  }
  class LinoleumSink(cfg: LinoleumConfig) {
    import LinoleumSink._

    def apply(evaluatedTraces: DataStream[EvaluatedTrace]): Unit = {
      evaluatedTraces.sinkTo(buildSink())
    }

    private def buildSink(): MongoSink[EvaluatedTrace] = {
      val mongoCfg = cfg.sink.mongoDb
      MongoSink.builder[EvaluatedTrace]()
        .setUri(mongoCfg.mongoUri)
        .setDatabase(mongoCfg.mongoDatabase)
        .setCollection(mongoCfg.mongoCollection)
        .setBatchSize(mongoCfg.mongoBatchSize)
        .setBatchIntervalMs(mongoCfg.mongoBatchIntervalMs)
        .setMaxRetries(mongoCfg.mongoMaxRetries)
        .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
        .setSerializationSchema((trace, _) => {
          log.info("Writing evaluated trace {} to MongoDB", trace)
          new InsertOneModel(trace.toBsonDocument)
        })
        .build()
    }
  }
}
