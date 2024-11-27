package es.ucm.fdi.linoleum.tools.simreplayer

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.*
import kotlin.system.exitProcess

private const val SCOPE_NAME = "es.ucm.fdi.linoleum.tools.simreplayer"
private const val SCOPE_VERSION = "0.1.0"
private const val SCOPE_SCHEMA_URL = "https://demiourgoi.github.io"

/** A simulated trace span
 * See https://opentelemetry.io/docs/concepts/signals/traces/
 *
 * @property parentId .spanId.spanId of the span that is the parent of this span, or null if
 * this is a root span. We implicitly assume the parent is a span of the same trace
 * as spanId.traceId
 * @property startTimeOffsetNs How much time (in nanoseconds) to wait since the start of the replay to start this span. The parent
 * trace is created on its first span
 * @property durationNs How much time (in nanoseconds) to wait since the span is created to close the span. Spans
 * involved in a SimSpanTree wait both for this duration and for their children spans to terminate.
 * https://github.com/envoyproxy/envoy/issues/21583
 * */
@Serializable
data class SimSpan(
    val spanId: SpanId, val parentId: String?=null,
    val spanName: String, val spanKind: SpanKind=SpanKind.INTERNAL,
    val startTimeOffsetNs: Long,
    val durationNs: Long,
    val attributes: Map<String, String> = emptyMap()) {

    companion object {
        fun fromJsonStr(jsonStr: String): Result<SimSpan> =
            runCatching {  Json.decodeFromString<SimSpan>(jsonStr) }

        fun new(spanId: String, traceId: String,
                startTimeOffsetNs: Long,  durationNs: Long,
                parentSpan: SimSpan?=null) = SimSpan(
            spanId = SpanId(traceId = traceId, spanId=spanId),
            parentId = parentSpan?.spanId?.spanId,
            spanName = spanId,
            startTimeOffsetNs = startTimeOffsetNs, durationNs = durationNs
        )

        fun waitForSpanRecording(span: Span) {
            while (!span.isRecording) {
                Thread.sleep(Duration.ofNanos(100))
            }
        }
    }

    // In this case the parent id has no information, as both should
    // have the same trace id
    val isRootSpan = parentId == null

    fun toJsonStr(): String = Json.encodeToString(this)

    fun start(tracer: Tracer, context: Context): Span {
        val spanBuilder = tracer
            .spanBuilder(spanName)
            .setSpanKind(spanKind)
        if (!isRootSpan) {
            spanBuilder.setParent(context)
        }
        attributes.forEach{ entry ->
            spanBuilder.setAttribute(entry.key, entry.value)
        }
        val span = spanBuilder.startSpan()
        // FIXME not clear this is required
        waitForSpanRecording(span)
        return span
    }

}

data class Tree<T>(val root: T, val children: List<Tree<T>>) {
    val list: List<T> by lazy {
        listOf(root) + children.map{it.list}.flatten()
    }
}

/**
 * Per https://opentelemetry.io/docs/concepts/signals/traces/ there is only one root span in a
 * trace, that represents the action that started the trace
 * See also https://grafana.com/docs/tempo/latest/introduction/
 * */
typealias SimSpanTree = Tree<SimSpan>
fun simSpanTree(spans: List<SimSpan>): SimSpanTree {
    val rootSpan = spans.first{ it.isRootSpan }
    // TODO find parent and resolve children, traversing the trace graph
    return Tree(rootSpan, emptyList())
}


/**
 * Identifier of a span in the simulation. These are arbitrary ids that won't be respected when emitting
 * the trace as the OTEL SDK will autogenerate new trace and span ids
 * A SimSpanId with an empty spanId is used for the parentId of the first span of a trace.
 * */
@Serializable
data class SpanId(val traceId: String, val spanId: String)


class SpanErrorsException(private val spanErrors: List<Throwable>): Exception() {
    override fun getLocalizedMessage(): String =
        spanErrors.map { it.message }.joinToString(separator = ",")
}

