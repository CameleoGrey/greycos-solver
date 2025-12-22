package ai.greycos.solver.core.impl.score.stream.common;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.stream.Constraint;

@FunctionalInterface
public interface ConstraintConstructor<
    Score_ extends Score<Score_>, JustificationMapping_, IndictedObjectsMapping_> {

  Constraint apply(
      String constraintPackage,
      String constraintName,
      String constraintDescription,
      String constraintGroup,
      Score_ constraintWeight,
      ScoreImpactType impactType,
      JustificationMapping_ justificationMapping,
      IndictedObjectsMapping_ indictedObjectsMapping);
}
