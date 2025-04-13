package es.ucm.fdi.demiourgoi.linoleum.ltlss

import io.jaegertracing.api_v3.QueryServiceOuterClass.TraceQueryParameters
import com.google.protobuf.{ByteString, Timestamp}
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.connector.base.source.reader.SourceReaderBase
import org.apache.flink.api.connector.source.{SourceSplit, SplitEnumerator, SplitEnumeratorContext}
import org.slf4j.LoggerFactory
import java.util
import java.time.Instant

package object messages {
  implicit class LinoleumSpanInfo(self: SpanInfo) {
    val hexTraceId: String = byteString2HexString(self.getSpan.getTraceId)
    val hexSpanId: String = byteString2HexString(self.getSpan.getSpanId)
    /** Per https://opentelemetry.io/docs/concepts/signals/traces/ a root span,
     * "denoting the beginning and end of the entire operation [...]  has no parent_id"
     * which in https://github.com/open-telemetry/opentelemetry-proto/blob/4ca4f0335c63cda7ab31ea7ed70d6553aee14dce/opentelemetry/proto/trace/v1/trace.proto
     * is encoded in `bytes parent_span_id = 4;` that is not optional, as "If this is a root span, then this field
     * must be empty"
     * */
    val isRoot: Boolean = self.getSpan.getParentSpanId.isEmpty
  }
  // From https://stackoverflow.com/questions/2756166/what-is-are-the-scala-ways-to-implement-this-java-byte-to-hex-class
  def byteString2HexString(byteString: ByteString): String =
    byteString.toByteArray.map("%02X" format _).mkString.toLowerCase
}

object TimeUtils {
  def instantToTimestamp(instant: Instant): Timestamp =
    Timestamp.newBuilder()
      .setSeconds(instant.getEpochSecond)
      .setNanos(instant.getNano)
      .build()

  def timestampToInstant(timestamp: Timestamp): Instant =
    Instant.ofEpochSecond(timestamp.getSeconds, timestamp.getNanos)
}

