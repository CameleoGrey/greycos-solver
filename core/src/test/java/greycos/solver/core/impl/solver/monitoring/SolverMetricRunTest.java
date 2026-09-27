package greycos.solver.core.impl.solver.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SolverMetricRunTest {
  @Test
  @Timeout(10)
  void sealingWaitsForPublicationAndPreventsAllLaterWrites() throws Exception {
    var run = new SolverMetricRun();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var sealing = new CountDownLatch(1);
    List<String> operations = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var publication =
          executor.submit(
              () ->
                  run.publish(
                      () -> {
                        entered.countDown();
                        await(release);
                        operations.add("publish");
                      }));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      var cleanup =
          executor.submit(
              () -> {
                sealing.countDown();
                run.seal();
                run.cleanup(() -> operations.add("cleanup"));
              });
      assertThat(sealing.await(5, TimeUnit.SECONDS)).isTrue();
      release.countDown();
      assertThat(publication.get(5, TimeUnit.SECONDS)).isTrue();
      cleanup.get(5, TimeUnit.SECONDS);
      assertThat(run.publish(() -> operations.add("late registration"))).isFalse();
      assertThat(operations).containsExactly("publish", "cleanup");
    } finally {
      release.countDown();
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS))
        throw new IllegalStateException("Publication timed out.");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
