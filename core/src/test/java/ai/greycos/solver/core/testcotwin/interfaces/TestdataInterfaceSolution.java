package ai.greycos.solver.core.testcotwin.interfaces;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public interface TestdataInterfaceSolution {
  static SolutionDescriptor<TestdataInterfaceSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataInterfaceSolution.class, TestdataInterfaceEntity.class);
  }

  static TestdataInterfaceSolution generateSolution() {
    var out =
        new TestdataInterfaceSolution() {
          private List<TestdataInterfaceEntity> entityList;
          private List<TestdataInterfaceValue> valueList;
          private SimpleScore score;

          @Override
          public List<TestdataInterfaceEntity> getEntityList() {
            return entityList;
          }

          @Override
          public void setEntityList(List<TestdataInterfaceEntity> entityList) {
            this.entityList = entityList;
          }

          @Override
          public List<TestdataInterfaceValue> getValueList() {
            return valueList;
          }

          @Override
          public void setValueList(List<TestdataInterfaceValue> valueList) {
            this.valueList = valueList;
          }

          @Override
          public SimpleScore getScore() {
            return score;
          }

          @Override
          public void setScore(SimpleScore score) {
            this.score = score;
          }
        };

    out.setEntityList(
        List.of(
            new TestdataInterfaceEntity() {
              private TestdataInterfaceValue value;

              @Override
              public String getId() {
                return "entity";
              }

              @Override
              public TestdataInterfaceValue getValue() {
                return value;
              }

              @Override
              public void setValue(TestdataInterfaceValue value) {
                this.value = value;
              }
            }));

    out.setValueList(List.of(new TestdataInterfaceValue() {}));

    return out;
  }

  @PlanningEntityCollectionProperty
  List<TestdataInterfaceEntity> getEntityList();

  void setEntityList(List<TestdataInterfaceEntity> entityList);

  @ValueRangeProvider
  List<TestdataInterfaceValue> getValueList();

  void setValueList(List<TestdataInterfaceValue> valueList);

  @PlanningScore
  SimpleScore getScore();

  void setScore(SimpleScore score);
}
