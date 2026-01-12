package ai.greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

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
}
