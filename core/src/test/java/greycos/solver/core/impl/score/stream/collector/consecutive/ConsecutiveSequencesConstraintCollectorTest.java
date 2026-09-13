package greycos.solver.core.impl.score.stream.collector.consecutive;

import static greycos.solver.core.impl.score.stream.collector.consecutive.ConsecutiveSequenceTestUtils.assertChain;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.bi.BiConstraintCollector;
import greycos.solver.core.api.score.stream.common.SequenceChain;
import greycos.solver.core.api.score.stream.quad.QuadConstraintCollector;
import greycos.solver.core.api.score.stream.tri.TriConstraintCollector;
import greycos.solver.core.api.score.stream.uni.UniConstraintCollector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConsecutiveSequencesConstraintCollectorTest {

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4})
  void sharedValueChangesIndex(int arity) {
    var context = context(arity);
    var first = context.handles().get();
    var second = context.handles().get();
    var third = context.handles().get();
    var anchorHandle = context.handles().get();
    var shared = new AtomicInteger(0);
    var anchor = new AtomicInteger(2);
    first.add().accept(shared);
    second.add().accept(shared);
    third.add().accept(shared);
    anchorHandle.add().accept(anchor);
    var chain = context.result().get();
    assertChain(chain, List.of(List.of(shared), List.of(anchor)), List.of(1, 1), List.of(2));

    shared.set(1);
    first.replace().accept(shared);
    // The remaining old contributions still occupy index 0, even though shared.get() is now 1.
    assertChain(chain, List.of(List.of(shared, shared, anchor)), List.of(3), List.of());
    second.replace().accept(shared);
    assertChain(chain, List.of(List.of(shared, shared, anchor)), List.of(3), List.of());
    third.remove().run(); // Retract its captured index 0, not the object's current index 1.
    assertChain(chain, List.of(List.of(shared, anchor)), List.of(2), List.of());
    first.replace().accept(shared); // Same object and index must retain both contributions.
    assertChain(chain, List.of(List.of(shared, anchor)), List.of(2), List.of());

    shared.set(-2);
    second.replace().accept(shared);
    assertChain(
        chain, List.of(List.of(shared), List.of(shared, anchor)), List.of(1, 2), List.of(3));
    first.replace().accept(shared);
    assertChain(chain, List.of(List.of(shared), List.of(anchor)), List.of(1, 1), List.of(4));

    var replacement = new AtomicInteger(-2);
    first.replace().accept(replacement);
    assertThat(chain.getFirstSequence().getItems()).containsExactlyInAnyOrder(shared, replacement);
    assertThat(chain.getFirstSequence().getCount()).isEqualTo(2);
    assertThat(chain.getFirstSequence().getLength()).isEqualTo(1);
    second.remove().run();
    assertChain(chain, List.of(List.of(replacement), List.of(anchor)), List.of(1, 1), List.of(4));
    anchorHandle.remove().run();
    assertChain(chain, List.of(List.of(replacement)), List.of(1), List.of());
    first.remove().run();
    assertChain(chain, List.of(), List.of(), List.of());

    // Delegating collectors may reuse a removed handle.
    shared.set(7);
    first.add().accept(shared);
    shared.set(8);
    first.replace().accept(shared);
    assertChain(chain, List.of(List.of(shared)), List.of(1), List.of());
    first.remove().run();
    assertChain(chain, List.of(), List.of(), List.of());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 3, 4})
  void sameObjectAtBothSequenceEndpoints(int arity) {
    var context = context(arity);
    var first = context.handles().get();
    var second = context.handles().get();
    var shared = new AtomicInteger(0);
    first.add().accept(shared);
    second.add().accept(shared);
    shared.set(1);
    first.replace().accept(shared);
    assertChain(context.result().get(), List.of(List.of(shared, shared)), List.of(2), List.of());
    second.replace().accept(shared);
    assertChain(context.result().get(), List.of(List.of(shared)), List.of(1), List.of());
    first.remove().run();
    assertChain(context.result().get(), List.of(List.of(shared)), List.of(1), List.of());
    second.remove().run();
    assertChain(context.result().get(), List.of(), List.of(), List.of());
  }

  @Test
  void failedIndexMappingDoesNotRetractOldContribution() {
    var fail = new AtomicBoolean();
    var context =
        uni(
            ConstraintCollectors.<AtomicInteger>toConsecutiveSequences(
                value -> {
                  if (fail.get()) {
                    throw new IllegalArgumentException("index mapping failed");
                  }
                  return value.get();
                }));
    var handle = context.handles().get();
    var value = new AtomicInteger(0);
    handle.add().accept(value);
    value.set(1);
    fail.set(true);
    assertThatThrownBy(() -> handle.replace().accept(value))
        .isInstanceOf(IllegalArgumentException.class);
    assertChain(context.result().get(), List.of(List.of(value)), List.of(1), List.of());
    handle.remove().run();
    assertChain(context.result().get(), List.of(), List.of(), List.of());
  }

  private static Context context(int arity) {
    return switch (arity) {
      case 1 -> uni(ConstraintCollectors.toConsecutiveSequences(AtomicInteger::get));
      case 2 ->
          bi(
              ConstraintCollectors.toConsecutiveSequences(
                  (AtomicInteger a, Integer b) -> a, AtomicInteger::get));
      case 3 ->
          tri(
              ConstraintCollectors.toConsecutiveSequences(
                  (AtomicInteger a, Integer b, Integer c) -> a, AtomicInteger::get));
      case 4 ->
          quad(
              ConstraintCollectors.toConsecutiveSequences(
                  (AtomicInteger a, Integer b, Integer c, Integer d) -> a, AtomicInteger::get));
      default -> throw new IllegalArgumentException("Unsupported arity: " + arity);
    };
  }

  private static <C> Context uni(
      UniConstraintCollector<AtomicInteger, C, SequenceChain<AtomicInteger, Integer>> collector) {
    var state = collector.supplier().get();
    return new Context(
        () -> {
          var handle = collector.accumulator().intoGroup(state);
          return new Handle(handle::add, handle::replaceWith, handle::remove);
        },
        () -> collector.finisher().apply(state));
  }

  private static <C> Context bi(
      BiConstraintCollector<AtomicInteger, Integer, C, SequenceChain<AtomicInteger, Integer>>
          collector) {
    var state = collector.supplier().get();
    return new Context(
        () -> {
          var handle = collector.accumulator().intoGroup(state);
          return new Handle(a -> handle.add(a, 0), a -> handle.replaceWith(a, 0), handle::remove);
        },
        () -> collector.finisher().apply(state));
  }

  private static <C> Context tri(
      TriConstraintCollector<
              AtomicInteger, Integer, Integer, C, SequenceChain<AtomicInteger, Integer>>
          collector) {
    var state = collector.supplier().get();
    return new Context(
        () -> {
          var handle = collector.accumulator().intoGroup(state);
          return new Handle(
              a -> handle.add(a, 0, 0), a -> handle.replaceWith(a, 0, 0), handle::remove);
        },
        () -> collector.finisher().apply(state));
  }

  private static <C> Context quad(
      QuadConstraintCollector<
              AtomicInteger, Integer, Integer, Integer, C, SequenceChain<AtomicInteger, Integer>>
          collector) {
    var state = collector.supplier().get();
    return new Context(
        () -> {
          var handle = collector.accumulator().intoGroup(state);
          return new Handle(
              a -> handle.add(a, 0, 0, 0), a -> handle.replaceWith(a, 0, 0, 0), handle::remove);
        },
        () -> collector.finisher().apply(state));
  }

  private record Context(
      Supplier<Handle> handles, Supplier<SequenceChain<AtomicInteger, Integer>> result) {}

  private record Handle(
      Consumer<AtomicInteger> add, Consumer<AtomicInteger> replace, Runnable remove) {}
}
