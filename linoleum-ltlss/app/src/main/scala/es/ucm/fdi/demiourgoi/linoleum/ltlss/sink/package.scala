package es.ucm.fdi.demiourgoi.linoleum.ltlss

import org.apache.flink.connector.mongodb.sink.MongoSink
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.connector.base.DeliveryGuarantee
import com.mongodb.client.model.InsertOneModel
import source.LinoleumConfig
import org.slf4j.LoggerFactory

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
      
      MongoSink.builder[EvaluatedTrace]()
        .setUri(cfg.mongoUri)
        .setDatabase(cfg.mongoDatabase)
        .setCollection(cfg.mongoCollection)
        .setBatchSize(cfg.mongoBatchSize)
        .setBatchIntervalMs(cfg.mongoBatchIntervalMs)
        .setMaxRetries(cfg.mongoMaxRetries)
        .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
        .setSerializationSchema((trace, _) => {
          log.info("Writing evaluated trace {} to MongoDB", trace)
          new InsertOneModel(trace.toBsonDocument)
        })
        .build()
    }
  }
}
