package greycos.solver.core.testcotwin.multiplelist;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataMultipleListNoInverseSolution {

  @PlanningEntityCollectionProperty public List<Owner> owners = new ArrayList<>();

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "firstRange")
  public List<FirstValue> firstValues = new ArrayList<>();

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "secondRange")
  public List<SecondValue> secondValues = new ArrayList<>();

  @PlanningEntityCollectionProperty public List<BaseValue> otherValues = new ArrayList<>();

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "basicRange")
  public List<Integer> basicRange = List.of(1, 2);

  @PlanningScore public SimpleScore score;

  public static SolutionDescriptor<TestdataMultipleListNoInverseSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataMultipleListNoInverseSolution.class,
        Owner.class,
        BaseValue.class,
        FirstValue.class,
        SecondValue.class);
  }

  public interface ValueMarker {}

  @PlanningEntity
  public static class BaseValue implements ValueMarker {
    @PlanningVariable(valueRangeProviderRefs = "basicRange")
    public Integer basicValue = 1;

    @ShadowVariable(supplierName = "copyBasicValue")
    public Integer copiedValue = 1;

    @ShadowSources("basicValue")
    public Integer copyBasicValue() {
      return basicValue;
    }
  }

  @PlanningEntity
  public static class FirstValue extends BaseValue {}

  @PlanningEntity
  public static class SecondValue extends BaseValue {}

  @PlanningEntity
  public static class Owner {
    @PlanningListVariable(valueRangeProviderRefs = "firstRange", allowsUnassignedValues = true)
    public List<FirstValue> first = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "secondRange", allowsUnassignedValues = true)
    public List<SecondValue> second = new ArrayList<>();

    @PlanningVariable(valueRangeProviderRefs = "basicRange")
    public Integer basicValue;

    @PlanningPin public boolean pinned;
  }
}
