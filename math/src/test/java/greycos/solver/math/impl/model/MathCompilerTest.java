package greycos.solver.math.impl.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import greycos.solver.math.api.LinearExpression;
import greycos.solver.math.api.MathModel;

import org.junit.jupiter.api.Test;

class MathCompilerTest {

  @Test
  void normalizeExactIntermediateCoefficientsAndConstantsWithoutLosingRows() {
    var builder = MathModel.builder();
    var x = builder.binaryVar("x");
    var large = x.times(Long.MAX_VALUE).times(Long.MAX_VALUE);
    builder.addConstraint("cancelled", large.minus(large).le(-1));
    builder.addConstraint("empty", LinearExpression.sum(List.of()).eq(0));
    builder.maximize(
        x.times(Long.MAX_VALUE)
            .plus(x)
            .minus(x)
            .plus(LinearExpression.constant(Long.MIN_VALUE).minus(Long.MIN_VALUE)));
    var model = builder.build();
    var compiled = MathCompiler.compile(model);

    assertThat(compiled.model()).isSameAs(model);
    assertThat(compiled.rows())
        .extracting(Row::name)
        .containsExactly("cancelled", "empty", "$objective");
    assertThat(compiled.rows()).extracting(Row::id).containsExactly(0, 1, 2);
    assertThat(compiled.terms())
        .containsExactly(new Term(compiled.rows().get(2), x.id(), Long.MAX_VALUE));
    assertThat(compiled.rows().getFirst().violation(0)).isEqualTo(1);
    assertThatThrownBy(() -> compiled.rows().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> compiled.terms().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void normalizedRowsMatchIndependentArithmeticForEveryTinyAssignment() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", -2, 2);
    var y = builder.binaryVar("y");
    builder.addConstraint("le", y.minus(x).times(3).plus(x).le(2));
    builder.addConstraint("ge", x.plus(y.times(2)).ge(-1));
    builder.addConstraint("eq", y.plus(x).eq(1));
    builder.minimize(x.times(3).plus(y.times(-2)).plus(7).times(-2));
    var compiled = MathCompiler.compile(builder.build());

    for (long xv = -2; xv <= 2; xv++) {
      for (long yv = 0; yv <= 1; yv++) {
        long[] expectedActivities = {3 * yv - 2 * xv, xv + 2 * yv, xv + yv, -6 * xv + 4 * yv - 14};
        long[] expectedViolations = {
          Math.max(0, expectedActivities[0] - 2),
          Math.max(0, -1 - expectedActivities[1]),
          Math.abs(expectedActivities[2] - 1),
          0
        };
        for (var row : compiled.rows()) {
          long sum = 0;
          for (var term : compiled.terms()) {
            if (term.row() == row) {
              sum += term.coefficient() * (term.variableId() == x.id() ? xv : yv);
            }
          }
          long activity = row.activity(sum);
          assertThat(activity).isEqualTo(expectedActivities[row.id()]);
          assertThat(row.violation(activity)).isEqualTo(expectedViolations[row.id()]);
          assertThat(row.softImpact(activity))
              .isEqualTo(row.isObjective() ? -expectedActivities[3] : 0);
        }
      }
    }
  }

  @Test
  void membershipIsCheckedBeforeCancellationOrMultiplicationByZero() {
    var foreign = MathModel.builder().binaryVar("foreign");
    var cancelled = MathModel.builder();
    cancelled.addConstraint("hidden", foreign.minus(foreign).le(0));
    assertThatIllegalArgumentException()
        .isThrownBy(cancelled::build)
        .withMessageContaining("hidden")
        .withMessageContaining("foreign");
    var zeroed = MathModel.builder();
    zeroed.minimize(foreign.times(0));
    assertThatIllegalArgumentException()
        .isThrownBy(zeroed::build)
        .withMessageContaining("$objective")
        .withMessageContaining("foreign");
  }

  @Test
  void deepExpressionsAndSharedGraphsUseIterativeIdentityTraversal() {
    var builder = MathModel.builder();
    var x = builder.binaryVar("x");
    LinearExpression deep = x;
    for (int i = 0; i < 30_000; i++) {
      deep = deep.plus(1);
    }
    builder.addConstraint("deep", deep.le(30_001));
    LinearExpression shared = x.minus(x);
    for (int i = 0; i < 300; i++) {
      shared = shared.plus(shared);
    }
    builder.maximize(shared.plus(x));
    var model =
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
            Duration.ofSeconds(10), builder::build);
    var compiled = MathCompiler.compile(model);
    assertThat(compiled.rows().getFirst().constant()).isEqualTo(30_000);
    assertThat(compiled.terms()).extracting(Term::coefficient).containsExactly(1L, 1L);
  }

