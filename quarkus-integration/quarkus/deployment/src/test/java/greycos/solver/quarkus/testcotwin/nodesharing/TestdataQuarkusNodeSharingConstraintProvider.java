package greycos.solver.quarkus.testcotwin.nodesharing;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;

public class TestdataQuarkusNodeSharingConstraintProvider implements ConstraintProvider {

  public static String blockedValue = "initial";
  public static int predicateCalls;

  private final SimpleScore secondWeight;

  public TestdataQuarkusNodeSharingConstraintProvider() {
    secondWeight = SimpleScore.of(2);
  }

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
    return new Constraint[] {first(factory), second(factory)};
  }

  private Constraint first(ConstraintFactory factory) {
    return factory
        .forEach(TestdataQuarkusEntity.class)
        .filter(entity -> isBlocked(entity.getValue()))
        .penalize(SimpleScore.ONE)
        .asConstraint("First blocked value");
  }

  private Constraint second(ConstraintFactory factory) {
    return factory
        .forEach(TestdataQuarkusEntity.class)
        .filter(entity -> isBlocked(entity.getValue()))
        .penalize(secondWeight)
        .asConstraint("Second blocked value");
  }

  private static boolean isBlocked(String value) {
    predicateCalls++;
    return blockedValue.equals(value);
  }
}
