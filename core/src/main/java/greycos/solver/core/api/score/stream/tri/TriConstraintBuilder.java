package greycos.solver.core.api.score.stream.tri;

import java.util.Collection;
import java.util.List;

import greycos.solver.core.api.function.QuadFunction;
import greycos.solver.core.api.function.TriFunction;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintBuilder;
import greycos.solver.core.api.score.stream.ConstraintJustification;

import org.jspecify.annotations.NullMarked;

/**
 * Used to build a {@link Constraint} out of a {@link TriConstraintStream}, applying optional
 * configuration. To build the constraint, use one of the terminal operations, such as {@link
 * #asConstraint(String)}.
 *
 * <p>Unless {@link #justifyWith(QuadFunction)} is called, the default justification mapping will be
 * used. The function takes the input arguments and converts them into a {@link List}.
 */
@NullMarked
public interface TriConstraintBuilder<A, B, C, Score_ extends Score<Score_>>
    extends ConstraintBuilder {

  /**
   * Sets a custom function to apply on a constraint match to justify it. That function must not
   * return a {@link Collection}, else {@link IllegalStateException} will be thrown during score
   * calculation.
   *
   * @return this
   */
  <ConstraintJustification_ extends ConstraintJustification>
      TriConstraintBuilder<A, B, C, Score_> justifyWith(
          QuadFunction<A, B, C, Score_, ConstraintJustification_> justificationMapping);

  /**
   * Selects the terminal objects indicted by each match, replacing the default non-null tuple
   * objects. An empty collection is valid; the mapping, its result and its elements must not be
   * null. Equal objects are counted once per match. The function is called lazily only when
   * terminal indictments are requested; it does not change justifications or original objects.
   *
   * @return this
   */
  default TriConstraintBuilder<A, B, C, Score_> indictWith(
      TriFunction<A, B, C, ? extends Collection<?>> indictmentMapping) {
    java.util.Objects.requireNonNull(indictmentMapping, "The indictment mapping must not be null.");
    throw new UnsupportedOperationException(
        "This constraint builder does not support indictWith().");
  }
}