package object sources {
  type SpanInfoStream = DataStream[SpanInfo]
}
package sources {

  import java.time.Duration
  import java.util.PriorityQueue
  import java.util.concurrent.Callable
  // https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/sources/
  // Note the rich source function API is deprecated
  /**
   * Data source that queries Jaeger for Span data using a find traces query determined by
   * the data source parameters
   *
   * TODO document https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/guarantees/
   *
   * @param queries are used as query templates to constantly call Jaeger's FindTraces,
   *                overriding the parameters start_time_min and start_time_max set
   *                in those queries
   * @param queriesStartTime initial time to use for all queries. If None then the processing time when the source
   *                         is started is used
   * */
  class JaegerDataSource(queries: Set[TraceQueryParameters], queriesStartTime: Option[Instant] = None) {
    // TODO
  }

  /**
   * A source split that represents a TraceQueryParameters to be run for some values for
   * start_time_min  and start_time_max
   *
   * Invariant: startTimeMin < startTimeMax
   * */
  @SerialVersionUID(1L)
  class JaegerSourceSplit(
                           val query: TraceQueryParameters,
                           val startTimeMin: Timestamp,
                           val startTimeMax: Timestamp
                         ) extends SourceSplit with Serializable {
    // Example https://github.com/apache/flink/blob/release-2.0/flink-connectors/flink-connector-files/src/main/java/org/apache/flink/connector/file/src/FileSourceSplit.java
    /** The unique ID of the split. Unique within the scope of this source. */
    override def splitId(): String = s"${query.hashCode()}-${startTimeMin.getNanos}/${startTimeMax.getNanos}"
  }

  object JaegerSplitEnumerator {
    import TimeUtils._

    private val log = LoggerFactory.getLogger(JaegerSplitEnumerator.getClass.getName)
    private type SubtaskId = Int
    private type StartTimeMin = Timestamp

    /*
    *     case class State(readerToQuery: Map[SubtaskId, TraceQueryParameters],
                     queryOffsets: Map[TraceQueryParameters, Timestamp])
    * */

    // FIXME: add list for queries pending to assign
    // FIXME: simplifica, no asigna reader a query sino
    // - lleva lista de splits devueltos, para dar prioridad al asignar
    // - si no hay split devueltos usa un heap para ordenar (offset, query) por offset ascendente,
    //   y asi cuando saco un offset meto el siguiente para el salto ... o simplemente cola pq entra ya ordernado
    /**
     * - Invariant: queryOffsets is ordered in increasing order of the timestamp
     * - Invariant: all entries in pendingSplits have startTimeMax < queryOffsets.peek()(0). This is
     *              because all entries in pendingSplits where previously on queryOffsets
     * */
    case class State(
                      pendingSplits: util.Deque[JaegerSourceSplit],
                      queryOffsets: util.PriorityQueue[(StartTimeMin, TraceQueryParameters)]
                    ) {

      /** @return the minimum startTimeMax (right bound of the time interval) for the next split
       *         if it exists, giving priority to pendingSplits; or None if it does not
       * */
      def peekNextSplitStartTimeMax(splitDuration: Duration): Option[Timestamp] = {
        if (!pendingSplits.isEmpty) {
          Some(pendingSplits.peek().startTimeMax)
        } else if (!queryOffsets.isEmpty){
          val (startTimeMin, _) = queryOffsets.peek()
          Some(extendStartTime(splitDuration, startTimeMin))
        } else {
          None
        }
      }

      /** @return the next split if it exists, giving priority to pendingSplits; or None if it does not
       *         If the split is extracted from queryOffsets this also advances the offset for the
       *         corresponding query on queryOffsets*/
      def pollNextSplit(splitDuration: Duration): Option[JaegerSourceSplit] = {
        if (!pendingSplits.isEmpty) {
          Some(pendingSplits.poll())
        } else if (!queryOffsets.isEmpty){
          val (startTimeMin, query) = queryOffsets.poll()
          val startTimeMax = extendStartTime(splitDuration, startTimeMin)
          // advance the offset for this query
          queryOffsets.add((startTimeMax, query))
          Some(
            new JaegerSourceSplit(query, startTimeMin, startTimeMax)
          )
        } else {
          None
        }
      }

      /** Gets the right bound of the time interval for a startTimeMin (left bound) */
      private def extendStartTime(splitDuration: Duration, startTimeMin: Timestamp): Timestamp =
        instantToTimestamp(timestampToInstant(startTimeMin).plus(splitDuration))
    }

    private val queryOffsetsOrdering: Ordering[(StartTimeMin, TraceQueryParameters)] = Ordering[Long].on(_._1.getSeconds)

    def initial(queriesStartTime: Instant, queries: Set[TraceQueryParameters]): State = {
      val queryOffsets = new PriorityQueue[(StartTimeMin, TraceQueryParameters)](queryOffsetsOrdering)
      val startTimeTimestamp = instantToTimestamp(queriesStartTime)
      queries.foreach{ query => queryOffsets.add((startTimeTimestamp, query)) }
      State(new util.LinkedList(), queryOffsets)
    }
  }
  /**
   * @param splitDuration - how long the splits are. Longer durations imply fewer queries to Jaeger, but higher latency
   *                      as it takes more time for the split to be ready
   * */
  class JaegerSplitEnumerator(context: SplitEnumeratorContext[JaegerSourceSplit],
                              state: JaegerSplitEnumerator.State,
                              splitDuration: Duration, maxJaegerIngestionLatency: Duration)
    extends SplitEnumerator[JaegerSourceSplit, JaegerSplitEnumerator.State] {
    import JaegerSplitEnumerator._
    import TimeUtils._

    override def start(): Unit = {
      log.warn("Starting JaegerSplitEnumerator for state {}", state)
      // Note we can call with a delay only with a schedule. As a workaround we call it faster assuming
      // most calls will check there is nothing to do
      context.callAsync(() => { assignSplits()}, onAssignSplitsError, 0L, splitDuration.toMillis / 10)
    }

    /**
     * Handles the request for a split.
     * This method is called when the reader with the given subtask id calls the SourceReaderContext.sendSplitRequest() method.
    * */
    override def handleSplitRequest(subtaskId: Int, requesterHostname: String): Unit = {
      // TODO dar split por pasos de tiempo, que determina el riesgo que se asume de dar
      // trabajo a un reader, y como de larga es la query a Jaeger. Para ello add final
      // tb al split. Pon esa longitud de parametro de eso

      // Cuidado definir el rango de tiempo en assignment, pq no se cuando va a ejecutar la busqueda
      // el reader... y tampoco puedo darle rango de tiempo en futuro pq perderia esos valores
      // Asi que otra idea es hacerlo eager como en https://github.com/apache/flink-connector-kafka/blob/main/flink-connector-kafka/src/main/java/org/apache/flink/connector/kafka/source/enumerator/KafkaSourceEnumerator.java
      // pq asi tampoc hago block y wait aqui a que llegue el siguiente instante
      // Podria hacer productor / consumidor con una cola thread safe que tenga write no blocking y read blocking
      // Eso se basa en context.callAsync (de SplitEnumeratorContext) para hacer periodic assingment de split segun pasa el tiempo
      // para ello uso los subtaskID que recolecto de addReader, y si no hay reader pues espero a siguiente pulso pero haceiendio
      // mas largo el rango de tiempo. Ver en kafka `    public void addSplitsBack(List<KafkaPartitionSplit> splits, int subtaskId) {`
      // que usa context.registeredReaders para ver que readers hay ... pues entonces no hazlo stateful sino distribuye splits en cada pulso
      // Puedo usar Collections.shuffle para random permutation de context.registeredReaders y asignar en orden los splits a la permutacion
      // que como permutan pues asi doistribyyo entre readers
      // FIXME context.assignSplit()

    }

    private def assignSplits(): Unit = {
      log.debug("Assigning split on state {}", state)
      val availableReaders = getReadersPermutation
      if (availableReaders.isEmpty) {
        log.warn("Waiting for some reader to get available")
        return
      }

      // FIXME: if we use event time here then we are assuming Jaeger has zero ingestion time: that´s incorrect
      // AQUI: add parameter maxJaegerIngestionLatency (ver arriba) que es una duration que es lo que asumimos que tarda Jaeger
      // en ingerir datos, y documentar que es una cruda approximacion. Crea issue contando esto, y diciendo de explorar
      // opciones con otro trace backend que tenga un WAL: es posible que Tempo lo teng para federacion

      val currentTime = Instant.now()
      var readerIdx = 0
      state.peekNextSplitStartTimeMax(splitDuration) match {
        case Some(nextSplitStartTimeMax) if timestampToInstant(nextSplitStartTimeMax).isBefore(currentTime) =>
          state.pollNextSplit(splitDuration).fold(
            log.error("No pending split found on state {}", state)
          ){ nextSplit =>
            // Note pollNextSplit already extends the state
            val nextReader = availableReaders.get(readerIdx)
            readerIdx = (readerIdx + 1) % availableReaders.size()
            log.info("Assigning split {} to reader {}", nextSplit, nextReader)
            log.debug("State after assigning split {} to reader {}: {}", nextSplit, nextReader, state)
            context.assignSplit(nextSplit, nextReader)
          }
      }
    }

    private def onAssignSplitsError(v: Unit, throwable: Throwable): Unit = {
      // FIXME how to recover from pollNextSplit advancing the offset? Could go to pendingSplits
      // or pollNextSplit could return the next split if any instaed of pushing it already. DO not overcomplicate ..
      // consider a big try-catch on assignSplits that saves the split and and onAssignSplitsError just logging ..
      log.error("assigning splits", throwable)
    }

    private def getReadersPermutation = {
      val readersList = new util.ArrayList(context.registeredReaders().keySet())
      util.Collections.shuffle(readersList)
      readersList
    }

    /** Add splits back to the split enumerator.
     * This will only happen when a SourceReader fails and there are splits assigned to it after the
     * last successful checkpoint.
    * */
    override def addSplitsBack(splits: util.List[JaegerSourceSplit], i: Int): Unit = {
      // FIXME sort splits before adding, or better use a priority queue here too
      splits.forEach{state.pendingSplits.addLast(_)}
    }

    // FIXME note same ide as handleSplitRequest
    override def addReader(subtaskId: Int): Unit = ???

    /**
     * Creates a snapshot of the state of this split enumerator, to be stored in a checkpoint.
     *
     * The snapshot should contain the latest state of the enumerator: It should assume that all
     * operations that happened before the snapshot have successfully completed. For example all
     * splits assigned to readers via SplitEnumeratorContext.assignSplit(SourceSplit, int) and
     * SplitEnumeratorContext.assignSplits(SplitsAssignment)) don't need to be included in the snapshot anymore.
     * */
    override def snapshotState(checkpointId: Long): JaegerSplitEnumerator.State = ???

    override def close(): Unit = ???
  }

  // FIXME An abstract implementation of SourceReader which provides some synchronization between the mail box main
  //  thread and the SourceReader internal threads. This class allows user to just provide a SplitReader and
  //  snapshot the split state.
  // TODO should be stateless: state is in SourceSplits
  class JaegerDataSourceReader { // } extends SourceReaderBase[SpanInfo] {

  }



  // TODO make sure we use event time. COuld be on main
  //     env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
  // assignAscendingTimestamps does not make sense now
  // Per https://nightlies.apache.org/flink/flink-docs-release-2.0/docs/dev/datastream/sources/#event-time-and-watermarks
  // this is independent, but it makes sense to also have the strategy in this package for consumers of this source
  // that might find it useful, and also because this source is specific of Spans (vs other generic sources like a Kafka source)
  // > The TimestampAssigner and WatermarkGenerator run transparently as part of the ReaderOutput(or SourceOutput)
  // > so source implementors do not have to implement any timestamp extraction and watermark generation code.

}


/*
TODO
While a SplitEnumerator implementation can work well in a reactive way by
 only taking coordination actions when its method is invoked, some
 SplitEnumerator implementations might want to take actions actively.
 For example, a SplitEnumerator may want to periodically run split discovery and assign the new
 splits to the SourceReaders. Such implementations may find that the callAsync() method in the SplitEnumeratorContext
  is handy. The code snippet below shows how the SplitEnumerator implementation can achieve that without maintaining
  its own threads.
* */