package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class BoundedChannelTest {

  @Test
  void channelEnforcesCapacity() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);
    assertThat(channel.trySend("first")).isTrue();
    assertThat(channel.capacity()).isEqualTo(1);

    assertThat(channel.trySend("second")).isFalse();
    assertThat(channel.size()).isEqualTo(1);
  }

  @Test
  void sendThenReceiveReturnsMessage() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);

    channel.send("test message");
    assertThat(channel.size()).isEqualTo(1);

    String received = channel.receive();
    assertThat(received).isEqualTo("test message");
    assertThat(channel.isEmpty()).isTrue();
  }

  @Test
  void sendWithTimeoutSucceedsWhenSpaceAvailable() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);

    boolean sent = channel.send("message", 10, TimeUnit.MILLISECONDS);
    assertThat(sent).isTrue();
    assertThat(channel.size()).isEqualTo(1);
  }

  @Test
  void sendWithTimeoutTimesOutWhenFull() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);
    channel.send("first");

    boolean sent = channel.send("second", 10, TimeUnit.MILLISECONDS);
    assertThat(sent).isFalse();
    assertThat(channel.size()).isEqualTo(1);
  }

  @Test
  void receiveWithTimeoutReturnsMessageWhenAvailable() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);
    channel.send("message");

    String received = channel.tryReceive(10, TimeUnit.MILLISECONDS);
    assertThat(received).isEqualTo("message");
  }

  @Test
  void receiveWithTimeoutTimesOutWhenEmpty() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);

    String received = channel.tryReceive(10, TimeUnit.MILLISECONDS);
    assertThat(received).isNull();
  }

  @Test
  void tryReceiveOnEmptyChannelReturnsNull() {
    BoundedChannel<String> channel = new BoundedChannel<>(1);

    String received = channel.tryReceive();
    assertThat(received).isNull();
  }

  @Test
  void tryReceiveOnFullChannelReturnsMessage() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);
    channel.send("message");

    String received = channel.tryReceive();
    assertThat(received).isEqualTo("message");
  }

  @Test
  void replaceOverwritesWhenChannelIsFull() throws InterruptedException {
    BoundedChannel<String> channel = new BoundedChannel<>(1);
    channel.send("old");

    boolean replaced = channel.replace("new");
    assertThat(replaced).isTrue();
    assertThat(channel.receive()).isEqualTo("new");
  }

  @Test
  void closingWakesAReceiverWithoutWaitingForItsTimeout() throws Exception {
    var channel = new BoundedChannel<String>(1);
    var received = new FutureTask<>(() -> channel.tryReceive(1, TimeUnit.DAYS));
    var receiver = new Thread(received, "closing-island-channel-receiver");
    receiver.start();
    try {
      await()
          .untilAsserted(
              () -> assertThat(receiver.getState()).isEqualTo(Thread.State.TIMED_WAITING));

      channel.close();

      assertThat(received.get(5, TimeUnit.SECONDS)).isNull();
      assertThat(channel.receive()).isNull();
      assertThat(channel.trySend("late")).isFalse();
      assertThat(channel.replace("late")).isFalse();
    } finally {
      channel.close();
      receiver.interrupt();
      receiver.join(5000);
      assertThat(receiver.isAlive()).isFalse();
    }
  }

  @Test
  void closingWakesAForwarderBlockedOnAFullChannel() throws Exception {
    var channel = new BoundedChannel<String>(1);
    channel.send("pending");
    var forwarded = new FutureTask<>(() -> channel.send("forwarded", 1, TimeUnit.DAYS));
    var sender = new Thread(forwarded, "closing-island-channel-forwarder");
    sender.start();
    try {
      await()
          .untilAsserted(() -> assertThat(sender.getState()).isEqualTo(Thread.State.TIMED_WAITING));

      channel.close();
      channel.close();

      assertThat(forwarded.get(5, TimeUnit.SECONDS)).isFalse();
      assertThat(channel.isEmpty()).isTrue();
      assertThat(channel.capacity()).isEqualTo(1);
      assertThat(channel.send("late", 1, TimeUnit.DAYS)).isFalse();
      assertThatThrownBy(() -> channel.send("late"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("closed island channel");
    } finally {
      channel.close();
      sender.interrupt();
      sender.join(5000);
      assertThat(sender.isAlive()).isFalse();
    }
  }

  @Test
  void interruptionReleasesABlockedReceiver() throws Exception {
    var channel = new BoundedChannel<String>(1);
    var received = new FutureTask<>(channel::receive);
    var receiver = new Thread(received, "interrupted-island-channel-receiver");
    receiver.start();
    try {
      await().untilAsserted(() -> assertThat(receiver.getState()).isEqualTo(Thread.State.WAITING));
      receiver.interrupt();

      assertThatThrownBy(() -> received.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(channel.trySend("still open")).isTrue();
    } finally {
      channel.close();
      receiver.interrupt();
      receiver.join(5000);
      assertThat(receiver.isAlive()).isFalse();
    }
  }
}