  @Test
  void unusedAndFixedDeclaredDomainsStillRequireRepresentableCardinality() {
    var unused = MathModel.builder();
    unused.intVar("tooWide", 0, Long.MAX_VALUE);
    assertThatIllegalArgumentException()
        .isThrownBy(unused::build)
        .withMessageContaining("tooWide")
        .withMessageContaining("cardinality");
    var fixed = MathModel.builder();
    var full = fixed.intVar("full", Long.MIN_VALUE, Long.MAX_VALUE);
    fixed.fix(full, 0);
    assertThatIllegalArgumentException().isThrownBy(fixed::build).withMessageContaining("full");
    var supported = MathModel.builder();
    supported.intVar("largest", 1, Long.MAX_VALUE);
    supported.intVar("minimum", Long.MIN_VALUE, Long.MIN_VALUE);
    supported.intVar("maximum", Long.MAX_VALUE, Long.MAX_VALUE);
    assertThat(supported.build().variables()).hasSize(3);
  }

  @Test
  void normalizedCoefficientAndConstantMustFitLong() {
    var coefficient = MathModel.builder();
    var x = coefficient.intVar("x", 0, 0);
    coefficient.maximize(x.times(Long.MAX_VALUE).plus(x));
    assertThatIllegalArgumentException()
        .isThrownBy(coefficient::build)
        .withMessageContaining("x")
        .withMessageContaining("normalized coefficient");
    var constant = MathModel.builder();
    constant.addConstraint("overflow", LinearExpression.constant(Long.MAX_VALUE).plus(1).le(0));
    assertThatIllegalArgumentException()
        .isThrownBy(constant::build)
        .withMessageContaining("overflow")
        .withMessageContaining("normalized constant");
  }

  @Test
  void productsAndReplacementWidthsUseEffectiveFixedBounds() {
    var product = MathModel.builder();
    var x = product.intVar("x", 1, 2);
    product.maximize(x.times(Long.MAX_VALUE));
    assertThatIllegalArgumentException()
        .isThrownBy(product::build)
        .withMessageContaining("x")
        .withMessageContaining("product");
    product.fix(x, 1);
    assertThat(MathCompiler.compile(product.build()).terms()).hasSize(1);

    var replacement = MathModel.builder();
    var y = replacement.intVar("y", -1, 1);
    replacement.maximize(y.times(Long.MAX_VALUE));
    assertThatIllegalArgumentException()
        .isThrownBy(replacement::build)
        .withMessageContaining("y")
        .withMessageContaining("replacement delta");
    replacement.fix(y, 0);
    assertThat(MathCompiler.compile(replacement.build()).terms()).hasSize(1);
  }

  @Test
  void arbitrarySubsetChecksRejectPositiveAndNegativeOverflowDespiteSafeFinalActivity() {
    var positive = MathModel.builder();
    var x = positive.intVar("x", Long.MAX_VALUE, Long.MAX_VALUE);
    var y = positive.intVar("y", Long.MAX_VALUE, Long.MAX_VALUE);
    var z = positive.intVar("z", -Long.MAX_VALUE, -Long.MAX_VALUE);
    positive.maximize(x.plus(y).plus(z));
    assertThatIllegalArgumentException()
        .isThrownBy(positive::build)
        .withMessageContaining("maximum partial aggregate");

    var negative = MathModel.builder();
    var a = negative.intVar("a", Long.MIN_VALUE, Long.MIN_VALUE);
    var b = negative.intVar("b", -1, -1);
    var c = negative.intVar("c", Long.MAX_VALUE, Long.MAX_VALUE);
    negative.maximize(a.plus(b).plus(c));
    assertThatIllegalArgumentException()
        .isThrownBy(negative::build)
        .withMessageContaining("minimum partial aggregate");
  }

