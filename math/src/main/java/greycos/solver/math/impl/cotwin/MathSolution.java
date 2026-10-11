package greycos.solver.math.impl.cotwin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.impl.model.CompiledModel;
import greycos.solver.math.impl.model.Row;
import greycos.solver.math.impl.model.Term;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Internal planning solution for one immutable mathematical model. */
@PlanningSolution(solutionCloner = MathSolutionCloner.class)
@NullMarked
public final class MathSolution {

  private final CompiledModel compiledModel;
  private final List<MathDecision> decisions;
  private @Nullable HardSoftScore score;

  MathSolution(
      CompiledModel compiledModel, List<MathDecision> decisions, @Nullable HardSoftScore score) {
    this.compiledModel = compiledModel;
    this.decisions = decisions;
    this.score = score;
  }

  public static MathSolution initialize(CompiledModel compiledModel, long seed) {
    var model = compiledModel.model();
    var random = new Random(seed);
    var decisions = new ArrayList<MathDecision>(model.variables().size());
    for (var variable : model.variables()) {
      var range = new InclusiveLongValueRange(variable.lowerBound(), variable.upperBound());
      var fixed = model.fixedValue(variable);
      var initial = model.initialValue(variable);
      long value;
      if (fixed.isPresent()) {
        value = fixed.getAsLong();
      } else if (initial.isPresent()) {
        value = initial.getAsLong();
      } else if (range.getSize() == 1) {
        value = range.get(0);
      } else {
        value = range.get(random.nextLong(range.getSize()));
      }
      decisions.add(
          new MathDecision(variable.id(), range, value, fixed.isPresent() || range.getSize() == 1));
    }
    return new MathSolution(compiledModel, decisions, null);
  }

  public MathModel getModel() {
    return compiledModel.model();
  }

  @PlanningEntityCollectionProperty
  public List<MathDecision> getDecisions() {
    return decisions;
  }

  @ProblemFactCollectionProperty
  public List<Row> getRows() {
    return compiledModel.rows();
  }

  @ProblemFactCollectionProperty
  public List<Term> getTerms() {
    return compiledModel.terms();
  }

  @PlanningScore
  public @Nullable HardSoftScore getScore() {
    return score;
  }

  public void setScore(@Nullable HardSoftScore score) {
    this.score = score;
  }

  CompiledModel compiledModel() {
    return compiledModel;
  }
}
