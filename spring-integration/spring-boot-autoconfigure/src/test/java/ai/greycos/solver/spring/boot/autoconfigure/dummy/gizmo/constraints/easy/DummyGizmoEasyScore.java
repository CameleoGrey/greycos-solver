package ai.greycos.solver.spring.boot.autoconfigure.dummy.gizmo.constraints.easy;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import ai.greycos.solver.spring.boot.autoconfigure.gizmo.cotwin.TestdataGizmoSpringSolution;

import org.jspecify.annotations.NonNull;

public class DummyGizmoEasyScore
    implements EasyScoreCalculator<TestdataGizmoSpringSolution, SimpleScore> {

  @Override
  public @NonNull SimpleScore calculateScore(@NonNull TestdataGizmoSpringSolution solution) {
    return null;
  }
}
