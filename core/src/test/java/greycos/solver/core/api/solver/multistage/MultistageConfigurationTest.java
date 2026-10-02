package greycos.solver.core.api.solver.multistage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;

class MultistageConfigurationTest {

  @Test
  void bothSelectorKindsRoundTripStandaloneInUnionsAndInIslands() {
    var basic =
        new MultistageMoveSelectorConfig()
            .withStageProviderClass(BasicProvider.class)
            .withEntityClass(String.class)
            .withVariableName("value")
            .withCandidateCountLimit(11)
            .withProbeCountLimit(22);
    var list =
        new ListMultistageMoveSelectorConfig()
            .withStageProviderClass(ListProvider.class)
            .withEntityClass(String.class)
            .withVariableName("values")
            .withCandidateCountLimit(33)
            .withProbeCountLimit(44);
    var io = new SolverConfigIO();
    for (var selector : List.<MoveSelectorConfig<?>>of(basic, list)) {
      var config =
          new SolverConfig()
              .withPhases(
                  new LocalSearchPhaseConfig().withMoveSelectorConfig(selector.copyConfig()),
                  new LocalSearchPhaseConfig()
                      .withMoveSelectorConfig(
                          new UnionMoveSelectorConfig()
                              .withMoveSelectors(
                                  new UnionMoveSelectorConfig()
                                      .withMoveSelectors(selector.copyConfig()))),
                  new IslandModelPhaseConfig()
                      .withIslandCount(2)
                      .withMoveSelectorConfig(selector.copyConfig()));
      var writer = new StringWriter();
      io.write(config, writer);
      var restored = io.read(new StringReader(writer.toString()));
      assertThat(restored).usingRecursiveComparison().isEqualTo(config);
    }
  }

  @Test
  void defaultsAndInvalidBudgetsAreExplicitForBothVariableKinds() {
    var basic = new MultistageMoveSelectorConfig();
    var list = new ListMultistageMoveSelectorConfig();
    assertThat(basic.determineCandidateCountLimit()).isEqualTo(64);
    assertThat(list.determineCandidateCountLimit()).isEqualTo(64);
    assertThat(basic.determineProbeCountLimit()).isEqualTo(10_000);
    assertThat(list.determineProbeCountLimit()).isEqualTo(10_000);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> basic.withCandidateCountLimit(0).determineCandidateCountLimit())
        .withMessageContaining("candidateCountLimit (0)");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> list.withCandidateCountLimit(-1).determineCandidateCountLimit())
        .withMessageContaining("candidateCountLimit (-1)");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> basic.withProbeCountLimit(-2).determineProbeCountLimit())
        .withMessageContaining("probeCountLimit (-2)");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> list.withProbeCountLimit(0).determineProbeCountLimit())
        .withMessageContaining("probeCountLimit (0)");
  }

  @Test
  void basicCopyAndInheritancePreserveScopeProviderAndExplicitOverrides() {
    var inherited =
        new MultistageMoveSelectorConfig()
            .withStageProviderClass(BasicProvider.class)
            .withEntityClass(String.class)
            .withVariableName("value")
            .withCandidateCountLimit(13)
            .withProbeCountLimit(27)
            .withSelectionOrder(SelectionOrder.ORIGINAL);
    var copy = inherited.copyConfig();
    var child = new MultistageMoveSelectorConfig().withProbeCountLimit(8).inherit(copy);
    inherited.setVariableName("other");

    assertThat(child.getStageProviderClass()).isEqualTo(BasicProvider.class);
    assertThat(child.getEntityClass()).isEqualTo(String.class);
    assertThat(child.getVariableName()).isEqualTo("value");
    assertThat(child.determineCandidateCountLimit()).isEqualTo(13);
    assertThat(child.determineProbeCountLimit()).isEqualTo(8);
    assertThat(child.getSelectionOrder()).isEqualTo(SelectionOrder.ORIGINAL);
    var referenced = new ArrayList<Class<?>>();
    child.visitReferencedClasses(referenced::add);
    assertThat(referenced).contains(BasicProvider.class, String.class);
  }

  @Test
  void listCopyAndInheritancePreserveScopeProviderAndExplicitOverrides() {
    var inherited =
        new ListMultistageMoveSelectorConfig()
            .withStageProviderClass(ListProvider.class)
            .withEntityClass(String.class)
            .withVariableName("values")
            .withCandidateCountLimit(17)
            .withProbeCountLimit(29)
            .withSelectionOrder(SelectionOrder.ORIGINAL);
    var copy = inherited.copyConfig();
    var child = new ListMultistageMoveSelectorConfig().withCandidateCountLimit(4).inherit(copy);
    inherited.setVariableName("other");

    assertThat(child.getStageProviderClass()).isEqualTo(ListProvider.class);
    assertThat(child.getEntityClass()).isEqualTo(String.class);
    assertThat(child.getVariableName()).isEqualTo("values");
    assertThat(child.determineCandidateCountLimit()).isEqualTo(4);
    assertThat(child.determineProbeCountLimit()).isEqualTo(29);
    assertThat(child.getSelectionOrder()).isEqualTo(SelectionOrder.ORIGINAL);
    var referenced = new ArrayList<Class<?>>();
    child.visitReferencedClasses(referenced::add);
    assertThat(referenced).contains(ListProvider.class, String.class);
  }

  public static final class BasicProvider
      implements BasicVariableStageProvider<Object, String, String, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 0;
    }

    @Override
    public List<BasicVariableCustomStage<Object, String, String, SimpleScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      return List.of();
    }
  }

  public static final class ListProvider
      implements ListVariableStageProvider<Object, String, String, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 0;
    }

    @Override
    public List<ListVariableCustomStage<Object, String, String, SimpleScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      return List.of();
    }
  }
}