class SpanSimFilePlayer(
    private val tracer: Tracer,
    private val scheduler: ScheduledExecutorService = DEFAULT_NEW_SCHEDULER(),
    private val logger: Logger = LoggerFactory.getLogger(SpanSimFilePlayer::class.java.name)
) : Closeable {

    companion object {
        // https://stackoverflow.com/questions/763579/how-many-threads-can-a-java-vm-support
        // https://stackoverflow.com/questions/7726871/maximum-number-of-threads-in-a-jvm
        private const val MAX_THREAD_POOL_SIZE_ENV_VAR = "MAX_THREAD_POOL_SIZE"
        private val MAX_THREAD_POOL_SIZE = (System.getenv(MAX_THREAD_POOL_SIZE_ENV_VAR) ?: "5000").toInt()
        private val SCHEDULER_TERMINATION_TIMEOUT = Duration.ofSeconds(10)
        private val DEFAULT_NEW_SCHEDULER = {
            Executors.newScheduledThreadPool(
                MAX_THREAD_POOL_SIZE
            ) { r: Runnable ->
                val thread = Thread(r)
                // so it doesn't block JVM shutdown
                // Java closes on Ctrl+C by default https://stackoverflow.com/questions/1611931/catching-ctrlc-in-java
                thread.isDaemon = true
                thread
            }
        }
    }
    /**
     * TODO assumptions
     * @param path Path to a JSON lines (https://jsonlines.org/) formatted file with
     * JSON objects serializing SimSpan values
     * @param timeout This command blocks for timeout time waiting for the simulation to completes,
     * and aborts in case it did not complete on time
     * @return The list of actual span ids generated by OTEL, or an error instead
     * */
    fun playSim(path: Path, timeout: Duration): Result<List<SpanId>>{
        val lines = path.toFile().readLines()
        val spanResults = lines.map{SimSpan.fromJsonStr(it)}
        logger.debug("${spanResults.size} spans parsed with success")
        val parseFailures = spanResults.filter{it.isFailure}
        return when {
            parseFailures.isNotEmpty() -> parseFailures.first().map{ emptyList() }
            else -> playSim(spanResults.successes, timeout)
        }
    }

    /**
* TODO for reaply
* - order by time: be explicit about assumption of whether or not we allow spans to happen at the same millis
* - create trace on first span found; also handle wait for next span. Grouping by trace and short the spans in advance
*   might be useful. Consider using some kind of executor service for this
* - signal errors due to spans emitted late: log to mark this
* - Create unit test with an OTEL provider that does nothing: detect spans emitted late with a mock logger. Usa
* https://mockk.io/ for idiomatic mocking
* * Precondition: each SimSpanId is unique in a simulation file. spanName doesn't need to be
* * Precondition: each trace has a single root span that is a span with null parentId.spanId, that is also
* the span with lowest startTimeOffsetMillis
*
* TODO docment it blocks
*  // TODO reimplement with coroutines to avoid the limitation due to MAX_THREAD_POOL_SIZE
*
*  @return The list of actual span ids generated by OTEL, or an error instead
* */
    private fun playSim(spans: List<SimSpan>, timeout: Duration): Result<List<SpanId>>{
        // Span ids generated by OTEL
        val spanIds = ConcurrentLinkedQueue<SpanId>()
        // To wait for all spans to complete
        val allSpansComplete = CountDownLatch(spans.size)
        // To accumulate span scheduling or execution errors
        val spanErrors = ConcurrentLinkedQueue<Throwable>()

        /** Schedules the replay of the spans of a trace
          */
        fun replayTrace(spanTree: SimSpanTree) {
            val traceId = spans.first().spanId.traceId
            val replayStartTime = Duration.ofNanos(System.nanoTime())
            logger.info("Start scheduling of replay of trace with id $traceId at start time $replayStartTime")

            /** How much should the scheduler wait before creating a span, relative to `replayStartTimeNanos`
             * Fails if the time is negative, because that implies we are too late to
             * schedule the span
             * TODO document notes
             * */
            fun spanScheduleDelay(span: SimSpan): Result<Duration> = runCatching {
                val delay = span.startTimeOffsetNs + replayStartTime.toNanos() - System.nanoTime()
                check(delay > 0){"Schedule delay for span with id ${span.spanId} is negative: too late to schedule"}
                Duration.ofNanos(delay)
            }

            /**
             * TODO document
             * */
            fun remainingSpanTime(span: SimSpan, spanStartTime: Duration): Result<Duration> = runCatching {
                val duration = span.durationNs + spanStartTime.toNanos() - System.nanoTime()
                check(duration > 0){
                    "Remaining span duration for span with id ${span.spanId} is negative: not enough time to run the span"
                }
                Duration.ofNanos(duration)
            }

            /**
             * Schedules the root span and adds the corresponding future to futures
             * @throws IllegalStateException when this tries to schedule the root trace too late
             * */
            fun scheduleSpanReplay(context: Context, spanTree: SimSpanTree, spanComplete: CountDownLatch?=null) {
                val span = spanTree.root
                val spanDelayResult = spanScheduleDelay(span)
                spanDelayResult.onFailure { exception -> spanErrors.add(exception) }
                val spanDelay = spanDelayResult.getOrNull()
                if (spanDelay == null) {
                    logger.error("Failure scheduling span tree $spanTree", spanDelayResult.exceptionOrNull())
                    spanComplete?.countDown()
                    spanTree.list.forEach{ _ -> allSpansComplete.countDown()}
                    return
                }

                logger.info("Scheduling $span with delay $spanDelay")
                scheduler.schedule({
                    val spanStartTime = Duration.ofNanos(System.nanoTime())
                    val emittedSpan = span.start(tracer, context)
                    val emittedSpanCtx = emittedSpan.spanContext
                    val otelSpanId = SpanId(traceId = emittedSpanCtx.traceId, spanId = emittedSpanCtx.spanId)
                    logger.info("Starting span with id ${span.spanId} emitted with OTEL id $otelSpanId")
                    spanIds.add(otelSpanId)

                    // launch children, but let them add themselves to futures
                    val childrenContext = emittedSpan.storeInContext(context)
                    val childrenComplete = CountDownLatch(spanTree.children.size)
                    spanTree.children.forEach{
                        scheduleSpanReplay(childrenContext, it, childrenComplete)
                    }

                    fun completeSpan(onComplete: () -> Unit) {
                        // wait for children
                        childrenComplete.await()
                        // complete span emission
                        emittedSpan.end()
                        // notify completion
                        spanComplete?.countDown()
                        allSpansComplete.countDown()

                        onComplete()
                    }

                    remainingSpanTime(span, spanStartTime).fold({remainingTime ->
                        scheduler.schedule({
                            completeSpan{ logger.info("Span with id ${span.spanId} completed with success") }
                        }, remainingTime.toNanos(), TimeUnit.NANOSECONDS)
                    }, { exception ->
                        spanErrors.add(exception)
                        completeSpan{ logger.error("Span with id ${span.spanId} run out of time", exception) }
                    })
                }, spanDelay.toNanos(), TimeUnit.NANOSECONDS)
            }

            // https://javadoc.io/doc/io.opentelemetry/opentelemetry-context/1.1.0/io/opentelemetry/context/Context.html
            val rootContext = Context.root()
            scheduleSpanReplay(rootContext, spanTree)
            logger.info("Completed scheduling of replay of trace with id $traceId")
        }

        // Add some padding to the span to cover the delay until we start scheduling
        val schedulePadding = Duration.ofSeconds(1)
        val paddedSpans = spans.map{ it.copy(startTimeOffsetNs = it.startTimeOffsetNs + schedulePadding.toNanos())}
        logger.info("Added $schedulePadding schedule padding of to all spans")

        // Schedule all spans
        paddedSpans.groupBy{ it.spanId.traceId }.values.forEach{
            // FIXME implement simSpanTree
            replayTrace(simSpanTree(it))
        }

        // Wait for all spans to complete
        runCatching {
            allSpansComplete.await(timeout.toNanos(), TimeUnit.NANOSECONDS)
        }.recover { exception ->
            return Result.failure(exception)
        }

        if (spanErrors.isNotEmpty()) {
            return Result.failure(SpanErrorsException(spanErrors.toList()))
        }

        return Result.success(spanIds.toList())
    }

    override fun close() {
        // Shutting down as recommended in https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html
        scheduler.shutdown()  // Disable new tasks from being submitted
        try {
            if (!scheduler.awaitTermination(
                    SCHEDULER_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow() // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!scheduler.awaitTermination(
                        SCHEDULER_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    logger.error("Failure shutting down thread pool")
                }
            }
        } catch (ie: InterruptedException) {
            // (Re-)Cancel if current thread also interrupted
            scheduler.shutdownNow()
            // Preserve interrupt status
            Thread.currentThread().interrupt()
        }
    }
}

