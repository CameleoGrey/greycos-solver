package ai.greycos.solver.core.impl.solver.random;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import org.junit.jupiter.api.Test;

class DefaultRandomSourceTest {

  @Test
  void childSourceCanBeTransferredExactlyOnce() throws Exception {
    var parentSource = DefaultRandomSource.seeded(37L);
    var childSource = parentSource.splitForChildThread();

    // Child phases are built on the parent thread before the solver task is submitted.
    childSource.factoryUsage().nextInt();

    runInNewThread(
        () -> {
          childSource.transferOwnershipToCurrentThread();
          childSource.moveIteratorUsage().nextInt();
          childSource.factoryUsage().nextInt();
          childSource.acceptorUsage().nextInt();
          assertThatThrownBy(childSource::transferOwnershipToCurrentThread)
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("Ownership transfer is not available");
          return null;
        });

    assertThatThrownBy(() -> childSource.moveIteratorUsage().nextInt())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not the owner thread");
    assertThatThrownBy(
            () ->
                runInNewThread(
                    () -> {
                      childSource.transferOwnershipToCurrentThread();
                      return null;
                    }))
        .hasCauseInstanceOf(IllegalStateException.class)
        .rootCause()
        .hasMessageContaining("Ownership transfer is not available");

    // Splitting a child source does not transfer or otherwise invalidate the parent source.
    assertThatCode(() -> parentSource.moveIteratorUsage().nextInt()).doesNotThrowAnyException();
  }

  @Test
  void regularSourceCannotBeTransferred() {
    var source = DefaultRandomSource.seeded(37L);

    assertThatThrownBy(source::transferOwnershipToCurrentThread)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Ownership transfer is not available");
    assertThatThrownBy(
            () ->
                runInNewThread(
                    () -> {
                      source.transferOwnershipToCurrentThread();
                      return null;
                    }))
        .hasCauseInstanceOf(IllegalStateException.class)
        .rootCause()
        .hasMessageContaining("Ownership transfer is not available");
  }

  private static <Result_> Result_ runInNewThread(Callable<Result_> callable) throws Exception {
    var task = new FutureTask<>(callable);
    var thread = new Thread(task, "random-source-test");
    thread.start();
    return task.get();
  }
}
