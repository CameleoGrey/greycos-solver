package greycos.solver.spring.boot.autoconfigure;

import greycos.solver.core.config.solver.SolverConfig;

import org.springframework.core.NativeDetector;

final class GreyCOSSolverNativeSupport {

  static void assertNodeSharingSupported(SolverConfig solverConfig, String solverDescription) {
    var scoreDirectorFactoryConfig = solverConfig.getScoreDirectorFactoryConfig();
    if (scoreDirectorFactoryConfig != null
        && Boolean.TRUE.equals(scoreDirectorFactoryConfig.getConstraintStreamAutomaticNodeSharing())
        && NativeDetector.inNativeImage()) {
      throw new UnsupportedOperationException(
          """
          SolverConfig (%s) with constraintProviderClass (%s) has constraintStreamAutomaticNodeSharing (true), but automatic node sharing is unsupported in a Spring native image.
          Maybe disable constraintStreamAutomaticNodeSharing or run on the JVM."""
              .formatted(
                  solverDescription, scoreDirectorFactoryConfig.getConstraintProviderClass()));
    }
  }

  private GreyCOSSolverNativeSupport() {}
}
