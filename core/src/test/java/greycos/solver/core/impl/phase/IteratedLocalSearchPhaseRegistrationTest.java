package greycos.solver.core.impl.phase;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.iteratedlocalsearch.DefaultIteratedLocalSearchPhaseFactory;

import org.junit.jupiter.api.Test;

class IteratedLocalSearchPhaseRegistrationTest {

  @Test
  void phaseRequiresInitializationAndOwnFiniteTermination() {
    var config =
        new IteratedLocalSearchPhaseConfig()
            .withLocalSearch(
                new LocalSearchPhaseConfig()
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)))
            .withPerturbationStrengths(1)
            .withPerturbationAttemptLimit(4)
            .withEpisodeCandidateAttemptLimit(8);
    assertThat(PhaseFactory.create(config))
        .isInstanceOf(DefaultIteratedLocalSearchPhaseFactory.class);
    assertThat(PhaseFactory.requiresInitializedSolution(config)).isTrue();
    assertThat(PhaseFactory.canTerminate(config)).isFalse();
    assertThat(PhaseFactory.canTerminate(config.copyConfig().withIterationCountLimit(2))).isTrue();
    assertThat(PhaseFactory.canTerminate(config.copyConfig().withIterationCountLimit(0))).isFalse();
    assertThat(
            PhaseFactory.canTerminate(
                config
                    .copyConfig()
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(20))))
        .isTrue();
  }

  @Test
  void bestEventsUseOneOuterPhaseIdentity() {
    var producer = EventProducerId.iteratedLocalSearch(3);
    assertThat(producer.producerId()).isEqualTo("Iterated Local Search (3)");
    assertThat(producer.simpleProducerName()).isEqualTo("Iterated Local Search");
    assertThat(producer.phaseIndex()).hasValue(3);
  }
}
