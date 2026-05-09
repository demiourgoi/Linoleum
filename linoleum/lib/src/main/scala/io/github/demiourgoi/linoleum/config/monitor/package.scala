package io.github.demiourgoi.linoleum.config

import java.time.Duration
import java.nio.file.Path

import io.github.demiourgoi.linoleum.maude.{KeyByTraceId, KeyByStringSpanAttribute, MaudeMonitor}
import es.ucm.maude.bindings.{Hook => MaudeHook}
import io.github.demiourgoi.linoleum.messages.LinoleumEvent

// Note: pureconfig throws a stackoverflow at build when there are nested Options. So when needed use zero values (in the Golang sense, e.g. an
// empty string) to encode None values
// Note: pureconfig cannot handle Java Duration, so we use seconds for durations
package object monitor {

  /** YAML-friendly representation of a Maude hook entry. */
  case class HookConfig(
      operatorName: String,
      hookSupplierFqn: String
  )

  /** YAML-friendly representation of MaudeMonitor.StateConfig.
    *
    * Differs from the runtime type in that `shouldIgnoreWindow` is a fully
    * qualified name of a static method instead of a function value.
    */
  case class StateConfigConfig(
      ttlSeconds: Long,
      shouldIgnoreWindowFqn: String = ""
  )

  /** YAML-friendly representation of KeyByCriteria for PureConfig deserialization. */
  sealed trait KeyByCriteriaConfig
  object KeyByCriteriaConfig {
    case object TraceId extends KeyByCriteriaConfig
    case class ByStringAttribute(key: String) extends KeyByCriteriaConfig
  }

  /** YAML-friendly representation of MaudeMonitor.EvaluationConfig. */
  case class EvaluationConfigConfig(
      messageRewriteBound: Int = 100,
      sessionGapSeconds: Long,
      allowedLatenessSeconds: Long = 0
  )

  object MaudeMonitorConfig {
    import pureconfig._
    import pureconfig.generic.auto._
    import pureconfig.module.yaml._

    def fromPath(path: Path): MaudeMonitor = {
      val config = YamlConfigSource
        .file(path)
        .load[MaudeMonitorConfig]
        .fold(
          errors =>
            throw new RuntimeException(
              s"Failed to parse MaudeMonitor YAML from $path: ${errors.prettyPrint()}"
            ),
          config => config
        )
      config.toMaudeMonitor
    }
  }

  /** YAML-friendly representation of MaudeMonitor for PureConfig
    * deserialization.
    *
    * All fields are primitive/collection types so `pureconfig.generic.auto._`
    * can derive readers automatically. Functions (hooks, shouldIgnoreWindow)
    * are represented as FQN strings and resolved at runtime via reflection.
    */
  case class MaudeMonitorConfig(
      name: String,
      program: String,
      module: String,
      monitorOid: String,
      initialSoup: String,
      property: String,
      keyBy: KeyByCriteriaConfig = KeyByCriteriaConfig.TraceId,
      dependencyPrograms: List[String] = List.empty,
      dependencyStdlibPrograms: List[String] = List.empty,
      eqHooks: List[HookConfig] = List.empty,
      rlHooks: List[HookConfig] = List.empty,
      stateConfig: Option[StateConfigConfig] = None,
      config: EvaluationConfigConfig
  ) {

    private def resolveHookSupplier(fqn: String): () => MaudeHook = {
      val lastDot = fqn.lastIndexOf('.')
      if (lastDot < 0)
        throw new IllegalArgumentException(
          s"Invalid FQN for hook supplier: '$fqn'. Expected format: com.example.Class.method"
        )
      val className = fqn.substring(0, lastDot)
      val methodName = fqn.substring(lastDot + 1)
      val clazz = Class.forName(className)
      val method = clazz.getMethod(methodName)
      () => method.invoke(null).asInstanceOf[MaudeHook]
    }

    private def resolveShouldIgnoreWindow(
        fqn: String
    ): (String, List[LinoleumEvent]) => Boolean = {
      val lastDot = fqn.lastIndexOf('.')
      if (lastDot < 0)
        throw new IllegalArgumentException(
          s"Invalid FQN for shouldIgnoreWindow: '$fqn'. Expected format: com.example.Class.method"
        )
      val className = fqn.substring(0, lastDot)
      val methodName = fqn.substring(lastDot + 1)
      val clazz = Class.forName(className)
      val method =
        clazz.getMethod(methodName, classOf[String], classOf[List[_]])
      (key: String, events: List[LinoleumEvent]) =>
        method.invoke(null, key, events).asInstanceOf[Boolean]
    }

    def toMaudeMonitor: MaudeMonitor = {
      val resolvedEqHooks: List[(String, () => MaudeHook)] =
        eqHooks.map(hc =>
          (hc.operatorName, resolveHookSupplier(hc.hookSupplierFqn))
        )
      val resolvedRlHooks: List[(String, () => MaudeHook)] =
        rlHooks.map(hc =>
          (hc.operatorName, resolveHookSupplier(hc.hookSupplierFqn))
        )

      MaudeMonitor(
        name = name,
        program = program,
        module = module,
        monitorOid = monitorOid,
        initialSoup = initialSoup,
        property = property,
        keyBy = keyBy match {
          case KeyByCriteriaConfig.TraceId => KeyByTraceId
          case KeyByCriteriaConfig.ByStringAttribute(key) => KeyByStringSpanAttribute(key)
        },
        dependencyPrograms = dependencyPrograms,
        dependencyStdlibPrograms = dependencyStdlibPrograms,
        eqHooks = resolvedEqHooks,
        rlHooks = resolvedRlHooks,
        stateConfig = stateConfig.map { sc =>
          MaudeMonitor.StateConfig(
            ttl = Duration.ofSeconds(sc.ttlSeconds),
            shouldIgnoreWindow =
              if (sc.shouldIgnoreWindowFqn.nonEmpty)
                resolveShouldIgnoreWindow(sc.shouldIgnoreWindowFqn)
              else
                (_, _) => false
          )
        },
        config = MaudeMonitor.EvaluationConfig(
          messageRewriteBound = config.messageRewriteBound,
          sessionGap = Duration.ofSeconds(config.sessionGapSeconds),
          allowedLateness = Duration.ofSeconds(config.allowedLatenessSeconds)
        )
      )
    }
  }
}
