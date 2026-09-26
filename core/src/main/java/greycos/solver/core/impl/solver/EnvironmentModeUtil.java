package greycos.solver.core.impl.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;

import org.jspecify.annotations.NullMarked;

/**
 * A {@link SolverConfig} declares one environment mode, the global one, and each of its phases may
 * override it with a stricter one. The declared mode stays the solver's own whatever the phases do:
 * it governs everything outside the phases, and the score director factory built for it is needed
 * regardless.
 *
 * <p>{@link #validate(SolverConfig)} rejects a config whose overrides break the rules, and is the
 * only method here which throws; the others only read the config.
 */
@NullMarked
public final class EnvironmentModeUtil {

  /**
   * Fails on a config whose phase-level environment modes break either of the two rules: no phase
   * may be less strict than the global mode, and a non-reproducible global mode admits no
   * phase-level override at all.
   *
   * @throws IllegalStateException when a phase-level override is not allowed
   */
  public static void validate(SolverConfig solverConfig) {
    var globalEnvironmentMode = solverConfig.determineEnvironmentMode();
    var phaseEnvironmentModeList =
        determinePhaseEnvironmentModeList(solverConfig, globalEnvironmentMode, true);
    if (phaseEnvironmentModeList.isEmpty()) {
      return;
    }
    if (globalEnvironmentMode == EnvironmentMode.NON_REPRODUCIBLE
        && phaseEnvironmentModeList.stream()
            .anyMatch(environmentMode -> environmentMode != globalEnvironmentMode)) {
      // A non-reproducible global environment mode cannot be overridden per phase,
      // as a phase-level override would have nothing reproducible to be an override of.
      throw new IllegalStateException(
          "Phase-level environmentMode override is only possible when global environmentMode is reproducible, but was %s."
              .formatted(globalEnvironmentMode.name()));
    }
    // Every phase may override the global mode,
    // including all of them at once.
    // The global mode still applies outside the phases,
    // and the factory built for it is needed.
    var invalidPhaseEnvironmentList = new ArrayList<String>(phaseEnvironmentModeList.size());
    for (var phaseEnvironmentMode : phaseEnvironmentModeList) {
      if (phaseEnvironmentMode.ordinal() > globalEnvironmentMode.ordinal()) {
        invalidPhaseEnvironmentList.add(phaseEnvironmentMode.name());
      }
    }
    validateNestedPhases(solverConfig.getPhaseConfigList(), globalEnvironmentMode);
    if (!invalidPhaseEnvironmentList.isEmpty()) {
      // The phase environments must have an assertion level greater than or equal to the global
      // environment level
      throw new IllegalStateException(
          """
          The phase environments must have an assertion level higher than or equal to the global environment level (%s). \
          The following phase environment modes are not valid: [%s].\
          """
              .formatted(
                  globalEnvironmentMode.name(), String.join(", ", invalidPhaseEnvironmentList)));
    }
  }

  /**
   * @return the solver's own environment mode, which a phase-level override never changes
   */
  public static EnvironmentMode resolve(SolverConfig solverConfig) {
    return solverConfig.determineEnvironmentMode();
  }

  /**
   * The environment modes the phases of this config run in, in the order of {@link
   * SolverConfig#getPhaseConfigList()}.
   *
   * @param useGlobalEnvironmentMode when true, it uses the {@code globalEnvironmentMode} as the
   *     phase environment mode if that phase does not override it. Otherwise, it only returns
   *     phases that override the phase environment mode.
   * @return empty when the config declares no phases, and, when {@code useGlobalMode} is false, and
   *     no phase overrides the solver's environment mode
   */
  public static List<EnvironmentMode> resolvePhases(
      SolverConfig solverConfig, boolean useGlobalEnvironmentMode) {
    return determinePhaseEnvironmentModeList(
        solverConfig, resolve(solverConfig), useGlobalEnvironmentMode);
  }

  private static List<EnvironmentMode> determinePhaseEnvironmentModeList(
      SolverConfig solverConfig,
      EnvironmentMode globalEnvironmentMode,
      boolean useGlobalEnvironmentMode) {
    var modes = new ArrayList<EnvironmentMode>();
    collectPhaseModes(
        solverConfig.getPhaseConfigList(), globalEnvironmentMode, useGlobalEnvironmentMode, modes);
    return List.copyOf(modes);
  }

  private static void collectPhaseModes(
      List<? extends PhaseConfig> phases,
      EnvironmentMode inherited,
      boolean includeInherited,
      List<EnvironmentMode> modes) {
    if (phases == null) {
      return;
    }
    for (var phase : phases) {
      var effective = Objects.requireNonNullElse(phase.getEnvironmentMode(), inherited);
      if (includeInherited || phase.getEnvironmentMode() != null) {
        modes.add(effective);
      }
      collectPhaseModes(childPhases(phase), effective, includeInherited, modes);
    }
  }

  private static void validateNestedPhases(
      List<? extends PhaseConfig> phases, EnvironmentMode inherited) {
    if (phases == null) {
      return;
    }
    for (var phase : phases) {
      var effective = Objects.requireNonNullElse(phase.getEnvironmentMode(), inherited);
      var children = childPhases(phase);
      if (children != null) {
        for (var child : children) {
          if (child.getEnvironmentMode() != null
              && child.getEnvironmentMode().ordinal() > effective.ordinal()) {
            throw new IllegalStateException(
                "Nested phase environmentMode (%s) must have an assertion level higher than or equal to the enclosing phase environment level (%s)."
                    .formatted(child.getEnvironmentMode(), effective));
          }
        }
        validateNestedPhases(children, effective);
      }
    }
  }

  private static List<? extends PhaseConfig> childPhases(PhaseConfig<?> phase) {
    if (phase instanceof IslandModelPhaseConfig island) {
      return island.getPhaseConfigList();
    }
    if (phase instanceof PartitionedSearchPhaseConfig partition) {
      return partition.getPhaseConfigList();
    }
    return Collections.emptyList();
  }

  private EnvironmentModeUtil() {
    // No external instances.
  }
}
