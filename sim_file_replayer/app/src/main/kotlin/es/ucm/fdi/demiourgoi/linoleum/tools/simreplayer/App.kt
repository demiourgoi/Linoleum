package es.ucm.fdi.demiourgoi.linoleum.tools.simreplayer

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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.concurrent.*
import kotlin.system.exitProcess
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.collections.Iterable

import arrow.core.flatMap

private const val SCOPE_NAME = "es.ucm.fdi.demiourgoi.linoleum.tools.simreplayer"
private const val SCOPE_VERSION = "0.1.0"
private const val SCOPE_SCHEMA_URL = "https://demiourgoi.github.io"

/** A simulated trace span
 * See https://opentelemetry.io/docs/concepts/signals/traces/
 *
 * @property parentId .spanId.spanId of the span that is the parent of this span, or null if
 * this is a root span. We implicitly assume the parent is a span of the same trace
 * as spanId.traceId
 * @property startTimeOffsetMs How much time (in milliseconds) to wait since the start of the replay to start this span. The parent
 * trace is created on its first span
 * @property durationMs How much time (in milliseconds) to wait since the span is created to close the span. Spans
 * involved in a SimSpanTree wait both for this duration and for their children spans to terminate.
 * https://github.com/envoyproxy/envoy/issues/21583
 * */
