package ai.greycos.solver.core.testcotwin.shadow.wronglistener;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.VariableListener;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;

import org.jspecify.annotations.NonNull;

@PlanningEntity
public class TestdataWrongListShadowEntity {

  public static EntityDescriptor<TestdataListSolution> buildEntityDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
            TestdataListSolution.class,
            TestdataListEntity.class,
            TestdataWrongListShadowEntity.class,
            TestdataListValue.class)
        .findEntityDescriptorOrFail(TestdataWrongListShadowEntity.class);
  }

  @ShadowVariable(
      variableListenerClass = MyBasicVariableListener.class,
      sourceEntityClass = TestdataListEntity.class,
      sourceVariableName = "valueList")
  private String shadow;

  public String getShadow() {
    return shadow;
  }

  public void setShadow(String shadow) {
    this.shadow = shadow;
  }

  public static class MyBasicVariableListener
      implements VariableListener<TestdataListSolution, TestdataListEntity> {

    @Override
    public void beforeEntityAdded(
        @NonNull ScoreDirector<TestdataListSolution> scoreDirector,
        @NonNull TestdataListEntity entity) {
      // Ignore
    }

    @Override
    public void afterEntityAdded(
        @NonNull ScoreDirector<TestdataListSolution> scoreDirector,
        @NonNull TestdataListEntity entity) {
      // Ignore
    }

    @Override
    public void beforeEntityRemoved(
        @NonNull ScoreDirector<TestdataListSolution> scoreDirector,
        @NonNull TestdataListEntity entity) {
      // Ignore
    }

    @Override
    public void afterEntityRemoved(
        @NonNull ScoreDirector<TestdataListSolution> scoreDirector,
        @NonNull TestdataListEntity entity) {
      // Ignore
    }

    @Override
    public void beforeVariableChanged(
        @NonNull ScoreDirector<TestdataListSolution> scoreDirector,
        @NonNull TestdataListEntity entity) {
      // Ignore
    }

    @Override
    public void afterVariableChanged(
        @NonNull ScoreDirector<TestdataListSolution> scoreDirector,
        @NonNull TestdataListEntity entity) {
      // Ignore
    }
  }
}
