package greycos.solver.jackson.testcotwin;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.jackson.api.score.SimpleScoreJacksonDeserializer;
import greycos.solver.jackson.api.score.SimpleScoreJacksonSerializer;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

@PlanningSolution
public class JacksonTestdataSolution extends JacksonTestdataObject {

  public static SolutionDescriptor<JacksonTestdataSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        JacksonTestdataSolution.class, JacksonTestdataEntity.class);
  }

  private List<JacksonTestdataValue> valueList;
  private List<JacksonTestdataEntity> entityList;

  private SimpleScore score;

  public JacksonTestdataSolution() {}

  public JacksonTestdataSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<JacksonTestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<JacksonTestdataValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<JacksonTestdataEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<JacksonTestdataEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  @JsonSerialize(using = SimpleScoreJacksonSerializer.class)
  @JsonDeserialize(using = SimpleScoreJacksonDeserializer.class)
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