  @Test
  void checksActivityResidualViolationAndObjectiveNegationSeparately() {
    var activity = MathModel.builder();
    var x = activity.intVar("x", Long.MAX_VALUE, Long.MAX_VALUE);
    activity.maximize(x.plus(1));
    assertThatIllegalArgumentException()
        .isThrownBy(activity::build)
        .withMessageContaining("activity");

    var residual = MathModel.builder();
    residual.addConstraint("ge", LinearExpression.constant(Long.MAX_VALUE).ge(-1));
    assertThatIllegalArgumentException()
        .isThrownBy(residual::build)
        .withMessageContaining("residual");

    for (boolean equality : List.of(true, false)) {
      var violation = MathModel.builder();
      var minimum = LinearExpression.constant(Long.MIN_VALUE);
      violation.addConstraint("magnitude", equality ? minimum.eq(0) : minimum.ge(0));
      assertThatIllegalArgumentException()
          .isThrownBy(violation::build)
          .withMessageContaining("violation");
    }
    var negation = MathModel.builder().minimize(LinearExpression.constant(Long.MIN_VALUE));
    assertThatIllegalArgumentException()
        .isThrownBy(negation::build)
        .withMessageContaining("negated");

    var supported = MathModel.builder();
    supported.addConstraint("negativeResidual", LinearExpression.constant(Long.MIN_VALUE).le(0));
    supported.maximize(LinearExpression.constant(Long.MIN_VALUE));
    var compiled = MathCompiler.compile(supported.build());
    assertThat(compiled.rows().getFirst().violation(Long.MIN_VALUE)).isZero();
    assertThat(compiled.rows().getLast().softImpact(Long.MIN_VALUE)).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  void exactHardScoreBoundaryAllowsTwoToThe63TotalViolation() {
    var builder = MathModel.builder();
    builder.addConstraint("largest", LinearExpression.constant(Long.MAX_VALUE).eq(0));
    builder.addConstraint("one", LinearExpression.constant(1).eq(0));
    assertThat(MathCompiler.compile(builder.build()).rows()).hasSize(3);

    var overflow = MathModel.builder();
    overflow.addConstraint("largest", LinearExpression.constant(Long.MAX_VALUE).eq(0));
    overflow.addConstraint("two", LinearExpression.constant(2).eq(0));
    assertThatIllegalArgumentException()
        .isThrownBy(overflow::build)
        .withMessageContaining("two")
        .withMessageContaining("sum of maximum hard violations");
  }

  @Test
  void noArbitraryHeadroomOrWholeObjectiveSpanRestriction() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", -3_000_000_000_000_000_000L, 3_000_000_000_000_000_000L);
    var y = builder.intVar("y", -3_000_000_000_000_000_000L, 3_000_000_000_000_000_000L);
    builder.minimize(x.plus(y));
    assertThat(MathCompiler.compile(builder.build()).terms()).hasSize(2);

    var limits = MathModel.builder();
    var a = limits.binaryVar("a");
    var b = limits.binaryVar("b");
    limits.maximize(a.times(Long.MAX_VALUE).minus(b.times(Long.MAX_VALUE)).minus(1));
    limits.addConstraint("largest", LinearExpression.constant(Long.MAX_VALUE).eq(0));
    limits.addConstraint("one", LinearExpression.constant(1).eq(0));
    assertThat(MathCompiler.compile(limits.build()).terms()).hasSize(2);
  }

  @Test
  void normalizedTermsHaveStableVariableDeclarationOrder() {
    var builder = MathModel.builder();
    var first = builder.binaryVar("first");
    var second = builder.binaryVar("second");
    var third = builder.binaryVar("third");
    builder.maximize(third.plus(first).plus(second));
    assertThat(MathCompiler.compile(builder.build()).terms())
        .extracting(Term::variableId)
        .containsExactly(0, 1, 2);
  }
}
