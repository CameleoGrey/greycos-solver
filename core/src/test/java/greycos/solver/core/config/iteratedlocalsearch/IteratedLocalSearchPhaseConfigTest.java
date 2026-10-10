package greycos.solver.core.config.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.io.jaxb.GreyCOSXmlSerializationException;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;

class IteratedLocalSearchPhaseConfigTest {

  @Test
  void defaultsRemainUnspecified() {
    var config = new IteratedLocalSearchPhaseConfig();
    assertThat(config.getLocalSearchConfig()).isNull();
    assertThat(config.getPerturbationMoveSelectorConfig()).isNull();
    assertThat(config.getPerturbationStrengths()).isNull();
    assertThat(config.getPerturbationAttemptLimit()).isNull();
    assertThat(config.getEpisodeCandidateAttemptLimit()).isNull();
    assertThat(config.getIterationCountLimit()).isNull();
    assertThat(config.getAcceptanceType()).isNull();
    assertThat(config.getMoveThreadCount()).isNull();
    assertThat(config.getTerminationConfig()).isNull();
    var visited = new ArrayList<Class<?>>();
    config.visitReferencedClasses(visited::add);
    assertThat(visited).isEmpty();
  }

  @Test
  void xmlRoundTripIncludesTopLevelIslandAndPartition() {
    var phase = configured();
    var solver =
        new SolverConfig()
            .withPhases(
                phase,
                new IslandModelPhaseConfig().withPhaseConfigList(List.of(phase.copyConfig())),
                new PartitionedSearchPhaseConfig().withPhaseConfigs(phase.copyConfig()));
    var writer = new StringWriter();
    var io = new SolverConfigIO();
    io.write(solver, writer);
    assertThat(writer.toString())
        .contains(
            "<iteratedLocalSearch>",
            "<perturbation>",
            "<unionMoveSelector>",
            "<perturbationStrengths>1 2 4 8</perturbationStrengths>");
    assertThat(io.read(new StringReader(writer.toString())))
        .usingRecursiveComparison()
        .isEqualTo(solver);
  }

