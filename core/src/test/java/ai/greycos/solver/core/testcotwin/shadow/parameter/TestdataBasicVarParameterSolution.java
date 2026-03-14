package ai.greycos.solver.core.testcotwin.shadow.parameter;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataBasicVarParameterSolution {

  public static SolutionDescriptor<TestdataBasicVarParameterSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataBasicVarParameterSolution.class,
        TestdataBasicVarParameterEntity.class,
        TestdataBasicVarParameterValue.class);
  }

  @PlanningEntityCollectionProperty List<TestdataBasicVarParameterEntity> entities;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataBasicVarParameterValue> values;

  @ProblemFactCollectionProperty List<Object> problemFacts;

  @PlanningScore HardSoftScore score;

  public TestdataBasicVarParameterSolution() {}

  public TestdataBasicVarParameterSolution(
      List<TestdataBasicVarParameterEntity> entities,
      List<TestdataBasicVarParameterValue> values,
      List<Object> problemFacts) {
    this.values = values;
    this.entities = entities;
    this.problemFacts = problemFacts;
  }

  public List<TestdataBasicVarParameterValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataBasicVarParameterValue> values) {
    this.values = values;
  }

  public List<TestdataBasicVarParameterEntity> getEntities() {
    return entities;
  }

  public void setEntities(List<TestdataBasicVarParameterEntity> entities) {
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
    return "TestdataBasicVarParameterSolution{"
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
