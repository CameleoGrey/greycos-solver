package greycos.solver.greybay.examples;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.greybay.api.VerificationResult;

import org.junit.jupiter.api.Test;

class ExampleBusinessReplayTest {
  @Test
  void schedulingFreshLoadsAndReplayRejectDroppedDemandEvenWithRecalculatedScore() {
    var adapter = SchedulingExample.problemAdapter();
    var first = adapter.loadFresh("train-small");
    var fresh = adapter.loadFresh("train-small");
    assertThat(first).isNotSameAs(fresh);
    assertThat(first.getAssignments().getFirst()).isNotSameAs(fresh.getAssignments().getFirst());
    assertThat(first.getAssignments().getFirst().getId())
        .isEqualTo(fresh.getAssignments().getFirst().getId());
    var calculator = new SchedulingExample.ScheduleScoreCalculator();
    first.setScore(calculator.calculateScore(first));
    assertThat(adapter.verifier().orElseThrow().verify("train-small", first).status())
        .isEqualTo(VerificationResult.Status.PASSED);
    first.getAssignments().removeFirst();
    first.setScore(calculator.calculateScore(first));
    assertThat(adapter.verifier().orElseThrow().verify("train-small", first).status())
        .isEqualTo(VerificationResult.Status.INVALID);
    assertThat(fresh.getAssignments()).hasSize(6);
    assertThat(adapter.describe("train-small").contentFingerprint())
        .isNotEqualTo(adapter.describe("heldout-small").contentFingerprint());
  }

  @Test
  void schedulingRejectsForeignReferencesAndScoreCorruption() {
    var adapter = SchedulingExample.problemAdapter();
    var solution = adapter.loadFresh("train-small");
    solution
        .getAssignments()
        .getFirst()
        .setWorker(adapter.loadFresh("train-small").getWorkers().getFirst());
    solution.setScore(new SchedulingExample.ScheduleScoreCalculator().calculateScore(solution));
    assertThat(adapter.verifier().orElseThrow().verify("train-small", solution).status())
        .isEqualTo(VerificationResult.Status.INVALID);
    var sound = adapter.loadFresh("train-small");
    sound.setScore(HardSoftScore.ZERO);
    assertThat(adapter.verifier().orElseThrow().verify("train-small", sound).status())
        .isEqualTo(VerificationResult.Status.INVALID);
  }

  @Test
  void routingFreshLoadsReconstructOwnerAndIndexShadowsByStableIds() {
    var adapter = RoutingExample.problemAdapter();
    var first = adapter.loadFresh("train-small");
    var fresh = adapter.loadFresh("train-small");
    assertThat(first.getStops().getFirst()).isNotSameAs(fresh.getStops().getFirst());
    assertThat(first.getRoutes().getFirst()).isNotSameAs(fresh.getRoutes().getFirst());
    assertThat(first.getStops().getFirst().getId()).isEqualTo(fresh.getStops().getFirst().getId());
    assertThat(fresh.getStops().getFirst().getRoute()).isSameAs(fresh.getRoutes().getFirst());
    first.setScore(new RoutingExample.RouteScoreCalculator().calculateScore(first));
    assertThat(adapter.verifier().orElseThrow().verify("train-small", first).status())
        .isEqualTo(VerificationResult.Status.PASSED);
    first.getStops().getFirst().setIndex(99);
    assertThat(adapter.verifier().orElseThrow().verify("train-small", first).status())
        .isEqualTo(VerificationResult.Status.INVALID);
    assertThat(fresh.getStops().getFirst().getIndex()).isZero();
  }

  @Test
  void routingReplayRejectsMissingOriginalDemandAndDuplicateVisits() {
    var adapter = RoutingExample.problemAdapter();
    var plan = adapter.loadFresh("train-small");
    var removed = plan.getStops().removeFirst();
    var route = removed.getRoute();
    route.getStops().remove(removed);
    for (int i = 0; i < route.getStops().size(); i++) {
      route.getStops().get(i).setIndex(i);
    }
    plan.setScore(new RoutingExample.RouteScoreCalculator().calculateScore(plan));
    assertThat(adapter.verifier().orElseThrow().verify("train-small", plan).status())
        .isEqualTo(VerificationResult.Status.INVALID);
    var duplicated = adapter.loadFresh("train-small");
    duplicated.getRoutes().getFirst().getStops().add(duplicated.getStops().getFirst());
    duplicated.setScore(new RoutingExample.RouteScoreCalculator().calculateScore(duplicated));
    assertThat(adapter.verifier().orElseThrow().verify("train-small", duplicated).status())
        .isEqualTo(VerificationResult.Status.INVALID);
  }
}
