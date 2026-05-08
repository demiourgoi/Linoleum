package io.github.demiourgoi.linoleum.config

import java.time.Duration
import java.nio.file.Path

import io.github.demiourgoi.linoleum.maude.{
  KeyByTraceId,
  MaudeMonitor
}
import es.ucm.maude.bindings.{Hook => MaudeHook}
import io.github.demiourgoi.linoleum.messages.LinoleumEvent

package object monitor {

  /** YAML-friendly representation of a Maude hook entry. */
  case class HookConfig(
      operatorName: String,
      hookSupplierFqn: String
  )

  /** YAML-friendly representation of MaudeMonitor.StateConfig.
    *
    * Differs from the runtime type in that `shouldIgnoreWindow` is a
    * fully qualified name of a static method instead of a function value.
    */
  case class StateConfigConfig(
      // Note: pureconfig cannot handle Java Duration
      ttlSeconds: Long,
      // Note: pureconfig throws a stackoverflow at build when there are nested Option
      // so here "" represents None
      shouldIgnoreWindowFqn: String = ""
  )

  // /** YAML-friendly representation of MaudeMonitor.EvaluationConfig. */
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
      ???
    }
  }

  /** YAML-friendly representation of MaudeMonitor for PureConfig deserialization.
    *
    * All fields are primitive/collection types so `pureconfig.generic.auto._`
    * can derive readers automatically. Functions (hooks, shouldIgnoreWindow)
    * are represented as FQN strings and resolved at runtime via reflection.
    *
    * `keyBy` is NOT parsed from YAML yet; it always defaults to `KeyByTraceId`.
    * TODO: support KeyByCriteriaConfig in YAML.
    */
  case class MaudeMonitorConfig(
      name: String,
      program: String,
      module: String,
      monitorOid: String,
      initialSoup: String,
      property: String,
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
      val method = clazz.getMethod(methodName, classOf[String], classOf[List[_]])
      (key: String, events: List[LinoleumEvent]) =>
        method.invoke(null, key, events).asInstanceOf[Boolean]
    }

    def toMaudeMonitor: MaudeMonitor = {
      val resolvedEqHooks: List[(String, () => MaudeHook)] =
        eqHooks.map(hc => (hc.operatorName, resolveHookSupplier(hc.hookSupplierFqn)))
      val resolvedRlHooks: List[(String, () => MaudeHook)] =
        rlHooks.map(hc => (hc.operatorName, resolveHookSupplier(hc.hookSupplierFqn)))

      MaudeMonitor(
        name = name,
        program = program,
        module = module,
        monitorOid = monitorOid,
        initialSoup = initialSoup,
        property = property,
        // TODO: parse keyBy from YAML; always default to KeyByTraceId for now
        keyBy = KeyByTraceId,
        dependencyPrograms = dependencyPrograms,
        dependencyStdlibPrograms = dependencyStdlibPrograms,
        eqHooks = resolvedEqHooks,
        rlHooks = resolvedRlHooks,
        // stateConfig = stateConfig.map { sc =>
        //   MaudeMonitor.StateConfig(
        //     ttl = sc.ttl,
        //     shouldIgnoreWindow = sc.shouldIgnoreWindowFqn
        //       .map(resolveShouldIgnoreWindow)
        //       .getOrElse((_, _) => false)
        //   )
        // },
        config = MaudeMonitor.EvaluationConfig(
          messageRewriteBound = config.messageRewriteBound,
          sessionGap = Duration.ofSeconds(config.sessionGapSeconds),
          allowedLateness = Duration.ofSeconds(config.allowedLatenessSeconds)
        )
      )
    }
  }

  // object MaudeMonitorConfig {
  //   import pureconfig._
  //   import pureconfig.generic.auto._
  //   import pureconfig.module.yaml._

  //   def fromPath(path: java.nio.file.Path): MaudeMonitor = {
  //     val config = YamlConfigSource
  //       .file(path)
  //       .load[MaudeMonitorConfig]
  //       .fold(
  //         errors => throw new RuntimeException(
  //           s"Failed to parse MaudeMonitor YAML from $path: ${errors.prettyPrint()}"
  //         ),
  //         config => config
  //       )
  //     config.toMaudeMonitor
  //   }
  // }
}