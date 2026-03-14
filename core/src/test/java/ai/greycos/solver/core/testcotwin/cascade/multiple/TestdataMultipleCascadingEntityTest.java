package ai.greycos.solver.core.testcotwin.cascade.multiple;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestdataMultipleCascadingEntityTest {

  @Test
  void createWithValuesInitializesTheListChainFromNullPreviousToNullNext() {
    var first = new TestdataMultipleCascadingValue(10);
    var second = new TestdataMultipleCascadingValue(20);
    var third = new TestdataMultipleCascadingValue(30);

    var entity = TestdataMultipleCascadingEntity.createWithValues("entity", first, second, third);

    assertThat(first.getEntity()).isSameAs(entity);
    assertThat(second.getEntity()).isSameAs(entity);
    assertThat(third.getEntity()).isSameAs(entity);

    assertThat(first.getPrevious()).isNull();
    assertThat(first.getNext()).isSameAs(second);
    assertThat(second.getPrevious()).isSameAs(first);
    assertThat(second.getNext()).isSameAs(third);
    assertThat(third.getPrevious()).isSameAs(second);
    assertThat(third.getNext()).isNull();

    assertThat(first.getCascadeValue()).isEqualTo(10);
    assertThat(second.getCascadeValue()).isEqualTo(20);
    assertThat(third.getCascadeValue()).isEqualTo(30);
    assertThat(first.getNumberOfCalls()).isEqualTo(1);
    assertThat(second.getNumberOfCalls()).isEqualTo(1);
    assertThat(third.getNumberOfCalls()).isEqualTo(1);
  }
}
