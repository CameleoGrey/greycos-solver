package greycos.solver.core.testcotwin.multiplelist;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolverFactory;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;

@PlanningSolution
public class TestdataMultipleListSolution {

  @PlanningEntityCollectionProperty
  private List<TestdataMultipleListEntity> entityList = new ArrayList<>();

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "firstValueRange")
  private List<TestdataMultipleListFirstValue> firstValueList = new ArrayList<>();

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "secondValueRange")
  private List<TestdataMultipleListSecondValue> secondValueList = new ArrayList<>();

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "basicValueRange")
  private List<Integer> basicValueRange = List.of(0, 1, 2, 3);

  @PlanningScore private SimpleScore score;

  public static SolutionDescriptor<TestdataMultipleListSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataMultipleListSolution.class,
        TestdataMultipleListEntity.class,
        TestdataMultipleListFirstValue.class,
        TestdataMultipleListSecondValue.class);
  }

  public static PlanningSolutionMetaModel<TestdataMultipleListSolution> buildMetaModel() {
    return buildSolutionDescriptor().getMetaModel();
  }

  public static InnerScoreDirector<TestdataMultipleListSolution, SimpleScore> buildScoreDirector() {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataMultipleListSolution.class)
            .withEntityClasses(
                TestdataMultipleListEntity.class,
                TestdataMultipleListFirstValue.class,
                TestdataMultipleListSecondValue.class)
            .withEasyScoreCalculatorClass(TestdataMultipleListEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT);
    return new DefaultSolverFactory<TestdataMultipleListSolution>(config)
        .<SimpleScore>getScoreDirectorFactory()
        .createScoreDirectorBuilder()
        .withLookUpEnabled(true)
        .build();
  }

  /** Parameter order follows the existing single-list fixture: populations, then owners. */
  public static TestdataMultipleListSolution generateUninitializedSolution(
      int firstValueCount, int secondValueCount, int entityCount) {
    if (firstValueCount < 0 || secondValueCount < 0 || entityCount < 0) {
      throw new IllegalArgumentException("Fixture population sizes must be nonnegative.");
    }
    var solution = new TestdataMultipleListSolution();
    for (int i = 0; i < entityCount; i++) {
      solution.entityList.add(new TestdataMultipleListEntity("entity-" + i));
    }
    for (int i = 0; i < firstValueCount; i++) {
      solution.firstValueList.add(new TestdataMultipleListFirstValue("first-" + i));
    }
    for (int i = 0; i < secondValueCount; i++) {
      solution.secondValueList.add(new TestdataMultipleListSecondValue("second-" + i));
    }
    return solution;
  }

  public static TestdataMultipleListSolution generateInitializedSolution(
      int firstValueCount, int secondValueCount, int entityCount) {
    return generateUninitializedSolution(firstValueCount, secondValueCount, entityCount)
        .initialize();
  }

  public TestdataMultipleListSolution initialize() {
    if (entityList.isEmpty() && (!firstValueList.isEmpty() || !secondValueList.isEmpty())) {
      throw new IllegalStateException(
          "Cannot initialize nonempty value populations without owners.");
    }
    if (basicValueRange.isEmpty() && (!entityList.isEmpty() || !secondValueList.isEmpty())) {
      throw new IllegalStateException("Cannot initialize basic variables from an empty range.");
    }
    for (var entity : entityList) {
      entity.getFirstValueList().clear();
      entity.getSecondValueList().clear();
      entity.setBasicValue(basicValueRange.getFirst());
    }
    for (int i = 0; i < firstValueList.size(); i++) {
      entityList.get(i % entityList.size()).getFirstValueList().add(firstValueList.get(i));
    }
    for (int i = 0; i < secondValueList.size(); i++) {
      var value = secondValueList.get(i);
      value.setQuantity(basicValueRange.getFirst());
      entityList.get(i % entityList.size()).getSecondValueList().add(value);
    }
    SolutionManager.updateShadowVariables(this);
    return this;
  }

  public List<TestdataMultipleListEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataMultipleListEntity> entityList) {
    this.entityList = entityList;
  }

  public List<TestdataMultipleListFirstValue> getFirstValueList() {
    return firstValueList;
  }

  public void setFirstValueList(List<TestdataMultipleListFirstValue> firstValueList) {
    this.firstValueList = firstValueList;
  }

  public List<TestdataMultipleListSecondValue> getSecondValueList() {
    return secondValueList;
  }

  public void setSecondValueList(List<TestdataMultipleListSecondValue> secondValueList) {
    this.secondValueList = secondValueList;
  }

  public List<Integer> getBasicValueRange() {
    return basicValueRange;
  }

  public void setBasicValueRange(List<Integer> basicValueRange) {
    this.basicValueRange = basicValueRange;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
