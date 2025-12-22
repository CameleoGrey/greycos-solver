package ai.greycos.solver.quarkus.deployment.config;

import java.util.Optional;
import java.util.Set;

import ai.greycos.solver.core.api.domain.common.DomainAccessType;
import ai.greycos.solver.core.api.score.stream.ConstraintStreamImplType;
import ai.greycos.solver.core.config.solver.PreviewFeature;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.quarkus.config.SolverRuntimeConfig;

import io.quarkus.runtime.annotations.ConfigGroup;

/**
 * During build time, this is translated into Greycos's {@link SolverConfig} (except for termination
 * properties which are translated at bootstrap time).
 *
 * @see SolverRuntimeConfig
 */
@ConfigGroup
public interface SolverBuildTimeConfig {

  /**
   * A classpath resource to read the specific solver configuration XML. If this property isn't
   * specified, that solverConfig.xml is optional.
   */
  // Build time - classes in the SolverConfig are visited by SolverConfig.visitReferencedClasses
  // which generates the constructor of classes used by Quarkus
  Optional<String> solverConfigXml();

  /**
   * Determines how to access the fields and methods of domain classes. Defaults to {@link
   * DomainAccessType#GIZMO}.
   */
  // Build time - GIZMO classes are only generated if at least one solver
  // has domain access type GIZMO
  Optional<DomainAccessType> domainAccessType();

  /** Enable the Nearby Selection quick configuration. */
  // Build time - visited by SolverConfig.visitReferencedClasses
  // which generates the constructor used by Quarkus
  Optional<Class<?>> nearbyDistanceMeterClass();

  /**
   * What preview features to enable. The list of available preview features should not be
   * considered stable and may change between releases.
   */
  Optional<Set<PreviewFeature>> enabledPreviewFeatures();

  /**
   * What constraint stream implementation to use. Defaults to {@link
   * ConstraintStreamImplType#BAVET}.
   *
   * @deprecated Not used anymore.
   */
  @Deprecated(forRemoval = true, since = "1.4.0")
  Optional<ConstraintStreamImplType> constraintStreamImplType();

  /**
   * Note: this setting is only available for <a
   * href="https://greycos.ai/docs/greycos-solver/latest/enterprise-edition/enterprise-edition">Greycos
   * Solver Enterprise Edition</a>. Enable rewriting the {@link
   * ai.greycos.solver.core.api.score.stream.ConstraintProvider} class so nodes share lambdas when
   * possible, improving performance. When enabled, breakpoints placed in the {@link
   * ai.greycos.solver.core.api.score.stream.ConstraintProvider} will no longer be triggered.
   * Defaults to "false".
   */
  // Build time - modifies the ConstraintProvider class if set
  Optional<Boolean> constraintStreamAutomaticNodeSharing();
}
