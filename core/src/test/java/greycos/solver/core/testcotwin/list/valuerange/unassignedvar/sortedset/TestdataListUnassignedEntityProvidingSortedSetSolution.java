package greycos.solver.core.testcotwin.list.valuerange.unassignedvar.sortedset;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataListUnassignedEntityProvidingSortedSetSolution {

  public static SolutionDescriptor<TestdataListUnassignedEntityProvidingSortedSetSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataListUnassignedEntityProvidingSortedSetSolution.class,
        TestdataListUnassignedEntityProvidingSortedSetEntity.class);
  }

  public static PlanningSolutionMetaModel<TestdataListUnassignedEntityProvidingSortedSetSolution>
      buildMetaModel() {
    return buildSolutionDescriptor().getMetaModel();
  }

  public static TestdataListUnassignedEntityProvidingSortedSetSolution generateSolution() {
    var solution = new TestdataListUnassignedEntityProvidingSortedSetSolution();
    var value1 = new TestdataValue("v1");
    var value2 = new TestdataValue("v2");
    var value3 = new TestdataValue("v3");
    var entity1 =
        new TestdataListUnassignedEntityProvidingSortedSetEntity("e1", List.of(value1, value2));
    var entity2 =
        new TestdataListUnassignedEntityProvidingSortedSetEntity("e2", List.of(value1, value3));
    solution.setEntityList(List.of(entity1, entity2));
    return solution;
  }

  private List<TestdataListUnassignedEntityProvidingSortedSetEntity> entityList;

  private SimpleScore score;

  @PlanningEntityCollectionProperty
  public List<TestdataListUnassignedEntityProvidingSortedSetEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataListUnassignedEntityProvidingSortedSetEntity> entityList) {
    this.entityList = entityList;
  }

  @ProblemFactCollectionProperty
  public List<TestdataValue> getValueList() {
    return entityList.stream()
        .flatMap(entity -> entity.getValueRange().stream())
        .distinct()
        .toList();
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
