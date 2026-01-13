package ai.greycos.solver.quarkus.benchmark.it.cotwin;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;

@PlanningEntity
public class TestdataStringLengthShadowEntity {

  @PlanningId private Long id;

  @PlanningListVariable private List<TestdataListValueShadowEntity> values;

  public TestdataStringLengthShadowEntity() {}

  public TestdataStringLengthShadowEntity(Long id) {
    this.id = id;
    this.values = new ArrayList<>();
  }

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public Long getId() {
    return id;
  }

  public List<TestdataListValueShadowEntity> getValues() {
    return values;
  }

  public void setValues(List<TestdataListValueShadowEntity> values) {
    this.values = values;
  }
}
