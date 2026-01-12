package ai.greycos.solver.core.impl.partitionedsearch.partitioner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Tests for {@link RoundRobinPartitioner}. */
class RoundRobinPartitionerTest {

  @Test
  void constructorValidatesPartCount() {
    assertThatThrownBy(() -> new RoundRobinPartitioner<Object>(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Partition count must be at least 1");

    assertThatThrownBy(() -> new RoundRobinPartitioner<Object>(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Partition count must be at least 1");
  }

  @Test
  void getPartCount() {
    var partitioner = new RoundRobinPartitioner<Object>(3);
    assertThat(partitioner.getPartCount()).isEqualTo(3);
  }
}