/** @return Filters and unwraps the successful results */
val <T> List<Result<T>>.successes: List<T>
    get() = this.flatMap {
        val value = it.getOrNull()
        when {
            value != null -> listOf(value)
            else -> emptyList()
        }
    }

/**
 * @return a suitable open telemetry API implementation, typically a configured
 * OTEL SDK instance
 * */
fun provideOtel(): OpenTelemetry {
    // https://opentelemetry.io/docs/languages/java/configuration/#zero-code-sdk-autoconfigure
    return AutoConfiguredOpenTelemetrySdk.initialize().openTelemetrySdk
}

fun provideTracer(otel: OpenTelemetry): Tracer {
    return otel.tracerProvider
        .tracerBuilder(SCOPE_NAME)
        .setInstrumentationVersion(SCOPE_VERSION)
        .setSchemaUrl(SCOPE_SCHEMA_URL)
        .build()
}

object EnvVars {
    const val BUILD_DIR_VAR_NAME = "BUILD_DIR"
    const val SIM_FILE_PATH_VAR_NAME = "SIM_FILE_PATH"
    const val REPLAY_TIMEOUT_VAR_NAME = "REPLAY_TIMEOUT"
    val REPLAY_TIMEOUT_DEFAULT_SECS = Duration.ofMinutes(5).toSeconds()
}
object ExitCodes {
    const val MISSING_ARGS = 1
    const val REPLAY_ERROR = 2
}

