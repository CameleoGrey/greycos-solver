package greycos.solver.math.examples;

import greycos.solver.math.api.MathModel;
import greycos.solver.math.api.MathSolver;
import greycos.solver.math.config.SolveOptions;

/** An annotation-free binary knapsack with a seven-unit weight limit. */
public final class KnapsackExample {

  private KnapsackExample() {}

  public static MathModel model() {
    // tag::model[]
    var builder = MathModel.builder();
    var camera = builder.binaryVar("camera");
    var food = builder.binaryVar("food");
    var jacket = builder.binaryVar("jacket");
    var book = builder.binaryVar("book");

    builder.addConstraint(
        "weight", camera.times(4).plus(food.times(3)).plus(jacket.times(2)).plus(book).le(7));
    builder.maximize(camera.times(7).plus(food.times(5)).plus(jacket.times(4)).plus(book));
    builder.initialValue(camera, 0);
    builder.initialValue(food, 0);
    builder.initialValue(jacket, 0);
    builder.initialValue(book, 0);
    var model = builder.build();
    // end::model[]
    return model;
  }

  public static void main(String[] args) {
    var model = model();
    // tag::solve[]
    var options = SolveOptions.moveEvaluationLimit(5_000).withSeed(7);
    var result = MathSolver.solve(model, options);
    System.out.printf(
        "%s, objective=%d, termination=%s%n",
        result.status(), result.objectiveValue(), result.termination());
    result
        .values()
        .forEach((variable, value) -> System.out.printf("%s=%d%n", variable.name(), value));
    result.constraintReports().forEach(System.out::println);
    // end::solve[]
  }
}
