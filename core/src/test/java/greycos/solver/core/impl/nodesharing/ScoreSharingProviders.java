package greycos.solver.core.impl.nodesharing;

import java.io.Serializable;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;

/** Providers whose independently expected scores distinguish unsafe lambda equivalence. */
public final class ScoreSharingProviders {

  private ScoreSharingProviders() {}

  public static boolean neverMatches(TestdataEntity entity) {
    return false;
  }

  public static boolean nonEmpty(TestdataEntity entity) {
    return !entity.getCode().isEmpty();
  }

  public static boolean constantMatches(String code, Object constant) {
    return code.equals("class") ? constant instanceof Class<?> : constant instanceof String;
  }

  public interface Marker {}

  public static class ConcatenationProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .filter(entity -> ("A" + entity.getCode()).equals("Ax"))
            .penalize(SimpleScore.ONE)
            .asConstraint("first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(entity -> ("B" + entity.getCode()).equals("Ax"))
            .penalize(SimpleScore.of(2))
            .asConstraint("second"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared second")
      };
    }
  }

  public static class NestedPredicateProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .filter(entity -> Stream.of(entity.getCode()).anyMatch(code -> code.equals("x")))
            .penalize(SimpleScore.ONE)
            .asConstraint("first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(entity -> Stream.of(entity.getCode()).anyMatch(code -> code.equals("y")))
            .penalize(SimpleScore.of(2))
            .asConstraint("second"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared second")
      };
    }
  }

  public static class BooleanGroupingProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .filter(
                entity ->
                    (entity.getCode().charAt(0) == '1' || entity.getCode().charAt(1) == '1')
                        && entity.getCode().charAt(2) == '1')
            .penalize(SimpleScore.ONE)
            .asConstraint("first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(
                entity ->
                    entity.getCode().charAt(0) == '1'
                        || (entity.getCode().charAt(1) == '1' && entity.getCode().charAt(2) == '1'))
            .penalize(SimpleScore.of(2))
            .asConstraint("second"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared second")
      };
    }
  }

  public static class ExceptionHandlerProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .filter(
                entity -> {
                  try {
                    try {
                      return Integer.parseInt(entity.getCode()) > 0;
                    } catch (NumberFormatException exception) {
                      return false;
                    }
                  } catch (RuntimeException exception) {
                    return true;
                  }
                })
            .penalize(SimpleScore.ONE)
            .asConstraint("first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(
                entity -> {
                  try {
                    try {
                      return Integer.parseInt(entity.getCode()) > 0;
                    } catch (NullPointerException exception) {
                      return false;
                    }
                  } catch (RuntimeException exception) {
                    return true;
                  }
                })
            .penalize(SimpleScore.of(2))
            .asConstraint("second"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared second")
      };
    }
  }

  public static class TypedConstantProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .filter(entity -> constantMatches(entity.getCode(), String.class))
            .penalize(SimpleScore.ONE)
            .asConstraint("first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(entity -> constantMatches(entity.getCode(), "TYPE:Ljava/lang/String;"))
            .penalize(SimpleScore.of(2))
            .asConstraint("second"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared second")
      };
    }
  }

  public static class SwitchProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .filter(
                entity ->
                    switch (entity.getCode().length()) {
                      case 0 -> true;
                      case 1 -> false;
                      default -> true;
                    })
            .penalize(SimpleScore.ONE)
            .asConstraint("first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(
                entity ->
                    switch (entity.getCode().length()) {
                      case 0 -> false;
                      case 1 -> true;
                      default -> true;
                    })
            .penalize(SimpleScore.of(2))
            .asConstraint("second"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared second")
      };
    }
  }

  public static class MixedLambdaProvider implements ConstraintProvider {
    public Predicate<TestdataEntity> ordinaryPredicate() {
      return ScoreSharingProviders::nonEmpty;
    }

    public Predicate<TestdataEntity> serializablePredicate() {
      // Force this method to be copied, so serialization tests exercise bootstrap forwarding.
      Objects.requireNonNull((Predicate<TestdataEntity>) ScoreSharingProviders::nonEmpty);
      return (Predicate<TestdataEntity> & Serializable & Marker) ScoreSharingProviders::nonEmpty;
    }

    public Predicate<TestdataEntity> capturedPredicate(int minimumLength) {
      Objects.requireNonNull((Predicate<TestdataEntity>) ScoreSharingProviders::nonEmpty);
      return entity -> entity.getCode().length() > minimumLength;
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .filter(ordinaryPredicate())
            .penalize(SimpleScore.ONE)
            .asConstraint("ordinary"),
        factory
            .forEach(TestdataEntity.class)
            .filter(serializablePredicate())
            .penalize(SimpleScore.of(2))
            .asConstraint("serializable"),
        factory
            .forEach(TestdataEntity.class)
            .filter(capturedPredicate(1))
            .penalize(SimpleScore.of(4))
            .asConstraint("captured one"),
        factory
            .forEach(TestdataEntity.class)
            .filter(capturedPredicate(3))
            .penalize(SimpleScore.of(8))
            .asConstraint("captured three"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared first"),
        factory
            .forEach(TestdataEntity.class)
            .filter(ScoreSharingProviders::neverMatches)
            .penalize(SimpleScore.ONE)
            .asConstraint("shared second")
      };
    }
  }

  public static class CountingProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .filter(entity -> ScoreSharingInvocationCounter.count(entity.getCode()))
            .penalize(SimpleScore.ONE)
            .asConstraint("weight one"),
        factory
            .forEach(TestdataEntity.class)
            .filter(entity -> ScoreSharingInvocationCounter.count(entity.getCode()))
            .penalize(SimpleScore.of(2))
            .asConstraint("weight two")
      };
    }
  }
}
