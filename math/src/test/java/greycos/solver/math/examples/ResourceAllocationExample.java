package greycos.solver.math.examples;

import java.time.Duration;

import greycos.solver.math.api.MathModel;
import greycos.solver.math.api.MathSolver;
import greycos.solver.math.config.SolveOptions;

/** Bounded integer production with wood, labor and a fixed resource reservation. */
public final class ResourceAllocationExample {

  private ResourceAllocationExample() {}

  public static MathModel model() {
    // tag::model[]
    var builder = MathModel.builder();
    var tables = builder.intVar("tables", 0, 3);
    var chairs = builder.intVar("chairs", 0, 4);
    var reserved = builder.intVar("reserved", 0, 2);

    builder.addConstraint("wood", tables.times(4).plus(chairs.times(2)).plus(reserved).le(13));
    builder.addConstraint("labor", tables.times(3).plus(chairs).le(8));
    builder.maximize(tables.times(9).plus(chairs.times(4)));
    builder.initialValue(tables, 0);
    builder.initialValue(chairs, 0);
    builder.fix(reserved, 1);
    var model = builder.build();
    // end::model[]
    return model;
  }

  public static void main(String[] args) {
    var model = model();
    // tag::limits[]
    var options =
        SolveOptions.moveEvaluationLimit(5_000).withTimeLimit(Duration.ofSeconds(2)).withSeed(7);
    var result = MathSolver.solve(model, options);
    // end::limits[]
    System.out.printf(
        "%s, objective=%d, termination=%s%n",
        result.status(), result.objectiveValue(), result.termination());
    result
        .values()
        .forEach((variable, value) -> System.out.printf("%s=%d%n", variable.name(), value));
    result.constraintReports().forEach(System.out::println);
  }
}
