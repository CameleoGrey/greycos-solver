package greycos.solver.core.impl.score.stream.common.bi;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BiFunction;

import greycos.solver.core.api.function.TriFunction;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.bi.BiConstraintBuilder;
import greycos.solver.core.impl.score.stream.common.AbstractConstraintBuilder;
import greycos.solver.core.impl.score.stream.common.ScoreImpactType;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class BiConstraintBuilderImpl<A, B, Score_ extends Score<Score_>>
    extends AbstractConstraintBuilder<Score_> implements BiConstraintBuilder<A, B, Score_> {

  private @Nullable TriFunction<A, B, Score_, ConstraintJustification> justificationMapping;

  private @Nullable BiFunction<A, B, ? extends Collection<?>> indictmentMapping;

  public BiConstraintBuilderImpl(
      BiConstraintConstructor<A, B, Score_> constraintConstructor,
      ScoreImpactType impactType,
      Score_ constraintWeight) {
    super(constraintConstructor, impactType, constraintWeight);
  }

  @Override
  protected @Nullable TriFunction<A, B, Score_, ConstraintJustification> getJustificationMapping() {
    return justificationMapping;
  }

  @Override
  public <ConstraintJustification_ extends ConstraintJustification>
      BiConstraintBuilder<A, B, Score_> justifyWith(
          TriFunction<A, B, Score_, ConstraintJustification_> justificationMapping) {
    if (this.justificationMapping != null) {
      throw new IllegalStateException(
          """
          Justification mapping already set (%s).
          Maybe the constraint calls justifyWith() twice?\
          """
              .formatted(justificationMapping));
    }
    this.justificationMapping =
        (TriFunction<A, B, Score_, ConstraintJustification>)
            Objects.requireNonNull(justificationMapping);
    return this;
  }

  @Override
  protected @Nullable Object getIndictmentMapping() {
    return indictmentMapping;
  }

  @Override
  public BiConstraintBuilder<A, B, Score_> indictWith(
      BiFunction<A, B, ? extends Collection<?>> indictmentMapping) {
    Objects.requireNonNull(indictmentMapping, "The indictment mapping must not be null.");
    if (this.indictmentMapping != null) {
      throw new IllegalStateException(
          "Indictment mapping already set; indictWith() may only be called once.");
    }
    this.indictmentMapping = indictmentMapping;
    return this;
  }
}
