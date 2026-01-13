package ai.greycos.solver.core.testcotwin.clone.cloneable;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.greycos.solver.core.impl.cotwin.solution.cloner.PlanningCloneable;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class PlanningCloneableSolution implements PlanningCloneable<PlanningCloneableSolution> {
  public static SolutionDescriptor<PlanningCloneableSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        PlanningCloneableSolution.class, PlanningCloneableEntity.class);
  }

  @PlanningEntityCollectionProperty
  public PlanningCloneableList<PlanningCloneableEntity> entityList;

  @ValueRangeProvider public PlanningCloneableList<TestdataValue> valueList;
  public PlanningCloneableMap<String, PlanningCloneableEntity> codeToEntity;

  @PlanningScore public HardSoftScore score;

  public PlanningCloneableSolution(List<PlanningCloneableEntity> entityList) {
    this.entityList = new PlanningCloneableList<>();
    this.valueList = new PlanningCloneableList<>();
    this.codeToEntity = new PlanningCloneableMap<>();
    this.score = null;
    for (PlanningCloneableEntity entity : entityList) {
      this.entityList.add(entity);
      this.codeToEntity.put(entity.getCode(), entity);
    }
  }

  @Override
  public PlanningCloneableSolution createNewInstance() {
    return new PlanningCloneableSolution(List.of());
  }
}
