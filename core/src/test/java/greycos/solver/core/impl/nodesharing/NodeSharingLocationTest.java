package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.bavet.common.BavetAbstractConstraintStream;
import greycos.solver.core.impl.bavet.common.ConstraintNodeLocation;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class NodeSharingLocationTest {

  @Test
  void sharedFiltersRetainBothOriginalSourceLocations() throws ReflectiveOperationException {
    var original = locations(new Provider());
    var sharedClass =
        new DefaultConstraintProviderNodeSharer().buildNodeSharedConstraintProvider(Provider.class);
    assertThat(sharedClass.isHidden()).isTrue();
    var shared = locations(sharedClass.getConstructor().newInstance());

    assertThat(original).hasSize(2);
    assertThat(original)
        .extracting(ConstraintNodeLocation::methodName)
        .containsExactlyInAnyOrder("first", "second");
    assertThat(original)
        .allSatisfy(
            location -> {
              assertThat(location.className()).isEqualTo(Provider.class.getName());
              assertThat(location.lineNumber()).isPositive();
            });
    assertThat(shared).containsExactlyElementsOf(original);
  }

  private static SortedSet<ConstraintNodeLocation> locations(ConstraintProvider provider)
      throws ReflectiveOperationException {
    var factory =
        new BavetConstraintFactory<>(
            TestdataSolution.buildSolutionDescriptor(), EnvironmentMode.NO_ASSERT);
    provider.defineConstraints(factory);
    var field = BavetConstraintFactory.class.getDeclaredField("sharingStreamMap");
    field.setAccessible(true);
    var streams = (Map<?, ?>) field.get(factory);
    var locations = new TreeSet<ConstraintNodeLocation>();
    streams.keySet().stream()
        .filter(
            stream -> stream.getClass().getSimpleName().equals("BavetFilterUniConstraintStream"))
        .map(stream -> (BavetAbstractConstraintStream<?>) stream)
        .forEach(stream -> locations.addAll(stream.getLocationSet()));
    return locations;
  }

  public static class Provider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {first(factory), second(factory)};
    }

    private Constraint first(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> !entity.getCode().isEmpty())
          .penalize(SimpleScore.ONE)
          .asConstraint("first");
    }

    private static Constraint second(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> !entity.getCode().isEmpty())
          .penalize(SimpleScore.ONE)
          .asConstraint("second");
    }
  }
}
