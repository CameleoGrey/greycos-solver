package greycos.solver.math.examples;

import java.util.List;

import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.api.MathSolver;
import greycos.solver.math.config.SolveOptions;

/** A feasible assignment whose improving exchange requires more than one scalar change. */
public final class AssignmentExample {

  private AssignmentExample() {}

  public static MathModel model() {
    // tag::model[]
    var builder = MathModel.builder();
    var adaA = builder.binaryVar("ada-a");
    var adaB = builder.binaryVar("ada-b");
    var benA = builder.binaryVar("ben-a");
    var benB = builder.binaryVar("ben-b");

    builder.addConstraint("ada", adaA.plus(adaB).eq(1));
    builder.addConstraint("ben", benA.plus(benB).eq(1));
    builder.addConstraint("task-a", adaA.plus(benA).eq(1));
    builder.addConstraint("task-b", adaB.plus(benB).eq(1));
    builder.minimize(adaA.times(9).plus(adaB).plus(benA.times(2)).plus(benB.times(8)));
    builder.initialValue(adaA, 1);
    builder.initialValue(adaB, 0);
    builder.initialValue(benA, 0);
    builder.initialValue(benB, 1);
    var model = builder.build();
    // end::model[]
    return model;
  }

  public static SolveOptions options() {
    // tag::expert[]
    var options =
        SolveOptions.moveEvaluationLimit(1_000)
            .withSeed(7)
            .withAcceptorConfig(
                new LocalSearchAcceptorConfig()
                    .withAcceptorTypeList(List.of(AcceptorType.HILL_CLIMBING)))
            .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1));
    // end::expert[]
    return options;
  }

  public static void main(String[] args) {
    var result = MathSolver.solve(model(), options());
    System.out.printf(
        "%s, objective=%d, termination=%s%n",
        result.status(), result.objectiveValue(), result.termination());
    result
        .values()
        .forEach((variable, value) -> System.out.printf("%s=%d%n", variable.name(), value));
    result.constraintReports().forEach(System.out::println);
    System.out.println(
        "Every binary flip violates two equalities; the reported cost is the best incumbent within the limit.");
  }
}
