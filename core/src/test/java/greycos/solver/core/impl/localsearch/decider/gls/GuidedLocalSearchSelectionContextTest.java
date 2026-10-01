package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchSelectionContext.Priority;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchSelectionContext.PrioritySnapshot;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchSelectionContextTest {

  @Test
  void endlessTournamentUsesEightLegalProbesAndEveryFourthOriginIsOrdinary() {
    var context = context(value -> Priority.of(GuidedLocalSearchNumber.of((int) value)));
    var next = new AtomicInteger();
    var source =
        new Iterator<Integer>() {
          @Override
          public boolean hasNext() {
            return true;
          }

          @Override
          public Integer next() {
            return next.incrementAndGet();
          }
        };
    var iterator = context.direct(source, true, value -> context.valuePriority(value, "valueList"));
    var selected = new ArrayList<Integer>();
    for (int i = 1; i <= 8; i++) {
      selected.add(iterator.next());
      assertThat(context.wasLastOriginOrdinary()).isEqualTo(i % 4 == 0);
    }
    assertThat(selected).containsExactly(8, 16, 24, 25, 33, 41, 49, 50);
    assertThat(next).hasValue(50);
    var diagnostics = context.getDiagnostics();
    assertThat(diagnostics.eligibleOriginProbes()).isEqualTo(50);
    assertThat(diagnostics.emittedOrigins()).isEqualTo(8);
    assertThat(diagnostics.ordinaryOrigins()).isEqualTo(2);
    iterator.next();
    assertThat(context.getDiagnostics().eligibleOriginProbes()).isEqualTo(58);
    assertThat(diagnostics.eligibleOriginProbes()).isEqualTo(50);
    context.clear();
    assertThat(context.getDiagnostics())
        .isEqualTo(new GuidedLocalSearchSelectionContext.OriginDiagnostics(0L, 0L, 0L));
  }

  @Test
  void finiteSourcesKeepEverySelectionAndStrictExhaustion() {
    var source = IntStream.rangeClosed(1, 25).boxed().toList();
    var context = context(value -> Priority.of(GuidedLocalSearchNumber.of((int) value)));
    var iterator =
        context.direct(
            source.iterator(), false, value -> context.valuePriority(value, "valueList"));
    var selected = new ArrayList<Integer>();
    iterator.forEachRemaining(selected::add);
    assertThat(selected.subList(0, 4)).containsExactly(8, 7, 6, 9);
    assertThat(selected).containsExactlyInAnyOrderElementsOf(source);
    assertThat(iterator.hasNext()).isFalse();
    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(iterator::next);
    assertThat(context.getDiagnostics().eligibleOriginProbes()).isEqualTo(25);
    assertThat(context.getDiagnostics().emittedOrigins()).isEqualTo(25);
    assertThat(context.getDiagnostics().ordinaryOrigins()).isEqualTo(5);
  }

  @Test
  void finiteDuplicateSelectionsArePreserved() {
    var context = context(value -> Priority.of(GuidedLocalSearchNumber.of((int) value)));
    var source = List.of(1, 2, 2, 3, 1);
    var actual = new ArrayList<Integer>();
    context
        .direct(source.iterator(), false, value -> context.valuePriority(value, "valueList"))
        .forEachRemaining(actual::add);
    assertThat(actual).containsExactlyInAnyOrderElementsOf(source);
  }

  @Test
  void zeroImportancePreservesOriginalOrderAndConsumption() {
    var context = new GuidedLocalSearchSelectionContext<>();
    var consumed = new AtomicInteger();
    var source =
        IntStream.rangeClosed(1, 20).boxed().peek(ignored -> consumed.incrementAndGet()).iterator();
    var iterator = context.direct(source, true, ignored -> Priority.ZERO);
    assertThat(iterator.next()).isEqualTo(1);
    assertThat(consumed).hasValue(1);
    assertThat(iterator.next()).isEqualTo(2);
    assertThat(consumed).hasValue(2);
    assertThat(context.wasLastOriginOrdinary()).isTrue();
    assertThat(context.getDiagnostics())
        .isEqualTo(new GuidedLocalSearchSelectionContext.OriginDiagnostics(2L, 2L, 2L));
  }

  @Test
  void equalPrioritiesUseProbeOrder() {
    var context = context(ignored -> Priority.of(GuidedLocalSearchNumber.ONE));
    var actual = new ArrayList<Integer>();
    context
        .direct(
            List.of(5, 4, 3, 2, 1).iterator(),
            false,
            value -> context.valuePriority(value, "valueList"))
        .forEachRemaining(actual::add);
    assertThat(actual).containsExactly(5, 4, 3, 2, 1);
  }

  @Test
  void priorityComparisonPreservesLargeAndDecimalDifferencesAndLengthNormalization() {
    var huge = BigInteger.ONE.shiftLeft(100);
    assertThat(new Priority(GuidedLocalSearchNumber.of(huge.add(BigInteger.ONE)), 3))
        .isGreaterThan(new Priority(GuidedLocalSearchNumber.of(huge), 3));
    assertThat(
            new Priority(GuidedLocalSearchNumber.of(new BigDecimal("0.10000000000000000001")), 2))
        .isGreaterThan(new Priority(GuidedLocalSearchNumber.of(new BigDecimal("0.1")), 2));
    assertThat(new Priority(GuidedLocalSearchNumber.of(15), 3))
        .isGreaterThan(new Priority(GuidedLocalSearchNumber.of(16), 4));
  }

  @Test
  void publishingReprioritizesRemainingFiniteSelections() {
    var context = context(value -> Priority.of(GuidedLocalSearchNumber.of((int) value)));
    var iterator =
        context.direct(
            List.of(1, 2, 3).iterator(), false, value -> context.valuePriority(value, "valueList"));
    assertThat(iterator.next()).isEqualTo(3);
    context.publish(snapshot(value -> Priority.of(GuidedLocalSearchNumber.of(4 - (int) value))));
    assertThat(iterator.next()).isEqualTo(1);
  }

  static GuidedLocalSearchSelectionContext<Object> context(Function<Object, Priority> priority) {
    var context = new GuidedLocalSearchSelectionContext<>();
    context.publish(snapshot(priority));
    return context;
  }

  static PrioritySnapshot snapshot(Function<Object, Priority> priority) {
    return new PrioritySnapshot() {
      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public Priority entityPriority(Object entity, Collection<String> variableNames) {
        return priority.apply(entity);
      }

      @Override
      public Priority valuePriority(Object value, String variableName) {
        return priority.apply(value);
      }

      @Override
      public Priority subListPriority(
          Object owner, String variableName, int fromIndex, int length) {
        return priority.apply(owner);
      }
    };
  }
}
