package greycos.solver.core.impl.score.stream.common.quad;

import java.util.Collection;
import java.util.Objects;

import greycos.solver.core.api.function.PentaFunction;
import greycos.solver.core.api.function.QuadFunction;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.quad.QuadConstraintBuilder;
import greycos.solver.core.impl.score.stream.common.AbstractConstraintBuilder;
import greycos.solver.core.impl.score.stream.common.ScoreImpactType;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class QuadConstraintBuilderImpl<A, B, C, D, Score_ extends Score<Score_>>
    extends AbstractConstraintBuilder<Score_> implements QuadConstraintBuilder<A, B, C, D, Score_> {

  private @Nullable PentaFunction<A, B, C, D, Score_, ConstraintJustification> justificationMapping;

  private @Nullable QuadFunction<A, B, C, D, ? extends Collection<?>> indictmentMapping;

  public QuadConstraintBuilderImpl(
      QuadConstraintConstructor<A, B, C, D, Score_> constraintConstructor,
      ScoreImpactType impactType,
      Score_ constraintWeight) {
    super(constraintConstructor, impactType, constraintWeight);
  }

  @Override
  protected @Nullable PentaFunction<A, B, C, D, Score_, ConstraintJustification>
      getJustificationMapping() {
    return justificationMapping;
  }

  @Override
  public <ConstraintJustification_ extends ConstraintJustification>
      QuadConstraintBuilder<A, B, C, D, Score_> justifyWith(
          PentaFunction<A, B, C, D, Score_, ConstraintJustification_> justificationMapping) {
    if (this.justificationMapping != null) {
      throw new IllegalStateException(
          """
          Justification mapping already set (%s).
          Maybe the constraint calls justifyWith() twice?\
          """
              .formatted(justificationMapping));
    }
    this.justificationMapping =
        (PentaFunction<A, B, C, D, Score_, ConstraintJustification>)
            Objects.requireNonNull(justificationMapping);
    return this;
  }

  @Override
  protected @Nullable Object getIndictmentMapping() {
    return indictmentMapping;
  }

  @Override
  public QuadConstraintBuilder<A, B, C, D, Score_> indictWith(
      QuadFunction<A, B, C, D, ? extends Collection<?>> indictmentMapping) {
    Objects.requireNonNull(indictmentMapping, "The indictment mapping must not be null.");
    if (this.indictmentMapping != null) {
      throw new IllegalStateException(
          "Indictment mapping already set; indictWith() may only be called once.");
    }
    this.indictmentMapping = indictmentMapping;
    return this;
  }
}
