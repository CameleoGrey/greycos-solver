package ai.greycos.solver.core.impl.score.stream.test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.test.ConstraintVerifier;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

import org.jspecify.annotations.NonNull;

public final class DefaultConstraintVerifier<
        ConstraintProvider_ extends ConstraintProvider, Solution_, Score_ extends Score<Score_>>
    implements ConstraintVerifier<ConstraintProvider_, Solution_> {

  private final ConstraintProvider_ constraintProvider;
  private final SolutionDescriptor<Solution_> solutionDescriptor;

  /** The configured verifier is created lazily and reused between verification calls. */
  private final AtomicReference<
          ConfiguredConstraintVerifier<ConstraintProvider_, Solution_, Score_>>
      configuredConstraintVerifierRef = new AtomicReference<>();

  public DefaultConstraintVerifier(
      ConstraintProvider_ constraintProvider, SolutionDescriptor<Solution_> solutionDescriptor) {
    this.constraintProvider = constraintProvider;
    this.solutionDescriptor = solutionDescriptor;
  }

  // ************************************************************************
  // Verify methods
  // ************************************************************************

  @Override
  public @NonNull DefaultSingleConstraintVerification<Solution_, Score_> verifyThat(
      @NonNull BiFunction<ConstraintProvider_, ConstraintFactory, Constraint> constraintFunction) {
    return getOrCreateConfiguredConstraintVerifier().verifyThat(constraintFunction);
  }

  private ConfiguredConstraintVerifier<ConstraintProvider_, Solution_, Score_>
      getOrCreateConfiguredConstraintVerifier() {
    return configuredConstraintVerifierRef.updateAndGet(
        v -> {
          if (v == null) {
            return new ConfiguredConstraintVerifier<>(constraintProvider, solutionDescriptor);
          }
          return v;
        });
  }

  @Override
  public @NonNull DefaultMultiConstraintVerification<Solution_, Score_> verifyThat() {
    return getOrCreateConfiguredConstraintVerifier().verifyThat();
  }
}
