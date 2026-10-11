package greycos.solver.math.impl.score;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.ToLongBiFunction;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.math.impl.cotwin.MathDecision;
import greycos.solver.math.impl.model.Row;
import greycos.solver.math.impl.model.RowKind;
import greycos.solver.math.impl.model.Term;

import org.jspecify.annotations.NullMarked;

/** Fixed public-stream graph for normalized linear models. */
@NullMarked
public final class MathConstraintProvider implements ConstraintProvider {

  private final ToLongBiFunction<Term, MathDecision> contribution;
  private final BiFunction<Row, Long, Long> activity;

  public MathConstraintProvider() {
    this(
        (term, decision) -> Math.multiplyExact(term.coefficient(), decision.getValue()),
        (row, sum) -> row.activity(sum));
  }

  // The same graph can be instrumented per factory in package-local tests.
  MathConstraintProvider(
      ToLongBiFunction<Term, MathDecision> contribution, BiFunction<Row, Long, Long> activity) {
    this.contribution = Objects.requireNonNull(contribution);
    this.activity = Objects.requireNonNull(activity);
  }

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
    var activities =
        factory
            .forEach(Term.class)
            .join(MathDecision.class, Joiners.equal(Term::variableId, MathDecision::getId))
            .groupBy((term, decision) -> term.row(), ConstraintCollectors.sum(contribution))
            .complement(Row.class, row -> 0L)
            .map((row, sum) -> row, activity);
    return new Constraint[] {
      activities
          .filter((row, value) -> row.kind() == RowKind.LE)
          .penalize(HardSoftScore.ONE_HARD, (row, value) -> row.violation(value))
          .justifyWith(MathConstraintProvider::justify)
          .asConstraint("mathLessThanOrEqual"),
      activities
          .filter((row, value) -> row.kind() == RowKind.GE)
          .penalize(HardSoftScore.ONE_HARD, (row, value) -> row.violation(value))
          .justifyWith(MathConstraintProvider::justify)
          .asConstraint("mathGreaterThanOrEqual"),
      activities
          .filter((row, value) -> row.kind() == RowKind.EQ)
          .penalize(HardSoftScore.ONE_HARD, (row, value) -> row.violation(value))
          .justifyWith(MathConstraintProvider::justify)
          .asConstraint("mathEqual"),
      activities
          .filter((row, value) -> row.isObjective())
          .impact(HardSoftScore.ONE_SOFT, (row, value) -> row.softImpact(value))
          .justifyWith(MathConstraintProvider::justify)
          .asConstraint("mathObjective")
    };
  }

  private static MathRowJustification justify(Row row, Long activity, HardSoftScore score) {
    return new MathRowJustification(
        row.id(),
        row.name(),
        activity,
        row.bound(),
        row.isObjective() ? activity : row.residual(activity),
        row.isObjective() ? 0 : row.violation(activity));
  }
}
