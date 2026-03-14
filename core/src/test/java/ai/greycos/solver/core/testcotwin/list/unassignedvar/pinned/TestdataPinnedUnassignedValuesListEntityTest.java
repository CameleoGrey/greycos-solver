package ai.greycos.solver.core.testcotwin.list.unassignedvar.pinned;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestdataPinnedUnassignedValuesListEntityTest {

  @Test
  void createWithValuesInitializesEntityIndexesAndChainBoundaries() {
    var first = new TestdataPinnedUnassignedValuesListValue("a");
    var second = new TestdataPinnedUnassignedValuesListValue("b");
    var third = new TestdataPinnedUnassignedValuesListValue("c");

    var entity =
        TestdataPinnedUnassignedValuesListEntity.createWithValues("entity", first, second, third);

    assertThat(first.getEntity()).isSameAs(entity);
    assertThat(second.getEntity()).isSameAs(entity);
    assertThat(third.getEntity()).isSameAs(entity);

    assertThat(first.getIndex()).isZero();
    assertThat(second.getIndex()).isEqualTo(1);
    assertThat(third.getIndex()).isEqualTo(2);

    assertThat(first.getPrevious()).isNull();
    assertThat(first.getNext()).isSameAs(second);
    assertThat(second.getPrevious()).isSameAs(first);
    assertThat(second.getNext()).isSameAs(third);
    assertThat(third.getPrevious()).isSameAs(second);
    assertThat(third.getNext()).isNull();
  }
}
