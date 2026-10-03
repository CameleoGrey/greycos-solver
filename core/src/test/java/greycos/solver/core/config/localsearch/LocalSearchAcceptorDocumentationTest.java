package greycos.solver.core.config.localsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.solver.PreviewFeature;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.localsearch.decider.LocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.acceptor.lateacceptance.DiversifiedLateAcceptanceAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.simulatedannealing.SimulatedAnnealingAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.tabu.ValueTabuAcceptor;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class LocalSearchAcceptorDocumentationTest {

  @Test
  void diversifiedLateAcceptanceAdvancedExampleSelectsPreviewAcceptor() {
    var config =
        readAndRoundTrip(
            """
            <solver xmlns="%s">
              <enablePreviewFeature>DIVERSIFIED_LATE_ACCEPTANCE</enablePreviewFeature>
              <localSearch>
                <acceptor>
                  <acceptorType>DIVERSIFIED_LATE_ACCEPTANCE</acceptorType>
                  <lateAcceptanceSize>5</lateAcceptanceSize>
                </acceptor>
                <forager>
                  <acceptedCountLimit>1</acceptedCountLimit>
                </forager>
              </localSearch>
            </solver>
            """
                .formatted(SolverConfig.XML_NAMESPACE));
    assertThat(config.getEnablePreviewFeatureSet())
        .containsExactly(PreviewFeature.DIVERSIFIED_LATE_ACCEPTANCE);
    var phaseConfig = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(phaseConfig.getAcceptorConfig().getAcceptorTypeList())
        .containsExactly(AcceptorType.DIVERSIFIED_LATE_ACCEPTANCE);
    assertThat(phaseConfig.getAcceptorConfig().getLateAcceptanceSize()).isEqualTo(5);
    assertThat(decider(buildSolver(config)).getAcceptor())
        .isExactlyInstanceOf(DiversifiedLateAcceptanceAcceptor.class);
  }

  @Test
  void valueTabuExampleUsesSupportedFixedSize() {
    var config =
        readAndRoundTrip(
            """
            <solver xmlns="%s">
              <localSearch>
                <acceptor>
                  <valueTabuSize>7</valueTabuSize>
                </acceptor>
                <forager>
                  <acceptedCountLimit>1000</acceptedCountLimit>
                </forager>
              </localSearch>
            </solver>
            """
                .formatted(SolverConfig.XML_NAMESPACE));
    var phaseConfig = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(phaseConfig.getAcceptorConfig().getValueTabuSize()).isEqualTo(7);
    assertThat(decider(buildSolver(config)).getAcceptor())
        .isExactlyInstanceOf(ValueTabuAcceptor.class);
  }

  @Test
  void diminishedReturnsWithStepLimitSuppliesSimulatedAnnealingCooling() {
    // The documented cooling combination, with a short work limit for this executable example.
    var config =
        readAndRoundTrip(
            """
            <solver xmlns="%s">
              <localSearch>
                <termination>
                  <terminationCompositionStyle>OR</terminationCompositionStyle>
                  <diminishedReturns />
                  <stepCountLimit>3</stepCountLimit>
                </termination>
                <acceptor>
                  <simulatedAnnealingStartingTemperature>10</simulatedAnnealingStartingTemperature>
                </acceptor>
                <forager>
                  <acceptedCountLimit>1</acceptedCountLimit>
                </forager>
              </localSearch>
            </solver>
            """
                .formatted(SolverConfig.XML_NAMESPACE));
    var phaseConfig = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(phaseConfig.getTerminationConfig().getDiminishedReturnsConfig()).isNotNull();
    var solver = buildSolver(config);
    assertThat(decider(solver).getAcceptor()).isExactlyInstanceOf(SimulatedAnnealingAcceptor.class);
    List<Double> gradients = new ArrayList<>();
    solver
        .getPhaseList()
        .getFirst()
        .addPhaseLifecycleListener(
            new PhaseLifecycleListenerAdapter<>() {
              @Override
              public void stepEnded(AbstractStepScope<TestdataSolution> stepScope) {
                gradients.add(
                    ((LocalSearchStepScope<TestdataSolution>) stepScope).getTimeGradient());
              }
            });

    var solution = solver.solve(TestdataSolution.generateSolution(3, 6));

    assertThat(gradients).containsExactly(0.0, 1.0 / 3.0, 2.0 / 3.0);
    assertThat(solution.getScore())
        .isEqualTo(new TestdataEasyScoreCalculator().calculateScore(solution));
  }

  private static SolverConfig readAndRoundTrip(String xml) {
    var io = new SolverConfigIO();
    var config = io.read(new StringReader(xml));
    var writer = new StringWriter();
    io.write(config, writer);
    var restored = io.read(new StringReader(writer.toString()));
    assertThat(restored).usingRecursiveComparison().isEqualTo(config);
    return restored;
  }

  private static DefaultSolver<TestdataSolution> buildSolver(SolverConfig config) {
    config
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }

  private static LocalSearchDecider<TestdataSolution> decider(
      DefaultSolver<TestdataSolution> solver) {
    var phase = (DefaultLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    return (LocalSearchDecider<TestdataSolution>) phase.getDecider();
  }
}
