package greycos.solver.math.impl.cotwin;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.impl.model.MathCompiler;

import org.junit.jupiter.api.Test;

class MathSolutionTest {

  @Test
  void initializationHonorsValuesAndPinsAndIsRepeatable() {
    var builder = MathModel.builder();
    var initial = builder.intVar("initial", -100, 100);
    var fixed = builder.intVar("fixed", -100, 100);
    var singleton = builder.intVar("singleton", Long.MAX_VALUE, Long.MAX_VALUE);
    var random = builder.intVar("random", Long.MIN_VALUE, Long.MIN_VALUE + 100);
    builder.initialValue(initial, 17);
    builder.fix(fixed, -29);
    var compiled = MathCompiler.compile(builder.build());
    var first = MathSolution.initialize(compiled, 37);
    var second = MathSolution.initialize(compiled, 37);
    assertThat(first.getDecisions())
        .extracting(MathDecision::getId)
        .containsExactly(initial.id(), fixed.id(), singleton.id(), random.id());
    assertThat(first.getDecisions())
        .extracting(MathDecision::getValue)
        .containsExactlyElementsOf(
            second.getDecisions().stream().map(MathDecision::getValue).toList());
    assertThat(first.getDecisions().get(0).getValue()).isEqualTo(17L);
    assertThat(first.getDecisions().get(1).getValue()).isEqualTo(-29L);
    assertThat(first.getDecisions().get(2).getValue()).isEqualTo(Long.MAX_VALUE);
    assertThat(first.getDecisions())
        .extracting(MathDecision::isPinned)
        .containsExactly(false, true, true, false);
    assertThat(first.getDecisions())
        .allSatisfy(
            decision ->
                assertThat(decision.getValueRange().contains(decision.getValue())).isTrue());
  }

  @Test
  void explicitCloneSharesOnlyImmutableDefinitions() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", 0, 10);
    var y = builder.intVar("y", 2, 2);
    builder.initialValue(x, 3);
    builder.addConstraint("capacity", x.plus(y).le(8));
    builder.maximize(x);
    var original = MathSolution.initialize(MathCompiler.compile(builder.build()), 0);
    original.setScore(HardSoftScore.of(0, 3));
    var clone = new MathSolutionCloner().cloneSolution(original);
    assertThat(clone).isNotSameAs(original);
    assertThat(clone.getModel()).isSameAs(original.getModel());
    assertThat(clone.getRows()).isSameAs(original.getRows());
    assertThat(clone.getTerms()).isSameAs(original.getTerms());
    assertThat(clone.getScore()).isEqualTo(original.getScore());
    assertThat(clone.getDecisions()).isNotSameAs(original.getDecisions());
    for (int i = 0; i < original.getDecisions().size(); i++) {
      var source = original.getDecisions().get(i);
      var target = clone.getDecisions().get(i);
      assertThat(target).isNotSameAs(source);
      assertThat(target.getValueRange()).isSameAs(source.getValueRange());
      assertThat(target.getId()).isEqualTo(source.getId());
      assertThat(target.isPinned()).isEqualTo(source.isPinned());
    }
    clone.getDecisions().getFirst().setValue(9L);
    clone.getDecisions().removeLast();
    clone.setScore(HardSoftScore.of(-3, 9));
    assertThat(original.getDecisions()).hasSize(2);
    assertThat(original.getDecisions().getFirst().getValue()).isEqualTo(3L);
    assertThat(original.getScore()).isEqualTo(HardSoftScore.of(0, 3));
  }
}
