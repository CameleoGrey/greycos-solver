package ai.greycos.solver.core.testcotwin.equals.list;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestdataEqualsByCodeListEntityTest {

  @Test
  void createWithValuesAssignsDistinctIndexesToEqualValues() {
    var first = new TestdataEqualsByCodeListValue("value");
    var second = new TestdataEqualsByCodeListValue("value");

    var entity = TestdataEqualsByCodeListEntity.createWithValues("entity", first, second);

    assertThat(entity.getValueList()).containsExactly(first, second);
    assertThat(first.getEntity()).isSameAs(entity);
    assertThat(second.getEntity()).isSameAs(entity);
    assertThat(first.getIndex()).isZero();
    assertThat(second.getIndex()).isEqualTo(1);
  }
}
