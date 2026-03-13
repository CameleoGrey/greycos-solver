package ai.greycos.solver.core.testcotwin.shadow.dynamic_follower;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataDynamicFollowerSolution extends TestdataObject {
  public static SolutionDescriptor<TestdataDynamicFollowerSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataDynamicFollowerSolution.class,
        TestdataDynamicLeaderEntity.class,
        TestdataDynamicFollowerEntity.class);
  }

  @PlanningEntityCollectionProperty @ValueRangeProvider List<TestdataDynamicLeaderEntity> leaders;

  @PlanningEntityCollectionProperty List<TestdataDynamicFollowerEntity> followers;

  @ValueRangeProvider List<TestdataValue> values;

  @PlanningScore SimpleScore score;

  public TestdataDynamicFollowerSolution() {}

  public TestdataDynamicFollowerSolution(
      String code,
      List<TestdataDynamicLeaderEntity> leaders,
      List<TestdataDynamicFollowerEntity> followers,
      List<TestdataValue> values) {
    super(code);
    this.leaders = leaders;
    this.followers = followers;
    this.values = values;
  }

  public static TestdataDynamicFollowerSolution generateSolution(
      int leaderCount, int followerCount, int valueCount) {
    var leaders = new ArrayList<TestdataDynamicLeaderEntity>(leaderCount);
    var followers = new ArrayList<TestdataDynamicFollowerEntity>(followerCount);
    var values = new ArrayList<TestdataValue>(valueCount);

    for (int i = 0; i < leaderCount; i++) {
      leaders.add(new TestdataDynamicLeaderEntity("Leader %d".formatted(i)));
    }

    for (var i = 0; i < followerCount; i++) {
      followers.add(new TestdataDynamicFollowerEntity("Follower %d".formatted(i)));
    }

    for (var i = 0; i < valueCount; i++) {
      values.add(new TestdataValue("Value %d".formatted(i)));
    }

    return new TestdataDynamicFollowerSolution("Solution", leaders, followers, values);
  }

  public List<TestdataDynamicLeaderEntity> getLeaders() {
    return leaders;
  }

  public void setLeaders(List<TestdataDynamicLeaderEntity> leaders) {
    this.leaders = leaders;
  }

  public List<TestdataDynamicFollowerEntity> getFollowers() {
    return followers;
  }

  public void setFollowers(List<TestdataDynamicFollowerEntity> followers) {
    this.followers = followers;
  }

  public List<TestdataValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataValue> values) {
    this.values = values;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
