package ai.greycos.solver.core.impl.bavet.common.index;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import ai.greycos.solver.core.impl.util.ElementAwareArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@MethodSource("randomSeeds")
@ParameterizedClass
@Execution(ExecutionMode.CONCURRENT)
class FilteredUniqueRandomIteratorTest {

  private static final List<String> ELEMENTS = List.of("A", "B", "C", "D");

  private static <T> ElementAwareArrayList<T> toEntries(List<T> elements) {
    var list = new ElementAwareArrayList<T>();
    for (var element : elements) {
      list.add(element);
    }
    return list;
  }

  private static Stream<Arguments> randomSeeds() {
    return IntStream.range(0, 10).mapToObj(Arguments::of);
  }

  @Parameter private int randomSeed;

  Random random;

  @BeforeEach
  void beforeEach() {
    random = new Random(randomSeed);
  }

  @CsvSource(
      useHeadersInDisplayName = true,
      value =
          """
          elementCount
          1
          2
          3
          4
          """)
  @ParameterizedTest(name = "{arguments}")
  void throwsWhenUnknowinglyEmpty(int elementCount) {
    var list = ELEMENTS.subList(0, elementCount);
    Predicate<String> filter = Predicate.not(list::contains);

    var sequence = new FilteredUniqueRandomIterator<>(toEntries(list), random, filter);
    assertThatThrownBy(sequence::next).isInstanceOf(NoSuchElementException.class);
  }
}
