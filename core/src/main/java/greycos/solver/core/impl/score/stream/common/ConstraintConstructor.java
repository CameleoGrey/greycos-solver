package greycos.solver.core.impl.score.stream.common;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintMetadata;

@FunctionalInterface
public interface ConstraintConstructor<Score_ extends Score<Score_>, JustificationMapping_> {

  Constraint apply(
      ConstraintMetadata description,
      Score_ constraintWeight,
      ScoreImpactType impactType,
      JustificationMapping_ justificationMapping);
}
