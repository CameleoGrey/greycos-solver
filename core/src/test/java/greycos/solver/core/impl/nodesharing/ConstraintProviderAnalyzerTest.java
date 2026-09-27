package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.function.Predicate;

import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;

import org.junit.jupiter.api.Test;

class ConstraintProviderAnalyzerTest {

  @Test
  void analyzeReturnsAnalysis() {
    ConstraintProviderAnalyzer analyzer =
        new ConstraintProviderAnalyzer(SimpleConstraintProvider.class);
    LambdaAnalysis analysis = analyzer.analyze();

    assertThat(analysis).isNotNull();
  }

  @Test
  void analyzeNoLambdaProvider() {
    ConstraintProviderAnalyzer analyzer =
        new ConstraintProviderAnalyzer(NoLambdaConstraintProvider.class);
    LambdaAnalysis analysis = analyzer.analyze();

    assertThat(analysis).isNotNull();
    assertThat(analysis.getAllLambdas()).isEmpty();
  }

  @Test
  void analysisGetShareableLambdasReturnsImmutableMap() {
    ConstraintProviderAnalyzer analyzer =
        new ConstraintProviderAnalyzer(SimpleConstraintProvider.class);
    LambdaAnalysis analysis = analyzer.analyze();

    assertThat(analysis.getShareableLambdas()).isNotNull();
  }

  @Test
  void analyzeMultipleProviders() {
    ConstraintProviderAnalyzer analyzer1 =
        new ConstraintProviderAnalyzer(SimpleConstraintProvider.class);
    ConstraintProviderAnalyzer analyzer2 =
        new ConstraintProviderAnalyzer(ComplexConstraintProvider.class);

    LambdaAnalysis analysis1 = analyzer1.analyze();
    LambdaAnalysis analysis2 = analyzer2.analyze();

    assertThat(analysis1).isNotNull();
    assertThat(analysis2).isNotNull();
  }

  @Test
  void analyzeSimpleProviderDetectsEquivalentInlineLambdas() {
    ConstraintProviderAnalyzer analyzer =
        new ConstraintProviderAnalyzer(SimpleConstraintProvider.class);

    LambdaAnalysis analysis = analyzer.analyze();

    assertThat(analysis.getShareableLambdaGroupCount()).isEqualTo(1);
    assertThat(analysis.getShareableLambdaCount()).isEqualTo(2);
  }

  @Test
  void analyzeComplexProviderDetectsEquivalentInlineLambdas() {
    ConstraintProviderAnalyzer analyzer =
        new ConstraintProviderAnalyzer(ComplexConstraintProvider.class);

    LambdaAnalysis analysis = analyzer.analyze();

    assertThat(analysis.getShareableLambdaGroupCount()).isEqualTo(1);
    assertThat(analysis.getShareableLambdaCount()).isEqualTo(3);
  }

  @Test
  void analyzeCapturedProviderSkipsCapturedLambdas() {
    ConstraintProviderAnalyzer analyzer =
        new ConstraintProviderAnalyzer(CapturedArgumentProvider.class);

    LambdaAnalysis analysis = analyzer.analyze();

    assertThat(analysis.getShareableLambdaGroupCount()).isZero();
    assertThat(analysis.getShareableLambdaCount()).isZero();
  }

  @Test
  void serializableLambdasNeverFallBackIntoShareableGroups() {
    assertThat(
            new ConstraintProviderAnalyzer(SerializableProvider.class)
                .analyze()
                .hasShareableLambdas())
        .isFalse();
  }

  @Test
  void constructorAndStaticInitializerSitesAreNotCountedAsTransformableDuplicates() {
    assertThat(
            new ConstraintProviderAnalyzer(InitializationOnlyProvider.class)
                .analyze()
                .hasShareableLambdas())
        .isFalse();
  }

  static class SerializableProvider implements ConstraintProvider {
    Predicate<String> first() {
      return (Predicate<String> & Serializable) String::isEmpty;
    }

    Predicate<String> second() {
      return (Predicate<String> & Serializable) String::isEmpty;
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }

  static class InitializationOnlyProvider implements ConstraintProvider {
    static final Predicate<String> FIRST = value -> value.isEmpty();
    final Predicate<String> second;

    InitializationOnlyProvider() {
      second = value -> value.isEmpty();
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }
}
