package greycos.solver.core.impl.solver.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class IslandWorkAccountingTest {
  @Test
  void replacesCumulativeSnapshotsAndRetainsSeparatePhaseOccurrences() {
    var accounting = new IslandWorkAccounting();
    accounting.register("phase-0/island-0");
    accounting.register("phase-0/island-1");
    accounting.register("phase-1/island-0");
    var counts = new HashMap<>(Map.of("change", 2L));
    accounting.publish("phase-0/island-0", new SolverWorkSnapshot(3, 2, counts));
    counts.put("change", 99L);
    accounting.publish("phase-0/island-0", new SolverWorkSnapshot(7, 4, Map.of("change", 4L)));
    accounting.publish("phase-0/island-1", new SolverWorkSnapshot(5, 3, Map.of("swap", 3L)));
    accounting.publish("phase-1/island-0", new SolverWorkSnapshot(2, 1, Map.of("change", 1L)));
    assertThat(accounting.snapshot())
        .isEqualTo(new SolverWorkSnapshot(14, 8, Map.of("change", 5L, "swap", 3L)));
  }

  @Test
  void sealingFreezesAcceptedWorkAndFreshRunStartsEmpty() {
    var accounting = new IslandWorkAccounting();
    accounting.register("island");
    accounting.publish("island", new SolverWorkSnapshot(3, 2, Map.of()));
    accounting.seal();
    assertThat(accounting.publish("island", new SolverWorkSnapshot(100, 99, Map.of()))).isFalse();
    assertThat(accounting.snapshot()).isEqualTo(new SolverWorkSnapshot(3, 2, Map.of()));
    assertThat(new IslandWorkAccounting().snapshot()).isEqualTo(SolverWorkSnapshot.ZERO);
  }

  @Test
  void rejectsUnregisteredDuplicateAndRegressingWork() {
    var accounting = new IslandWorkAccounting();
    assertThatThrownBy(() -> accounting.publish("island", SolverWorkSnapshot.ZERO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not registered");
    accounting.register("island");
    assertThatThrownBy(() -> accounting.register("island"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already registered");
    accounting.publish("island", new SolverWorkSnapshot(2, 1, Map.of()));
    assertThatThrownBy(() -> accounting.publish("island", SolverWorkSnapshot.ZERO))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("decreased");
  }
}
