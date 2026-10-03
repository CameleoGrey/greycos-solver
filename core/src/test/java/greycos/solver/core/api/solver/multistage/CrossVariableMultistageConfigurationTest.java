package greycos.solver.core.api.solver.multistage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.CrossVariableMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageVariableConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageVariableKind;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;

class CrossVariableMultistageConfigurationTest {

  private static final BasicVariableReference<String, Integer> BASIC =
      BasicVariableReference.of(String.class, "amount", Integer.class);
  private static final ListVariableReference<String, Long> LIST =
      ListVariableReference.of(String.class, "values", Long.class);

  @Test
  void crossVariableSelectorRoundTripsStandaloneInNestedUnionsIslandsAndPartitions() {
    var selector =
        new CrossVariableMultistageMoveSelectorConfig()
            .withStageProviderClass(CrossProvider.class)
            .withVariables(BASIC, LIST)
            .withCandidateCountLimit(11)
            .withProbeCountLimit(22)
            .withSelectionOrder(SelectionOrder.ORIGINAL);
    var config =
        new SolverConfig()
            .withMoveThreadCount("2")
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
                    .withMoveSelectorConfig(selector.copyConfig()),
                new PartitionedSearchPhaseConfig()
                    .withPhaseConfigs(
                        new LocalSearchPhaseConfig()
                            .withMoveSelectorConfig(selector.copyConfig())));

    var io = new SolverConfigIO();
    var writer = new StringWriter();
    io.write(config, writer);
    var xml = writer.toString();
    var restored = io.read(new StringReader(xml));

