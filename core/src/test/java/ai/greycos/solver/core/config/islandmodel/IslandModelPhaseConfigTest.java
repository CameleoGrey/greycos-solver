package ai.greycos.solver.core.config.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.Collections;

import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchType;
import ai.greycos.solver.core.config.solver.termination.TerminationConfig;

import org.junit.jupiter.api.Test;

class IslandModelPhaseConfigTest {

  @Test
  void withMethodCallsProperlyChain() {
    final int islandCount = 8;
    final int migrationFrequency = 50;

    IslandModelPhaseConfig islandModelPhaseConfig =
        new IslandModelPhaseConfig()
            .withIslandCount(islandCount)
            .withMigrationFrequency(migrationFrequency)
            .withCompareGlobalEnabled(false)
            .withReceiveGlobalUpdateFrequency(25)
            .withMigrationTimeout(500L)
            .withLocalSearchType(LocalSearchType.TABU_SEARCH)
            .withTerminationConfig(new TerminationConfig().withBestScoreFeasible(true));

    assertSoftly(
        softly -> {
          softly.assertThat(islandModelPhaseConfig.getIslandCount()).isEqualTo(islandCount);
          softly
              .assertThat(islandModelPhaseConfig.getMigrationFrequency())
              .isEqualTo(migrationFrequency);
          softly.assertThat(islandModelPhaseConfig.getCompareGlobalEnabled()).isFalse();
          softly.assertThat(islandModelPhaseConfig.getReceiveGlobalUpdateFrequency()).isEqualTo(25);
          softly.assertThat(islandModelPhaseConfig.getMigrationTimeout()).isEqualTo(500L);
          softly
              .assertThat(islandModelPhaseConfig.getLocalSearchType())
              .isEqualTo(LocalSearchType.TABU_SEARCH);
          softly.assertThat(islandModelPhaseConfig.getTerminationConfig()).isNotNull();
          softly
              .assertThat(islandModelPhaseConfig.getTerminationConfig().getBestScoreFeasible())
              .isTrue();
        });
  }

  @Test
  void childInheritsIslandCountFromParentWhenNull() {
    IslandModelPhaseConfig child = new IslandModelPhaseConfig();
    IslandModelPhaseConfig parent = new IslandModelPhaseConfig().withIslandCount(8);

    child.inherit(parent);

    assertThat(child.getIslandCount()).isEqualTo(8);
  }

  @Test
  void childKeepsIslandCountWhenNotNull() {
    IslandModelPhaseConfig child = new IslandModelPhaseConfig().withIslandCount(4);
    IslandModelPhaseConfig parent = new IslandModelPhaseConfig().withIslandCount(8);

    child.inherit(parent);

    assertThat(child.getIslandCount()).isEqualTo(4);
  }

  @Test
  void childInheritsMigrationAndGlobalOptionsFromParentWhenNull() {
    IslandModelPhaseConfig child = new IslandModelPhaseConfig();
    IslandModelPhaseConfig parent =
        new IslandModelPhaseConfig()
            .withMigrationFrequency(50)
            .withCompareGlobalEnabled(false)
            .withReceiveGlobalUpdateFrequency(25)
            .withMigrationTimeout(500L);

    child.inherit(parent);

    assertSoftly(
        softly -> {
          softly.assertThat(child.getMigrationFrequency()).isEqualTo(50);
          softly.assertThat(child.getCompareGlobalEnabled()).isFalse();
          softly.assertThat(child.getReceiveGlobalUpdateFrequency()).isEqualTo(25);
          softly.assertThat(child.getMigrationTimeout()).isEqualTo(500L);
        });
  }

  @Test
  void childInheritsPhaseConfigListFromParentWhenNull() {
    LocalSearchPhaseConfig innerPhase = new LocalSearchPhaseConfig();
    IslandModelPhaseConfig child = new IslandModelPhaseConfig();
    IslandModelPhaseConfig parent =
        new IslandModelPhaseConfig().withPhaseConfigList(Collections.singletonList(innerPhase));

    child.inherit(parent);

    assertThat(child.getPhaseConfigList()).isNotNull();
    assertThat(child.getPhaseConfigList()).hasSize(1);
    assertThat(child.getPhaseConfigList().get(0)).isNotSameAs(innerPhase);
  }

  @Test
  void childKeepsPhaseConfigListWhenNotNull() {
    LocalSearchPhaseConfig childPhase = new LocalSearchPhaseConfig();
    LocalSearchPhaseConfig parentPhase = new LocalSearchPhaseConfig();
    IslandModelPhaseConfig child =
        new IslandModelPhaseConfig().withPhaseConfigList(Collections.singletonList(childPhase));
    IslandModelPhaseConfig parent =
        new IslandModelPhaseConfig().withPhaseConfigList(Collections.singletonList(parentPhase));

    child.inherit(parent);

    assertThat(child.getPhaseConfigList()).hasSize(1);
    assertThat(child.getPhaseConfigList().get(0)).isSameAs(childPhase);
  }

  @Test
  void copyConfigCreatesIndependentCopy() {
    IslandModelPhaseConfig original = new IslandModelPhaseConfig().withIslandCount(8);

    IslandModelPhaseConfig copy = original.copyConfig();

    assertThat(copy.getIslandCount()).isEqualTo(original.getIslandCount());

    original.setIslandCount(16);
    assertThat(copy.getIslandCount()).isEqualTo(8);
  }

  @Test
  void copyConfigPreservesMigrationAndGlobalOptions() {
    IslandModelPhaseConfig original =
        new IslandModelPhaseConfig()
            .withMigrationFrequency(9)
            .withCompareGlobalEnabled(false)
            .withReceiveGlobalUpdateFrequency(7)
            .withMigrationTimeout(11L);

    IslandModelPhaseConfig copy = original.copyConfig();

    assertSoftly(
        softly -> {
          softly.assertThat(copy.getMigrationFrequency()).isEqualTo(9);
          softly.assertThat(copy.getCompareGlobalEnabled()).isFalse();
          softly.assertThat(copy.getReceiveGlobalUpdateFrequency()).isEqualTo(7);
          softly.assertThat(copy.getMigrationTimeout()).isEqualTo(11L);
        });

    original.setMigrationFrequency(12);
    original.setCompareGlobalEnabled(true);
    original.setReceiveGlobalUpdateFrequency(17);
    original.setMigrationTimeout(19L);

    assertSoftly(
        softly -> {
          softly.assertThat(copy.getMigrationFrequency()).isEqualTo(9);
          softly.assertThat(copy.getCompareGlobalEnabled()).isFalse();
          softly.assertThat(copy.getReceiveGlobalUpdateFrequency()).isEqualTo(7);
          softly.assertThat(copy.getMigrationTimeout()).isEqualTo(11L);
        });
  }
}
