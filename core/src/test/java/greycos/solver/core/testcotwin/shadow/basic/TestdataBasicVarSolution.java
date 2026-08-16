package greycos.solver.core.testcotwin.shadow.basic;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataBasicVarSolution {

  public static SolutionDescriptor<TestdataBasicVarSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataBasicVarSolution.class, TestdataBasicVarEntity.class, TestdataBasicVarValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataBasicVarEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataBasicVarValue> values;

  @ProblemFactCollectionProperty List<Object> problemFacts;

  @PlanningScore HardSoftScore score;

  public TestdataBasicVarSolution() {}

  public TestdataBasicVarSolution(
      List<TestdataBasicVarEntity> entities,
      List<TestdataBasicVarValue> values,
      List<Object> problemFacts) {
    this.values = values;
    this.entities = entities;
    this.problemFacts = problemFacts;
  }

  public List<TestdataBasicVarValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataBasicVarValue> values) {
    this.values = values;
  }

  public List<TestdataBasicVarEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataBasicVarEntity> entities) {
    this.entities = entities;
  }

  public List<Object> getProblemFacts() {
    return problemFacts;
  }

  public void setProblemFacts(List<Object> problemFacts) {
    this.problemFacts = problemFacts;
  }

  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }

  @Override
  public String toString() {
    return "TestdataBasicVarSolution{"
        + "entities="
        + entities
        + ", values="
        + values
        + ", problemFacts="
        + problemFacts
        + ", score="
        + score
        + '}';
  }
}
