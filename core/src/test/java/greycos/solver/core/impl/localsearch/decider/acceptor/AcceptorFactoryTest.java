package greycos.solver.core.impl.localsearch.decider.acceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.stepcountinghillclimbing.StepCountingHillClimbingType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.localsearch.decider.acceptor.greatdeluge.GreatDelugeAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.hillclimbing.HillClimbingAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.lateacceptance.DiversifiedLateAcceptanceAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.lateacceptance.LateAcceptanceAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.simulatedannealing.SimulatedAnnealingAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.stepcountinghillclimbing.StepCountingHillClimbingAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.tabu.EntityTabuAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.tabu.MoveTabuAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.tabu.ValueTabuAcceptor;
import greycos.solver.core.impl.score.definition.HardSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.ScoreDefinition;

import org.junit.jupiter.api.Test;

class AcceptorFactoryTest {

  @Test
  <Solution_> void buildCompositeAcceptor() {
    LocalSearchAcceptorConfig localSearchAcceptorConfig =
        new LocalSearchAcceptorConfig()
            .withAcceptorTypeList(Arrays.asList(AcceptorType.values()))
            .withEntityTabuSize(1)
            .withFadingEntityTabuSize(1)
            .withMoveTabuSize(1)
            .withFadingMoveTabuSize(1)
            .withValueTabuSize(1)
            .withFadingValueTabuSize(1)
            .withLateAcceptanceSize(10)
            .withSimulatedAnnealingStartingTemperature("-10hard/-10soft")
            .withStepCountingHillClimbingSize(1)
            .withStepCountingHillClimbingType(StepCountingHillClimbingType.IMPROVING_STEP);

    HeuristicConfigPolicy<Solution_> heuristicConfigPolicy = mock(HeuristicConfigPolicy.class);
    ScoreDefinition<HardSoftScore> scoreDefinition = new HardSoftScoreDefinition();
    when(heuristicConfigPolicy.getEnvironmentMode())
        .thenReturn(EnvironmentMode.NON_INTRUSIVE_FULL_ASSERT);
    when(heuristicConfigPolicy.getScoreDefinition()).thenReturn(scoreDefinition);

    AcceptorFactory<Solution_> acceptorFactory = AcceptorFactory.create(localSearchAcceptorConfig);
    Acceptor<Solution_> acceptor = acceptorFactory.buildAcceptor(heuristicConfigPolicy);
    assertThat(acceptor).isExactlyInstanceOf(CompositeAcceptor.class);
    CompositeAcceptor<Solution_> compositeAcceptor = (CompositeAcceptor<Solution_>) acceptor;
    assertThat(compositeAcceptor.acceptorList)
        .map(a -> (Class) a.getClass())
        .containsExactly(
            HillClimbingAcceptor.class,
            StepCountingHillClimbingAcceptor.class,
            EntityTabuAcceptor.class,
            ValueTabuAcceptor.class,
            MoveTabuAcceptor.class,
            SimulatedAnnealingAcceptor.class,
            LateAcceptanceAcceptor.class,
            DiversifiedLateAcceptanceAcceptor.class,
            GreatDelugeAcceptor.class);
  }

  @Test
  <Solution_> void noAcceptorConfigured_throwsException() {
    AcceptorFactory<Solution_> acceptorFactory =
        AcceptorFactory.create(new LocalSearchAcceptorConfig());
    assertThatIllegalArgumentException()
        .isThrownBy(() -> acceptorFactory.buildAcceptor(mock(HeuristicConfigPolicy.class)))
        .withMessageContaining("The acceptor does not specify any acceptorType");
  }

  @Test
  <Solution_> void lateAcceptanceAcceptor() {
    var localSearchAcceptorConfig =
        new LocalSearchAcceptorConfig().withAcceptorTypeList(List.of(AcceptorType.LATE_ACCEPTANCE));
    HeuristicConfigPolicy<Solution_> heuristicConfigPolicy = mock(HeuristicConfigPolicy.class);
    AcceptorFactory<Solution_> acceptorFactory = AcceptorFactory.create(localSearchAcceptorConfig);
    var acceptor = acceptorFactory.buildAcceptor(heuristicConfigPolicy);
    assertThat(acceptor).isExactlyInstanceOf(LateAcceptanceAcceptor.class);

    localSearchAcceptorConfig = new LocalSearchAcceptorConfig().withLateAcceptanceSize(10);
    acceptorFactory = AcceptorFactory.create(localSearchAcceptorConfig);
    acceptor = acceptorFactory.buildAcceptor(heuristicConfigPolicy);
    assertThat(acceptor).isExactlyInstanceOf(LateAcceptanceAcceptor.class);
  }

  @Test
  <Solution_> void diversifiedLateAcceptanceAcceptor() {
    var localSearchAcceptorConfig =
        new LocalSearchAcceptorConfig()
            .withAcceptorTypeList(List.of(AcceptorType.DIVERSIFIED_LATE_ACCEPTANCE));
    HeuristicConfigPolicy<Solution_> heuristicConfigPolicy = mock(HeuristicConfigPolicy.class);
    AcceptorFactory<Solution_> acceptorFactory = AcceptorFactory.create(localSearchAcceptorConfig);
    var acceptor = acceptorFactory.buildAcceptor(heuristicConfigPolicy);
    assertThat(acceptor).isExactlyInstanceOf(DiversifiedLateAcceptanceAcceptor.class);

    localSearchAcceptorConfig =
        new LocalSearchAcceptorConfig()
            .withAcceptorTypeList(List.of(AcceptorType.DIVERSIFIED_LATE_ACCEPTANCE))
            .withLateAcceptanceSize(10);
    acceptorFactory = AcceptorFactory.create(localSearchAcceptorConfig);
    acceptor = acceptorFactory.buildAcceptor(heuristicConfigPolicy);
    assertThat(acceptor).isExactlyInstanceOf(DiversifiedLateAcceptanceAcceptor.class);

    doThrow(new IllegalStateException()).when(heuristicConfigPolicy).ensurePreviewFeature(any());
    localSearchAcceptorConfig =
        new LocalSearchAcceptorConfig()
            .withAcceptorTypeList(List.of(AcceptorType.DIVERSIFIED_LATE_ACCEPTANCE))
            .withLateAcceptanceSize(10);
    AcceptorFactory<Solution_> badAcceptorFactory =
        AcceptorFactory.create(localSearchAcceptorConfig);
    assertThatIllegalStateException()
        .isThrownBy(() -> badAcceptorFactory.buildAcceptor(heuristicConfigPolicy));
  }

  @Test
  <Solution_> void valueTabuWithoutSizes_throwsException() {
    var config =
        new LocalSearchAcceptorConfig().withAcceptorTypeList(List.of(AcceptorType.VALUE_TABU));
    var factory = AcceptorFactory.create(config);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> factory.buildAcceptor(mock(HeuristicConfigPolicy.class)));
  }

  @Test
  <Solution_> void moveTabuWithoutSizes_throwsException() {
    var config =
        new LocalSearchAcceptorConfig().withAcceptorTypeList(List.of(AcceptorType.MOVE_TABU));
    var factory = AcceptorFactory.create(config);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> factory.buildAcceptor(mock(HeuristicConfigPolicy.class)));
  }
}
