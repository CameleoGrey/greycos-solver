package greycos.solver.core.testcotwin.cascade.mixed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

@PlanningSolution
public class TestdataMixedCascadingSolution {
  @PlanningEntityCollectionProperty public List<Route> routes = new ArrayList<>();

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "visits")
  public List<Visit> visits = new ArrayList<>();

  @ValueRangeProvider(id = "delays")
  public List<Integer> delays = List.of(1, 2, 3);

  @PlanningScore public SimpleScore score;

  public static SolutionDescriptor<TestdataMixedCascadingSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataMixedCascadingSolution.class, Route.class, Visit.class);
  }

  public static TestdataMixedCascadingSolution generate(int routeCount, int visitsPerRoute) {
    var solution = new TestdataMixedCascadingSolution();
    for (var r = 0; r < routeCount; r++) {
      var route = new Route();
      solution.routes.add(route);
      for (var i = 0; i < visitsPerRoute; i++) {
        var visit = new Visit();
        solution.visits.add(visit);
        route.visits.add(visit);
      }
    }
    return solution;
  }

  public SimpleScore replayScore() {
    var result = 0;
    for (var route : routes) {
      var prefix = 0;
      for (var visit : route.visits) {
        prefix += visit.delay;
        result += prefix;
      }
    }
    return SimpleScore.of(result);
  }

  public SimpleScore shadowScore() {
    return SimpleScore.of(visits.stream().mapToInt(v -> v.total == null ? 0 : v.total).sum());
  }

  @PlanningEntity
  public static class Route {
    @PlanningVariable(valueRangeProviderRefs = "delays")
    public Integer offset = 1;

    @PlanningListVariable(valueRangeProviderRefs = "visits", allowsUnassignedValues = true)
    public List<Visit> visits = new ArrayList<>();
  }

  @PlanningEntity
  public static class Visit {
    @PlanningVariable(valueRangeProviderRefs = "delays")
    public Integer delay = 1;

    @PlanningVariable(valueRangeProviderRefs = "visits", allowsUnassigned = true)
    public Visit partner;

    @InverseRelationShadowVariable(sourceVariableName = "partner")
    public Collection<Visit> dependents = new ArrayList<>();

    @IndexShadowVariable(sourceVariableName = "visits")
    public Integer index;

    @NextElementShadowVariable(sourceVariableName = "visits")
    public Visit next;

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    public Route route;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    public Visit previous;

    @ShadowVariable(supplierName = "durationSupplier")
    public Integer duration;

    @CascadingUpdateShadowVariable(targetMethodName = "updateTotal")
    public Integer total;

    @CascadingUpdateShadowVariable(targetMethodName = "updateTotal")
    public Integer mirroredTotal;

    @CascadingUpdateShadowVariable(targetMethodName = "updateIndependent")
    public Integer independent;

    @CascadingUpdateShadowVariable(targetMethodName = "updatePosition")
    public Integer position;

    @CascadingUpdateShadowVariable(targetMethodName = "updatePosition")
    public Integer ownerMarker;

    @CascadingUpdateShadowVariable(targetMethodName = "updateDependents")
    public Integer inverseCount;

    public int positionCalls;

    public int calls;
    public int independentCalls;
    public boolean failUpdate;

    @ShadowSources("delay")
    public Integer durationSupplier() {
      return delay;
    }

    public void updateTotal() {
      if (failUpdate) {
        throw new IllegalStateException("cascade failure");
      }
      calls++;
      total = route == null ? 0 : duration + (previous == null ? 0 : previous.total);
      mirroredTotal = total;
    }

    public void updatePosition() {
      positionCalls++;
      position =
          route == null
              ? 0
              : (index < 5 ? 0 : index) + (previous == null ? 100 : 0) + (next == null ? 1000 : 0);
      ownerMarker = route == null ? 0 : route.offset;
    }

    public void updateDependents() {
      inverseCount = dependents.size();
    }

    public void updateIndependent() {
      independentCalls++;
      independent = route == null ? 0 : delay;
    }
  }
}
