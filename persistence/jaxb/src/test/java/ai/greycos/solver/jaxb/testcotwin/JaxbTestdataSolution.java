package ai.greycos.solver.jaxb.testcotwin;

import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.jaxb.api.score.SimpleScoreJaxbAdapter;

@PlanningSolution
@XmlRootElement
public class JaxbTestdataSolution extends JaxbTestdataObject {

  public static SolutionDescriptor<JaxbTestdataSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        JaxbTestdataSolution.class, JaxbTestdataEntity.class);
  }

  private List<JaxbTestdataValue> valueList;
  private List<JaxbTestdataEntity> entityList;

  private SimpleScore score;

  public JaxbTestdataSolution() {}

  public JaxbTestdataSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  @XmlElementWrapper(name = "valueList")
  @XmlElement(name = "jaxbTestdataValue")
  public List<JaxbTestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<JaxbTestdataValue> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  @XmlElementWrapper(name = "entityList")
  @XmlElement(name = "jaxbTestdataEntity")
  public List<JaxbTestdataEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<JaxbTestdataEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  @XmlJavaTypeAdapter(SimpleScoreJaxbAdapter.class)
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
