package greycos.solver.benchmark.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;

import greycos.solver.benchmark.impl.io.jaxb.PlannerBenchmarkConfigIO;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MultipleListBenchmarkConfigTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void validatesAndRoundTripsListDeclarationTargets(boolean namespaced) {
    var owner = TestdataMultipleListEntity.class.getName();
    var xml =
        """
        <plannerBenchmark%s>
          <solverBenchmark>
            <name>Independent lists</name>
            <solver>
              <localSearch>
                <unionMoveSelector>
                  <listSwapMoveSelector>
                    <entityClass>%s</entityClass>
                    <valueSelector variableName="firstValueList"/>
                  </listSwapMoveSelector>
                  <subListSwapMoveSelector>
                    <entityClass>%s</entityClass>
                    <subListSelector><valueSelector variableName="secondValueList"/></subListSelector>
                  </subListSwapMoveSelector>
                  <kOptListMoveSelector>
                    <entityClass>%s</entityClass>
                    <originSelector variableName="firstValueList"/>
                    <valueSelector variableName="firstValueList"/>
                  </kOptListMoveSelector>
                  <listRuinRecreateMoveSelector>
                    <entityClass>%s</entityClass>
                    <variableName>secondValueList</variableName>
                  </listRuinRecreateMoveSelector>
                </unionMoveSelector>
              </localSearch>
            </solver>
          </solverBenchmark>
        </plannerBenchmark>
        """
            .formatted(
                namespaced ? " xmlns=\"" + PlannerBenchmarkConfig.XML_NAMESPACE + "\"" : "",
                owner,
                owner,
                owner,
                owner);
    var io = new PlannerBenchmarkConfigIO();
    var original = io.read(new StringReader(xml));
    var writer = new StringWriter();
    io.write(original, writer);
    var restored = io.read(new StringReader(writer.toString()));
    var phase =
        (LocalSearchPhaseConfig)
            restored
                .getSolverBenchmarkConfigList()
                .getFirst()
                .getSolverConfig()
                .getPhaseConfigList()
                .getFirst();
    var selectors = ((UnionMoveSelectorConfig) phase.getMoveSelectorConfig()).getMoveSelectorList();
    var swap = (ListSwapMoveSelectorConfig) selectors.get(0);
    var subListSwap = (SubListSwapMoveSelectorConfig) selectors.get(1);
    var kOpt = (KOptListMoveSelectorConfig) selectors.get(2);
    var ruin = (ListRuinRecreateMoveSelectorConfig) selectors.get(3);
    assertThat(swap.getEntityClass()).isEqualTo(TestdataMultipleListEntity.class);
    assertThat(swap.getValueSelectorConfig().getVariableName()).isEqualTo("firstValueList");
    assertThat(subListSwap.getEntityClass()).isEqualTo(TestdataMultipleListEntity.class);
    assertThat(subListSwap.getSubListSelectorConfig().getValueSelectorConfig().getVariableName())
        .isEqualTo("secondValueList");
    assertThat(kOpt.getEntityClass()).isEqualTo(TestdataMultipleListEntity.class);
    assertThat(kOpt.getOriginSelectorConfig().getVariableName()).isEqualTo("firstValueList");
    assertThat(kOpt.getValueSelectorConfig().getVariableName()).isEqualTo("firstValueList");
    assertThat(ruin.getEntityClass()).isEqualTo(TestdataMultipleListEntity.class);
    assertThat(ruin.getVariableName()).isEqualTo("secondValueList");
  }
}
