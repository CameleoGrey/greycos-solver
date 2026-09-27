package greycos.solver.core.config.partitionedsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;

import org.junit.jupiter.api.Test;

class PartitionedSearchPhaseXmlTest {

  @Test
  void nestedIslandRoundTripPreservesTypeAndSettings() {
    var island =
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withMoveThreadCount("NONE")
            .withPhaseConfigList(
                List.of(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig()
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(3))));
    var config =
        new SolverConfig()
            .withPhases(
                new PartitionedSearchPhaseConfig()
                    .withRunnablePartThreadLimit("2")
                    .withPhaseConfigs(island));
    var io = new SolverConfigIO();
    var writer = new StringWriter();
    io.write(config, writer);
    assertThat(writer.toString()).contains("<islandModel>").doesNotContain("<customPhase");
    assertThat(io.read(new StringReader(writer.toString())))
        .usingRecursiveComparison()
        .isEqualTo(config);
  }

  @Test
  void namespacedNestedIslandValidatesAgainstGeneratedSchema() {
    var xml =
        """
        <solver xmlns="%s">
          <partitionedSearch>
            <runnablePartThreadLimit>2</runnablePartThreadLimit>
            <islandModel>
              <islandCount>2</islandCount>
              <moveThreadCount>NONE</moveThreadCount>
              <constructionHeuristic/>
              <localSearch><termination><stepCountLimit>3</stepCountLimit></termination></localSearch>
            </islandModel>
          </partitionedSearch>
        </solver>
        """
            .formatted(SolverConfig.XML_NAMESPACE);
    var config = new SolverConfigIO().read(new StringReader(xml));
    var partition = (PartitionedSearchPhaseConfig) config.getPhaseConfigList().getFirst();
    assertThat(partition.getPhaseConfigList())
        .singleElement()
        .isInstanceOf(IslandModelPhaseConfig.class);
    var island = (IslandModelPhaseConfig) partition.getPhaseConfigList().getFirst();
    assertThat(island.getIslandCount()).isEqualTo(2);
    assertThat(island.getMoveThreadCount()).isEqualTo("NONE");
    assertThat(island.getPhaseConfigList()).hasSize(2);
    assertThat(island.getPhaseConfigList().get(1).getTerminationConfig().getStepCountLimit())
        .isEqualTo(3);
  }
}