  @Test
  void namespacedXmlValidatesAgainstSchema() {
    var config = new SolverConfigIO().read(new StringReader(xml("IMPROVING_ONLY")));
    var phase = (IteratedLocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(phase.getLocalSearchConfig().getLocalSearchType())
        .isEqualTo(LocalSearchType.LATE_ACCEPTANCE);
    assertThat(phase.getLocalSearchConfig().getTerminationConfig().getStepCountLimit())
        .isEqualTo(500);
    assertThat(phase.getTerminationConfig().getSpentLimit()).isEqualTo(Duration.ofSeconds(60));
    assertThat(phase.getPerturbationStrengths()).containsExactly(1, 2, 4, 8);
    assertThat(phase.getEpisodeCandidateAttemptLimit()).isEqualTo(10_000L);
    assertThat(phase.getAcceptanceType())
        .isEqualTo(IteratedLocalSearchAcceptanceType.IMPROVING_ONLY);
  }

  @Test
  void schemaRejectsUnsupportedAcceptanceAndMultiplePerturbationSelectors() {
    assertThatThrownBy(
            () -> new SolverConfigIO().read(new StringReader(xml("SIMULATED_ANNEALING"))))
        .isInstanceOf(GreyCOSXmlSerializationException.class);
    var duplicate =
        xml("IMPROVING_ONLY")
            .replace("</unionMoveSelector>", "</unionMoveSelector><swapMoveSelector/>");
    assertThatThrownBy(() -> new SolverConfigIO().read(new StringReader(duplicate)))
        .isInstanceOf(GreyCOSXmlSerializationException.class);
  }

  @Test
  void copiesOwnNestedSelectorsAndBudgets() {
    var original = configured();
    var copy = original.copyConfig();
    original.getPerturbationStrengths().set(0, 7);
    original.getLocalSearchConfig().getMoveSelectorConfig().setSelectedCountLimit(999L);
    original.getLocalSearchConfig().getTerminationConfig().setStepCountLimit(999);
    original.getTerminationConfig().setSpentLimit(Duration.ofDays(1));
    var originalUnion = (UnionMoveSelectorConfig) original.getPerturbationMoveSelectorConfig();
    originalUnion.getMoveSelectorList().getFirst().setSelectedCountLimit(999L);
    assertThat(copy.getPerturbationStrengths()).containsExactly(1, 2, 4, 8);
    assertThat(copy.getLocalSearchConfig().getMoveSelectorConfig().getSelectedCountLimit())
        .isNull();
    assertThat(copy.getLocalSearchConfig().getTerminationConfig().getStepCountLimit())
        .isEqualTo(500);
    assertThat(copy.getTerminationConfig().getSpentLimit()).isEqualTo(Duration.ofSeconds(60));
    assertThat(
            ((UnionMoveSelectorConfig) copy.getPerturbationMoveSelectorConfig())
                .getMoveSelectorList()
                .getFirst()
                .getSelectedCountLimit())
        .isNull();
  }

  @Test
  void inheritancePreservesOverridesWithoutConcatenatingStrengthSchedules() {
    var parent = configured();
    var child =
        new IteratedLocalSearchPhaseConfig()
            .withPerturbationStrengths(3, 6)
            .withLocalSearch(
                new LocalSearchPhaseConfig().withLocalSearchType(LocalSearchType.HILL_CLIMBING))
            .withIterationCountLimit(12L)
            .inherit(parent);
    assertThat(child.getPerturbationStrengths()).containsExactly(3, 6);
    assertThat(child.getLocalSearchConfig().getLocalSearchType())
        .isEqualTo(LocalSearchType.HILL_CLIMBING);
    assertThat(child.getEpisodeCandidateAttemptLimit()).isEqualTo(10_000L);
    assertThat(child.getIterationCountLimit()).isEqualTo(12L);
    child.getLocalSearchConfig().getMoveSelectorConfig().setSelectedCountLimit(5L);
    assertThat(parent.getLocalSearchConfig().getMoveSelectorConfig().getSelectedCountLimit())
        .isNull();
  }

  @Test
  void visitsBothMovePortfolios() {
    var config =
        configured()
            .withLocalSearch(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(
                        new ChangeMoveSelectorConfig().withFilterClass(InnerFilter.class)))
            .withPerturbationMoveSelectorConfig(
                new SwapMoveSelectorConfig().withFilterClass(PerturbationFilter.class));
    var visited = new ArrayList<Class<?>>();
    config.visitReferencedClasses(visited::add);
    assertThat(visited).contains(InnerFilter.class, PerturbationFilter.class);
  }

  private static IteratedLocalSearchPhaseConfig configured() {
    return new IteratedLocalSearchPhaseConfig()
        .withMoveThreadCount("4")
        .withLocalSearch(
            new LocalSearchPhaseConfig()
                .withLocalSearchType(LocalSearchType.LATE_ACCEPTANCE)
                .withMoveSelectorConfig(new ChangeMoveSelectorConfig())
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(500)))
        .withPerturbationMoveSelectorConfig(
            new UnionMoveSelectorConfig()
                .withMoveSelectors(new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig()))
        .withPerturbationStrengths(1, 2, 4, 8)
        .withPerturbationAttemptLimit(256)
        .withEpisodeCandidateAttemptLimit(10_000L)
        .withAcceptanceType(IteratedLocalSearchAcceptanceType.IMPROVING_ONLY)
        .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(60)));
  }

  private static String xml(String acceptance) {
    return """
        <solver xmlns="%s">
          <iteratedLocalSearch>
            <termination><spentLimit>PT60S</spentLimit></termination>
            <moveThreadCount>4</moveThreadCount>
            <localSearch>
              <termination><stepCountLimit>500</stepCountLimit></termination>
              <localSearchType>LATE_ACCEPTANCE</localSearchType>
            </localSearch>
            <perturbation><unionMoveSelector><changeMoveSelector/><swapMoveSelector/></unionMoveSelector></perturbation>
            <perturbationStrengths>1 2 4 8</perturbationStrengths>
            <perturbationAttemptLimit>256</perturbationAttemptLimit>
            <episodeCandidateAttemptLimit>10000</episodeCandidateAttemptLimit>
            <acceptanceType>%s</acceptanceType>
          </iteratedLocalSearch>
        </solver>
        """
        .formatted(SolverConfig.XML_NAMESPACE, acceptance);
  }

  public abstract static class InnerFilter implements SelectionFilter<Object, Object> {}

  public abstract static class PerturbationFilter implements SelectionFilter<Object, Object> {}
}
