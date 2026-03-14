package ai.greycos.solver.core.testcotwin.list.pinned.noshadows;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestdataPinnedNoShadowsListEntityTest {

  @Test
  void createWithValuesAssignsIndexesByPosition() {
    var first = new TestdataPinnedNoShadowsListValue("v");
    var second = new TestdataPinnedNoShadowsListValue("v");

    var entity = TestdataPinnedNoShadowsListEntity.createWithValues("entity", first, second);

    assertThat(entity.getValueList()).containsExactly(first, second);
    assertThat(first.getIndex()).isZero();
    assertThat(second.getIndex()).isEqualTo(1);
  }
}
