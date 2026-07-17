package ai.greycos.solver.core.api.score.stream.quad;

import java.util.Collection;
import java.util.List;

import ai.greycos.solver.core.api.function.PentaFunction;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintBuilder;
import ai.greycos.solver.core.api.score.stream.ConstraintJustification;

import org.jspecify.annotations.NullMarked;

/**
 * Used to build a {@link Constraint} out of a {@link QuadConstraintStream}, applying optional
 * configuration. To build the constraint, use one of the terminal operations, such as {@link
 * #asConstraint(String)}.
 *
 * <p>Unless {@link #justifyWith(PentaFunction)} is called, the default justification mapping will
 * be used. The function takes the input arguments and converts them into a {@link List}.
 */
@NullMarked
public interface QuadConstraintBuilder<A, B, C, D, Score_ extends Score<Score_>>
    extends ConstraintBuilder {

  /**
   * Sets a custom function to apply on a constraint match to justify it. That function must not
   * return a {@link Collection}, else {@link IllegalStateException} will be thrown during score
   * calculation.
   *
   * @return this
   */
  <ConstraintJustification_ extends ConstraintJustification>
      QuadConstraintBuilder<A, B, C, D, Score_> justifyWith(
          PentaFunction<A, B, C, D, Score_, ConstraintJustification_> justificationMapping);
}