    assertThat(restored).usingRecursiveComparison().isEqualTo(config);
    // write() omits namespaces; restore the solver namespace to validate the generated schema.
    var namespacedXml =
        xml.replace("<solver>", "<solver xmlns=\"" + SolverConfig.XML_NAMESPACE + "\">");
    assertThat(namespacedXml).contains("<solver xmlns=\"" + SolverConfig.XML_NAMESPACE + "\">");
    assertThat(io.read(new StringReader(namespacedXml)))
        .usingRecursiveComparison()
        .isEqualTo(config);
    assertThat(xml)
        .contains("<crossVariableMultistageMoveSelector>", "<variable>", "<kind>BASIC</kind>")
        .contains("<kind>LIST</kind>", "<valueClass>java.lang.Integer</valueClass>")
        .doesNotContain("<variableList>");
  }

  @Test
  void unsetPropertiesStayUnsetWhenDefaultsAreResolvedAndCopied() {
    var config = new CrossVariableMultistageMoveSelectorConfig();

    assertThat(config.determineCandidateCountLimit()).isEqualTo(64);
    assertThat(config.determineProbeCountLimit()).isEqualTo(10_000);
    assertThat(config.getCandidateCountLimit()).isNull();
    assertThat(config.getProbeCountLimit()).isNull();
    assertThat(config.getVariableList()).isNull();
    assertThat(config.copyConfig()).usingRecursiveComparison().isEqualTo(config);
    assertThat(config.hasNearbySelectionConfig()).isFalse();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> config.withCandidateCountLimit(0).determineCandidateCountLimit())
        .withMessageContaining("candidateCountLimit (0)");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> config.withCandidateCountLimit(-1).determineCandidateCountLimit())
        .withMessageContaining("candidateCountLimit (-1)");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> config.withProbeCountLimit(0).determineProbeCountLimit())
        .withMessageContaining("probeCountLimit (0)");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> config.withProbeCountLimit(-1).determineProbeCountLimit())
        .withMessageContaining("probeCountLimit (-1)");
  }

  @Test
  void unsetDeclarationListInheritsIndependentCopiesAndVisitsEveryReferencedClass() {
    var inherited =
        new CrossVariableMultistageMoveSelectorConfig()
            .withStageProviderClass(CrossProvider.class)
            .withVariables(BASIC, LIST)
            .withCandidateCountLimit(13)
            .withProbeCountLimit(27)
            .withSelectionOrder(SelectionOrder.ORIGINAL);
    var copy = inherited.copyConfig();
    var child =
        new CrossVariableMultistageMoveSelectorConfig().withProbeCountLimit(8).inherit(copy);
    inherited.getVariableList().getFirst().setVariableName("changed");
    inherited.getVariableList().clear();
    copy.getVariableList().getLast().setValueClass(Double.class);

    assertThat(child.getVariableList()).hasSize(2);
    assertThat(child.getVariableList().getFirst().getVariableName()).isEqualTo("amount");
    assertThat(child.getVariableList().getLast().getValueClass()).isEqualTo(Long.class);
    assertThat(child.getStageProviderClass()).isEqualTo(CrossProvider.class);
    assertThat(child.determineCandidateCountLimit()).isEqualTo(13);
    assertThat(child.determineProbeCountLimit()).isEqualTo(8);
    assertThat(child.getSelectionOrder()).isEqualTo(SelectionOrder.ORIGINAL);
    var referenced = new ArrayList<Class<?>>();
    child.visitReferencedClasses(referenced::add);
    assertThat(referenced).contains(CrossProvider.class, String.class, Integer.class, Long.class);
  }

  @Test
  void explicitDeclarationListReplacesInheritedListEvenWhenEmpty() {
    var inherited = new CrossVariableMultistageMoveSelectorConfig().withVariables(BASIC, LIST);
    var child =
        new CrossVariableMultistageMoveSelectorConfig().withVariables(LIST).inherit(inherited);
    var empty =
        new CrossVariableMultistageMoveSelectorConfig()
            .withVariableList(new ArrayList<>())
            .inherit(inherited);

    assertThat(child.getVariableList()).hasSize(1);
    assertThat(child.getVariableList().getFirst().getKind()).isEqualTo(MultistageVariableKind.LIST);
    assertThat(empty.getVariableList()).isEmpty();
    assertThat(empty.copyConfig().getVariableList()).isEmpty();
  }

  @Test
  void referenceConversionPreservesKindsAndReplacesExistingDeclarations() {
    var variables = new MultistageVariableReference<?, ?>[] {BASIC, LIST};
    var config = new CrossVariableMultistageMoveSelectorConfig().withVariables(variables);
    variables[0] = LIST;

    assertThat(config.getVariableList())
        .extracting(MultistageVariableConfig::getKind)
        .containsExactly(MultistageVariableKind.BASIC, MultistageVariableKind.LIST);
    assertThat(config.getVariableList())
        .extracting(MultistageVariableConfig::getValueClass)
        .containsExactly(Integer.class, Long.class);
    assertThat(config.getVariableList())
        .extracting(MultistageVariableConfig::getVariableName)
        .containsExactly("amount", "values");
    config.withVariables(LIST);
    assertThat(config.getVariableList()).hasSize(1);
    assertThatNullPointerException()
        .isThrownBy(() -> config.withVariables((MultistageVariableReference<?, ?>[]) null));
    assertThatNullPointerException().isThrownBy(() -> config.withVariables(BASIC, null));
    assertThat(config.getVariableList()).hasSize(1);
  }

  @Test
  void declarationCopyAndInheritancePreserveExplicitOverrides() {
    var parent =
        new MultistageVariableConfig()
            .withKind(MultistageVariableKind.BASIC)
            .withEntityClass(String.class)
            .withVariableName("amount")
            .withValueClass(Integer.class);
    var child =
        new MultistageVariableConfig().withVariableName("other").inherit(parent.copyConfig());
    parent.setValueClass(Long.class);

    assertThat(child.getKind()).isEqualTo(MultistageVariableKind.BASIC);
    assertThat(child.getEntityClass()).isEqualTo(String.class);
    assertThat(child.getVariableName()).isEqualTo("other");
    assertThat(child.getValueClass()).isEqualTo(Integer.class);
    var referenced = new ArrayList<Class<?>>();
    child.visitReferencedClasses(referenced::add);
    assertThat(referenced).containsExactly(String.class, Integer.class);
  }

  public static final class CrossProvider
      implements CrossVariableStageProvider<Object, SimpleScore> {
    @Override
    public long getCandidateCount() {
      return 0;
    }

    @Override
    public List<CrossVariableCustomStage<Object, SimpleScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      return List.of();
    }
  }
}
