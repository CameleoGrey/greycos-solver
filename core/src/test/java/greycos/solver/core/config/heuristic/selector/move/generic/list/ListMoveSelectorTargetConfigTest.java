package greycos.solver.core.config.heuristic.selector.move.generic.list;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.stream.Stream;

import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.pinned.TestdataPinnedListEntity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class ListMoveSelectorTargetConfigTest {

  static Stream<MoveSelectorConfig<?>> targetedSelectors() {
    return Stream.of(
        new ListSwapMoveSelectorConfig().withEntityClass(TestdataListEntity.class),
        new SubListSwapMoveSelectorConfig().withEntityClass(TestdataListEntity.class),
        new KOptListMoveSelectorConfig().withEntityClass(TestdataListEntity.class),
        new ListRuinRecreateMoveSelectorConfig()
            .withEntityClass(TestdataListEntity.class)
            .withVariableName("valueList"));
  }

  @ParameterizedTest
  @MethodSource("targetedSelectors")
  void copyingInheritanceAndClassVisitingPreserveTarget(MoveSelectorConfig<?> original) {
    var copy = original.copyConfig();
    assertThat(copy).isNotSameAs(original).usingRecursiveComparison().isEqualTo(original);
    var visitedClasses = new ArrayList<Class<?>>();
    copy.visitReferencedClasses(visitedClasses::add);
    assertThat(visitedClasses).contains(TestdataListEntity.class);

    // Explicit targets override the inherited declaration, including a distinct list name.
    MoveSelectorConfig<?> overridden =
        switch (original) {
          case ListSwapMoveSelectorConfig config ->
              new ListSwapMoveSelectorConfig()
                  .withEntityClass(TestdataPinnedListEntity.class)
                  .inherit(config);
          case SubListSwapMoveSelectorConfig config ->
              new SubListSwapMoveSelectorConfig()
                  .withEntityClass(TestdataPinnedListEntity.class)
                  .inherit(config);
          case KOptListMoveSelectorConfig config ->
              new KOptListMoveSelectorConfig()
                  .withEntityClass(TestdataPinnedListEntity.class)
                  .inherit(config);
          case ListRuinRecreateMoveSelectorConfig config ->
              new ListRuinRecreateMoveSelectorConfig()
                  .withEntityClass(TestdataPinnedListEntity.class)
                  .withVariableName("anotherList")
                  .inherit(config);
          default -> throw new IllegalArgumentException("Unexpected selector: " + original);
        };
    visitedClasses.clear();
    overridden.visitReferencedClasses(visitedClasses::add);
    assertThat(visitedClasses)
        .contains(TestdataPinnedListEntity.class)
        .doesNotContain(TestdataListEntity.class);
    if (overridden instanceof ListRuinRecreateMoveSelectorConfig config) {
      assertThat(config.getVariableName()).isEqualTo("anotherList");
      config.setVariableName(null);
      config.setEntityClass(null);
      assertThat(config.getVariableName()).isNull();
      assertThat(config.getEntityClass()).isNull();
    }
  }

  @ParameterizedTest
  @CsvSource({
    "localSearch, listSwapMoveSelector",
    "localSearch, subListSwapMoveSelector",
    "localSearch, kOptListMoveSelector",
    "localSearch, listRuinRecreateMoveSelector",
    "islandModel, listSwapMoveSelector",
    "islandModel, subListSwapMoveSelector",
    "islandModel, kOptListMoveSelector",
    "islandModel, listRuinRecreateMoveSelector"
  })
  void xmlRoundTripPreservesTarget(String phaseName, String selectorName) {
    var variableElement =
        selectorName.equals("listRuinRecreateMoveSelector")
            ? "<variableName>valueList</variableName>"
            : "";
    var xml =
        """
        <solver xmlns="https://github.com/CameleoGrey/greycos-solver/xsd/solver">
          <%s>
            <%s>
              <entityClass>%s</entityClass>
              %s
            </%s>
          </%s>
        </solver>
        """
            .formatted(
                phaseName,
                selectorName,
                TestdataListEntity.class.getName(),
                variableElement,
                selectorName,
                phaseName);
    var io = new SolverConfigIO();
    var config = io.read(new StringReader(xml));
    MoveSelectorConfig<?> selector =
        switch (config.getPhaseConfigList().getFirst()) {
          case LocalSearchPhaseConfig phase -> phase.getMoveSelectorConfig();
          case IslandModelPhaseConfig phase -> phase.getMoveSelectorConfig();
          default -> throw new IllegalArgumentException("Unexpected phase: " + phaseName);
        };
    var visitedClasses = new ArrayList<Class<?>>();
    selector.visitReferencedClasses(visitedClasses::add);
    assertThat(visitedClasses).contains(TestdataListEntity.class);
    if (selector instanceof ListRuinRecreateMoveSelectorConfig ruinConfig) {
      assertThat(ruinConfig.getVariableName()).isEqualTo("valueList");
    }

    var output = new StringWriter();
    io.write(config, output);
    assertThat(output.toString())
        .contains("<entityClass>" + TestdataListEntity.class.getName() + "</entityClass>");
    assertThat(io.read(new StringReader(output.toString())))
        .usingRecursiveComparison()
        .isEqualTo(config);
  }
}
