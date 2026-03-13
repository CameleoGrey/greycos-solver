package ai.greycos.solver.core.config.solver.testutil.corruptedundoshadow;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;

@PlanningSolution
public class CorruptedUndoShadowSolution {
  @PlanningEntityCollectionProperty List<CorruptedUndoShadowEntity> entityList;

  @PlanningEntityCollectionProperty @ValueRangeProvider List<CorruptedUndoShadowValue> valueList;

  @PlanningScore SimpleScore score;

  public CorruptedUndoShadowSolution() {}

  public CorruptedUndoShadowSolution(
      List<CorruptedUndoShadowEntity> entityList, List<CorruptedUndoShadowValue> valueList) {
    this.entityList = entityList;
    this.valueList = valueList;
  }

  public List<CorruptedUndoShadowEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<CorruptedUndoShadowEntity> entityList) {
    this.entityList = entityList;
  }

  public List<CorruptedUndoShadowValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<CorruptedUndoShadowValue> valueList) {
    this.valueList = valueList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