@Serializable
data class SimSpan(
    val spanId: SpanId, val parentId: String?=null,
    val spanName: String, val spanKind: SpanKind=SpanKind.INTERNAL,
    val startTimeOffsetMs: Long,
    val durationMs: Long,
    val attributes: Map<String, String> = emptyMap()) {

    companion object {
        fun fromJsonStr(jsonStr: String): Result<SimSpan> =
            runCatching {  Json.decodeFromString<SimSpan>(jsonStr) }

        fun new(spanId: String, traceId: String,
                startTimeOffsetMs: Long, durationMs: Long,
                spanName: String?=null, parentSpan: SimSpan?=null) = SimSpan(
            spanId = SpanId(traceId = traceId, spanId=spanId),
            parentId = parentSpan?.spanId?.spanId,
            spanName = spanName ?: spanId,
            startTimeOffsetMs = startTimeOffsetMs, durationMs = durationMs)

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
fun simSpanTree(spans: List<SimSpan>): Result<SimSpanTree> = runCatching {
    val spanIds = spans.map{it.spanId}
    require(spanIds.toSet().size == spanIds.size){"found duplicate span id"}

    fun buildSimSpanTree(root: SimSpan? = null): SimSpanTree {
        val rootSpan = root ?: spans.first{ it.isRootSpan }
        val children = spans.filter { it.parentId?.equals(rootSpan.spanId.spanId) ?: false }
        return Tree(rootSpan, children.map(::buildSimSpanTree))
    }

    val tree = buildSimSpanTree()
    check(tree.list.size == spans.size){"spans dependencies are not a tree"}
    tree
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
        private val MAX_SCHEDULE_DELAY_LATENESS = Duration.ofMillis(500)
    }
    /**
     * Replay a json lines file with a SimSpan per line.
     * This call blocks until the simulation is complete, or the timeout is reached.
     *
     * Limitations:
     * - SimSpan offsets and durations are in milliseconds, but with 500ms of granularity
     * we already get a 1% error in the timing of the replayed spans
     * - Spans can happen at the same time. This is scheduling each span using a thread pool of 5000 threads, which
     * size can be set with the env var MAX_THREAD_POOL_SIZE_ENV_VAR. A better implementation would use coroutines
     * - The simulation starts 1 second after this command is called, to avoid failing schedules due to
     * the time it takes to compute the span trees
     *
     * @param path Path to a JSON lines (https://jsonlines.org/) formatted file with
     * JSON objects serializing SimSpan values
     * @param timeout This command blocks for timeout time waiting for the simulation to completes,
     * and aborts in case it did not complete on time
     * @return The list of actual span ids generated by OTEL, or an error instead. Errors can happen:
     * - If some line cannot be parsed
     * - If two spans have the same id
     * - If there is more than 1 root span in any trace
     * - If the simulation takes longer than the specified timeout
     * - If a span runs out of time, either to start or to complete
     * */
    fun playSim(path: Path, timeout: Duration): Result<List<SpanId>>{
        return playSim(listOf(path), timeout)
    }

    /** Like `playSim(path: Path, timeout: Duration)` but for a list of files, and
     * adding a suffix to each trace id as discussed on `parseSimFiles`
     */
    fun playSim(paths: Iterable<Path>, timeout: Duration): Result<List<SpanId>> {
        return parseSimFiles(paths, true).flatMap { spans: List<SimSpan> ->
            playSim(spans, timeout)
        }
    }

    /** Parses a sequence of paths for sim files, returing an error if there is any parsing failure.
     *
     * If `addIdxToTraceIdSuffix` it's then the trace id for all the spans is modified to
     * add a suffix depending of the index of the corresponding path in `paths`. This allows using
     * simulation files with the same trace ids, so each trace can still be reconstructed separately.
     * Note those "logical" trace ids are irrelevant, as during the replace those ids are replaced
     * by trace ids generated by the OTEL client during the replay.
     */
    private fun parseSimFiles(paths: Iterable<Path>, addIdxToTraceIdSuffix: Boolean=false): Result<List<SimSpan>> {
        val spanResults = paths.mapIndexed { idx, path ->
            val suffix = if (addIdxToTraceIdSuffix) "-$idx" else ""
            val lines = path.toFile().readLines()
            lines.map{
                val spanResult = SimSpan.fromJsonStr(it)
                spanResult.map{ simSpan: SimSpan ->
                    val spanId = simSpan.spanId.copy(traceId = "${simSpan.spanId.traceId}$suffix")
                    simSpan.copy(spanId = spanId)
                }
            }
        }.flatten()
        logger.debug("${spanResults.size} spans parsed with success")
        val parseFailures = spanResults.filter{it.isFailure}
        return when {
            parseFailures.isNotEmpty() -> parseFailures.first().map{ emptyList() }
            else -> Result.success(spanResults.successes)
        }
    }

    /** How much should the scheduler wait before creating a span, relative to `replayStartTime`
     * Returns 0 if the time is negative, which introduces an scheduling inaccuracy. This is to support
     * child spans at the start of the parent trace or span
     * */
    private fun spanScheduleDelay(replayStartTime: Instant, span: SimSpan): Result<Duration> = runCatching {
        // discount the time that has passed since replayStartTime
        val delay = span.startTimeOffsetMs - replayStartTime.elapsedTime().toMillis()
        // Allow delayed schedules, to support child spans just at the start of their parent
        // check(delay > 0){"Schedule delay for span with id ${span.spanId} is negative: too late to schedule"}
        if (delay < 0) {
            check(delay.absoluteValue <= MAX_SCHEDULE_DELAY_LATENESS.toMillis()){
                "Schedule delay $delay for span with id ${span.spanId} is late more than ${MAX_SCHEDULE_DELAY_LATENESS.toMillis()} ms: too late to schedule"
            }
            logger.warn("Late schedule of span with id ${span.spanId}: using delay of 0 instead of expected $delay")
        }
        Duration.ofMillis(max(delay, 0L))
    }

    /**
     * How much time is remaining for this span
     * */
    private fun remainingSpanTime(span: SimSpan, spanStartTime: Instant): Result<Duration> = runCatching {
        // discount the time that has passed since spanStartTime
        val duration = span.durationMs - spanStartTime.elapsedTime().toMillis()
        check(duration > 0){
            "Remaining span duration for span with id ${span.spanId} is negative: not enough time to run the span"
        }
        Duration.ofMillis(duration)
    }

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
            val traceId = spanTree.root.spanId.traceId
            val replayStartTime = Instant.now()
            logger.info("Start scheduling of trace with id $traceId at start time $replayStartTime")

            /**
             * Schedules the root span and adds the corresponding future to futures
             * @throws IllegalStateException when this tries to schedule the root trace too late
             * */
            fun scheduleSpanReplay(context: Context, spanTree: SimSpanTree, spanComplete: CountDownLatch?=null) {
                val span = spanTree.root
                spanScheduleDelay(replayStartTime, span).fold({spanDelay ->
                    logger.info("Scheduling $span with delay $spanDelay")
                    scheduler.schedule({
                        val spanStartTime = Instant.now()
                        val emittedSpan = span.start(tracer, context)
                        val emittedSpanCtx = emittedSpan.spanContext
                        val otelSpanId = SpanId(traceId = emittedSpanCtx.traceId, spanId = emittedSpanCtx.spanId)
                        logger.info("Started span with id ${span.spanId} emitted with OTEL id $otelSpanId")
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
                                completeSpan{ logger.info("Completed span with id ${span.spanId} with success") }
                            }, remainingTime.toMillis(), TimeUnit.MILLISECONDS)
                        }, { exception ->
                            spanErrors.add(exception)
                            completeSpan{ logger.error("Timeout span with id ${span.spanId}", exception) }
                        })
                    }, spanDelay.toMillis(), TimeUnit.MILLISECONDS)
                }, { exception ->
                    spanErrors.add(exception)
                    logger.error("Failure scheduling span tree $spanTree", exception)
                    spanComplete?.countDown()
                    spanTree.list.forEach{ _ -> allSpansComplete.countDown()}
                })
            }

            // https://javadoc.io/doc/io.opentelemetry/opentelemetry-context/1.1.0/io/opentelemetry/context/Context.html
            // Use a fresh root context for this trace
            val rootContext = Context.root()
            scheduleSpanReplay(rootContext, spanTree)
            logger.info("Completed scheduling of trace with id $traceId")
        }

        // Add some padding to the span to cover the delay until we start scheduling
        val schedulePadding = Duration.ofSeconds(1)
        val paddedSpans = spans.map{ it.copy(startTimeOffsetMs = it.startTimeOffsetMs + schedulePadding.toMillis())}
        logger.info("Added schedule padding of $schedulePadding to all spans")

        // Build all span tress
        val spanTrees = paddedSpans.groupBy{ it.spanId.traceId }.values
            .map{
                val spanTree = simSpanTree(it)
                logger.info("Built span tree {}", spanTree)
                spanTree
            }

        // Schedule all spans
        val traceScheduled : MutableList<Future<*>> = mutableListOf()
        val traceReplayErrors = spanTrees.flatMap {
            it.fold({ spanTree ->
                traceScheduled.add(scheduler.submit{replayTrace(spanTree)})
                emptyList()
            },{ exception ->
                listOf(exception)
            })
        }
        if (traceReplayErrors.isNotEmpty()) {
            return Result.failure(SpanErrorsException(traceReplayErrors))
        }
        traceScheduled.forEach{it.get(timeout.toMillis()/traceScheduled.size, TimeUnit.MILLISECONDS)}

        // Wait for all spans to complete
        runCatching {
            allSpansComplete.await(timeout.toMillis(), TimeUnit.MILLISECONDS)
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

/** @return How much time has passed since this, at the time of the call.
 * Resolution is limited to milliseconds
 * */
fun Instant.elapsedTime(): Duration =
    Duration.ofMillis(Instant.now().toEpochMilli() - this.toEpochMilli())

/** @return Filters and unwraps the successful results */
val <T> List<Result<T>>.successes: List<T>
    get() = this.flatMap {
        val value = it.getOrNull()
        when {
            value != null -> listOf(value)
            else -> emptyList()
        }
    }

fun String.listDirectory(): List<Path> {
    return Files.list(Paths.get(this)).toList()
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
    const val SIM_FILE_DIR_PATH_VAR_NAME = "SIM_FILE_DIR_PATH"
    const val REPLAY_TIMEOUT_VAR_NAME = "REPLAY_TIMEOUT"
    val REPLAY_TIMEOUT_DEFAULT_SECS = Duration.ofMinutes(10).toSeconds()
}
object ExitCodes {
    const val MISSING_ARGS = 1
    const val REPLAY_ERROR = 2
}

fun main() {
    val logger = LoggerFactory.getLogger("root")
    val simFileDirPath = System.getenv(EnvVars.SIM_FILE_DIR_PATH_VAR_NAME)
    val simFilePath = System.getenv(EnvVars.SIM_FILE_PATH_VAR_NAME)
    if (simFileDirPath != null && simFilePath != null) {
        logger.warn("Both ${EnvVars.SIM_FILE_DIR_PATH_VAR_NAME} and ${EnvVars.SIM_FILE_PATH_VAR_NAME} are set"
        + ", using ${EnvVars.SIM_FILE_DIR_PATH_VAR_NAME} only")
    }
    if (simFileDirPath == null && simFilePath == null) {
        logger.error("Both env vars ${EnvVars.SIM_FILE_DIR_PATH_VAR_NAME} and ${EnvVars.SIM_FILE_PATH_VAR_NAME} are missing value"
            + " please set one of them and retry. Aborting")
        exitProcess(ExitCodes.MISSING_ARGS)
    }

    val replayTimeout = Duration.ofSeconds(
        System.getenv(EnvVars.REPLAY_TIMEOUT_VAR_NAME)?.toLong()
             ?: EnvVars.REPLAY_TIMEOUT_DEFAULT_SECS)
    logger.info("Using simFilePath=$simFilePath, simFileDirPath=$simFileDirPath, replayTimeout=${replayTimeout.toSeconds()} seconds")

    val simFilePaths = simFileDirPath?.listDirectory() ?: listOf(Path.of(simFilePath)!!)
    val otel = provideOtel()
    val tracer = provideTracer(otel)
    val replayer = SpanSimFilePlayer(tracer)
    replayer.playSim(simFilePaths, replayTimeout)
        .onFailure { exception ->
            logger.error("Failure replaying file $simFilePath", exception)
            exitProcess(ExitCodes.REPLAY_ERROR)
        }
        .onSuccess { spanIds ->
            logger.info("Successfully replayed ${spanIds.size} spans")
            spanIds.forEach{ spanId ->
                logger.debug("spanId {}", spanId)
            }
        }
}
