package greycos.solver.math.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class MathModelTest {

  @Test
  void immutableDefinitionsAndOneShotBuilder() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", -3, 7);
    var y = builder.binaryVar("y");
    var expression = x.times(3).plus(y).minus(2);
    builder
        .initialValue(x, 4)
        .fix(y, 1)
        .addConstraint("capacity", expression.le(18))
        .maximize(expression);
    var model = builder.build();

    assertThat(model.variables()).containsExactly(x, y);
    assertThat(x.id()).isZero();
    assertThat(y.id()).isEqualTo(1);
    assertThat(x.lowerBound()).isEqualTo(-3);
    assertThat(x.upperBound()).isEqualTo(7);
    assertThat(y.lowerBound()).isZero();
    assertThat(y.upperBound()).isEqualTo(1);
    assertThat(model.objective()).isSameAs(expression);
    assertThat(model.objectiveDirection()).isEqualTo(OptimizationDirection.MAXIMIZE);
    assertThat(model.constraints())
        .containsExactly(new NamedConstraint("capacity", expression, Relation.LE, 18));
    assertThat(model.initialValue(x)).hasValue(4);
    assertThat(model.fixedValue(x)).isEmpty();
    assertThat(model.fixedValue(y)).hasValue(1);
    assertThat(model.initialValue(y)).isEmpty();
    assertThatThrownBy(() -> model.variables().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> model.constraints().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatIllegalStateException().isThrownBy(builder::build);
    assertThatIllegalStateException().isThrownBy(() -> builder.intVar("z", 0, 1));
    assertThatIllegalStateException().isThrownBy(() -> builder.binaryVar("z"));
    assertThatIllegalStateException().isThrownBy(() -> builder.initialValue(x, 0));
    assertThatIllegalStateException().isThrownBy(() -> builder.fix(x, 0));
    assertThatIllegalStateException().isThrownBy(() -> builder.addConstraint("later", x.eq(0)));
    assertThatIllegalStateException().isThrownBy(() -> builder.minimize(x));
  }

  @Test
  void originalNodesRetainOperandsAndDefensivelyCopyCollections() {
    var builder = MathModel.builder();
    var x = builder.binaryVar("x");
    var operands = new ArrayList<LinearExpression>(List.of(x, LinearExpression.constant(2)));
    var expression = LinearExpression.sum(operands);
    operands.clear();
    var sum = (LinearExpression.Sum) expression;
    assertThat(sum.operands()).hasSize(2);
    assertThat(sum.operands().getFirst()).isSameAs(x);
    assertThatThrownBy(() -> sum.operands().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(x.times(0)).isInstanceOf(LinearExpression.Scale.class);
    assertThat(x.minus(x)).isInstanceOf(LinearExpression.Sum.class);
  }

  @Test
  void defaultObjectiveAndEmptyModel() {
    var model = MathModel.builder().build();
    assertThat(model.variables()).isEmpty();
    assertThat(model.constraints()).isEmpty();
    assertThat(model.objective()).isEqualTo(LinearExpression.constant(0));
    assertThat(model.objectiveDirection()).isEqualTo(OptimizationDirection.MINIMIZE);
  }

  @Test
  void namesDomainsAndMultipleObjectivesFailWithContext() {
    var builder = MathModel.builder();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.binaryVar(" "))
        .withMessageContaining("name");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.intVar("bad", 2, 1))
        .withMessageContaining("bad");
    var x = builder.binaryVar("x");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.intVar("x", 0, 1))
        .withMessageContaining("x");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.binaryVar("x"))
        .withMessageContaining("x");
    builder.addConstraint("capacity", x.le(1));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.addConstraint("capacity", x.ge(0)))
        .withMessageContaining("capacity");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.addConstraint("$objective", x.eq(0)))
        .withMessageContaining("reserved");
    builder.minimize(x);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.maximize(x))
        .withMessageContaining("multiple");
  }

  @Test
  void handlesUseIdentityAndRejectForeignValues() {
    var first = MathModel.builder();
    var second = MathModel.builder();
    var x = first.binaryVar("same");
    var foreign = second.binaryVar("same");
    assertThat(x).isNotEqualTo(foreign);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> first.initialValue(foreign, 0))
        .withMessageContaining("same")
        .withMessageContaining("another model");
    assertThatIllegalArgumentException().isThrownBy(() -> first.fix(foreign, 0));
    var model = first.build();
    assertThatIllegalArgumentException().isThrownBy(() -> model.initialValue(foreign));
    assertThatIllegalArgumentException().isThrownBy(() -> model.fixedValue(foreign));
  }

  @Test
  void initialAndFixedValuesMustFitAndAgree() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", -1, 1);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.initialValue(x, 2))
        .withMessageContaining("x")
        .withMessageContaining("2");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> builder.fix(x, -2))
        .withMessageContaining("x")
        .withMessageContaining("-2");
    builder.initialValue(x, -1).fix(x, 1);
    assertThatIllegalArgumentException()
        .isThrownBy(builder::build)
        .withMessageContaining("initial value")
        .withMessageContaining("fixed value");
    builder.initialValue(x, 1);
    assertThat(builder.build().fixedValue(x)).hasValue(1);
  }

  @Test
  void nullDefinitionsFailAtConstruction() {
    var builder = MathModel.builder();
    var x = builder.binaryVar("x");
    assertThatIllegalArgumentException().isThrownBy(() -> builder.binaryVar(null));
    assertThatIllegalArgumentException().isThrownBy(() -> builder.addConstraint("missing", null));
    assertThatIllegalArgumentException().isThrownBy(() -> builder.minimize(null));
    assertThatIllegalArgumentException().isThrownBy(() -> LinearExpression.sum(null));
    assertThatIllegalArgumentException().isThrownBy(() -> new LinearExpression.Sum(null));
    assertThatIllegalArgumentException().isThrownBy(() -> new LinearExpression.Scale(null, 1));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new LinearConstraint(null, Relation.LE, 0));
    assertThatIllegalArgumentException().isThrownBy(() -> new LinearConstraint(x, null, 0));
    assertThatIllegalArgumentException().isThrownBy(() -> x.plus(null));
    assertThatIllegalArgumentException().isThrownBy(() -> x.minus(null));
  }
}