fun main() {
    val logger = LoggerFactory.getLogger("root")

    val simFilePath = System.getenv(EnvVars.SIM_FILE_PATH_VAR_NAME)
    if (simFilePath == null) {
        logger.error("Missing value for required env var ${EnvVars.SIM_FILE_PATH_VAR_NAME}, aborting")
        exitProcess(ExitCodes.MISSING_ARGS)
    }
    val replayTimeout = Duration.ofSeconds(
        System.getenv(EnvVars.REPLAY_TIMEOUT_VAR_NAME)?.toLong()
             ?: EnvVars.REPLAY_TIMEOUT_DEFAULT_SECS)
    logger.info("Using simFilePath=$simFilePath, replayTimeout=${replayTimeout.toSeconds()} seconds")

    val otel = provideOtel()
    val tracer = provideTracer(otel)
    val replayer = SpanSimFilePlayer(tracer)
    replayer.playSim(Path.of(simFilePath), replayTimeout)
        .onFailure { exception ->
            logger.error("Failure replaying file $simFilePath", exception)
            exitProcess(ExitCodes.REPLAY_ERROR)
        }
        .onSuccess { spanIds ->
            logger.info("Successfully processed ${spanIds.size} spans")
            spanIds.forEach{ spanId ->
                logger.debug("spanId {}", spanId)
            }
        }
}
