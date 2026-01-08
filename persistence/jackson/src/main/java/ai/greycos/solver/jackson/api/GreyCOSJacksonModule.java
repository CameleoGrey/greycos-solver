package ai.greycos.solver.jackson.api;

import ai.greycos.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.analysis.ScoreAnalysis;
import ai.greycos.solver.core.api.score.buildin.bendable.BendableScore;
import ai.greycos.solver.core.api.score.buildin.bendablebigdecimal.BendableBigDecimalScore;
import ai.greycos.solver.core.api.score.buildin.bendablelong.BendableLongScore;
import ai.greycos.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.greycos.solver.core.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScore;
import ai.greycos.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.greycos.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.greycos.solver.core.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScore;
import ai.greycos.solver.core.api.score.buildin.hardsoftlong.HardSoftLongScore;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.api.score.buildin.simplebigdecimal.SimpleBigDecimalScore;
import ai.greycos.solver.core.api.score.buildin.simplelong.SimpleLongScore;
import ai.greycos.solver.core.api.score.constraint.ConstraintRef;
import ai.greycos.solver.core.api.score.stream.common.Break;
import ai.greycos.solver.core.api.score.stream.common.LoadBalance;
import ai.greycos.solver.core.api.score.stream.common.Sequence;
import ai.greycos.solver.core.api.score.stream.common.SequenceChain;
import ai.greycos.solver.core.api.solver.RecommendedAssignment;
import ai.greycos.solver.core.api.solver.RecommendedFit;
import ai.greycos.solver.core.impl.domain.solution.DefaultConstraintWeightOverrides;
import ai.greycos.solver.core.impl.solver.DefaultRecommendedAssignment;
import ai.greycos.solver.core.preview.api.domain.solution.diff.PlanningEntityDiff;
import ai.greycos.solver.core.preview.api.domain.solution.diff.PlanningSolutionDiff;
import ai.greycos.solver.core.preview.api.domain.solution.diff.PlanningVariableDiff;
import ai.greycos.solver.jackson.api.domain.solution.ConstraintWeightOverridesSerializer;
import ai.greycos.solver.jackson.api.score.PolymorphicScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.PolymorphicScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.analysis.ScoreAnalysisJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.bendable.BendableScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.bendable.BendableScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.bendablebigdecimal.BendableBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.bendablebigdecimal.BendableBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.bendablelong.BendableLongScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.bendablelong.BendableLongScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.hardmediumsoft.HardMediumSoftScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.hardmediumsoft.HardMediumSoftScoreJsonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.hardmediumsoftbigdecimal.HardMediumSoftBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.hardsoft.HardSoftScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.hardsoft.HardSoftScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.hardsoftbigdecimal.HardSoftBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.hardsoftlong.HardSoftLongScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.hardsoftlong.HardSoftLongScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.simple.SimpleScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.simple.SimpleScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.simplebigdecimal.SimpleBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.simplebigdecimal.SimpleBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.simplelong.SimpleLongScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.simplelong.SimpleLongScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.constraint.ConstraintRefJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.constraint.ConstraintRefJacksonSerializer;
import ai.greycos.solver.jackson.api.score.stream.common.BreakJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.stream.common.BreakJacksonSerializer;
import ai.greycos.solver.jackson.api.score.stream.common.LoadBalanceJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.stream.common.LoadBalanceJacksonSerializer;
import ai.greycos.solver.jackson.api.score.stream.common.SequenceChainJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.stream.common.SequenceChainJacksonSerializer;
import ai.greycos.solver.jackson.api.score.stream.common.SequenceJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.stream.common.SequenceJacksonSerializer;
import ai.greycos.solver.jackson.api.solver.RecommendedAssignmentJacksonSerializer;
import ai.greycos.solver.jackson.api.solver.RecommendedFitJacksonSerializer;
import ai.greycos.solver.jackson.impl.domain.solution.JacksonSolutionFileIO;
import ai.greycos.solver.jackson.preview.api.domain.solution.diff.PlanningEntityDiffJacksonSerializer;
import ai.greycos.solver.jackson.preview.api.domain.solution.diff.PlanningSolutionDiffJacksonSerializer;
import ai.greycos.solver.jackson.preview.api.domain.solution.diff.PlanningVariableDiffJacksonSerializer;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

/** This class adds all Jackson serializers and deserializers. */
public class GreyCOSJacksonModule extends SimpleModule {

  /**
   * Jackson modules can be loaded automatically via {@link java.util.ServiceLoader}. This will
   * happen if you use {@link JacksonSolutionFileIO}. Otherwise, register the module with {@link
   * ObjectMapper#registerModule(Module)}.
   *
   * @return never null
   */
  public static Module createModule() {
    return new GreyCOSJacksonModule();
  }

  /**
   * @deprecated Have the module loaded automatically via {@link JacksonSolutionFileIO} or use
   *     {@link #createModule()}. This constructor will be hidden in a future major version of
   *     GreyCOS.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  @Deprecated(forRemoval = true)
  public GreyCOSJacksonModule() {
    super("GreyCOS");
    // For non-subtype Score fields/properties, we also need to record the score type
    addSerializer(Score.class, new PolymorphicScoreJacksonSerializer());
    addDeserializer(Score.class, new PolymorphicScoreJacksonDeserializer());

    addSerializer(SimpleScore.class, new SimpleScoreJacksonSerializer());
    addDeserializer(SimpleScore.class, new SimpleScoreJacksonDeserializer());
    addSerializer(SimpleLongScore.class, new SimpleLongScoreJacksonSerializer());
    addDeserializer(SimpleLongScore.class, new SimpleLongScoreJacksonDeserializer());
    addSerializer(SimpleBigDecimalScore.class, new SimpleBigDecimalScoreJacksonSerializer());
    addDeserializer(SimpleBigDecimalScore.class, new SimpleBigDecimalScoreJacksonDeserializer());
    addSerializer(HardSoftScore.class, new HardSoftScoreJacksonSerializer());
    addDeserializer(HardSoftScore.class, new HardSoftScoreJacksonDeserializer());
    addSerializer(HardSoftLongScore.class, new HardSoftLongScoreJacksonSerializer());
    addDeserializer(HardSoftLongScore.class, new HardSoftLongScoreJacksonDeserializer());
    addSerializer(HardSoftBigDecimalScore.class, new HardSoftBigDecimalScoreJacksonSerializer());
    addDeserializer(
        HardSoftBigDecimalScore.class, new HardSoftBigDecimalScoreJacksonDeserializer());
    addSerializer(HardMediumSoftScore.class, new HardMediumSoftScoreJsonSerializer());
    addDeserializer(HardMediumSoftScore.class, new HardMediumSoftScoreJacksonDeserializer());
    addSerializer(HardMediumSoftLongScore.class, new HardMediumSoftLongScoreJacksonSerializer());
    addDeserializer(
        HardMediumSoftLongScore.class, new HardMediumSoftLongScoreJacksonDeserializer());
    addSerializer(
        HardMediumSoftBigDecimalScore.class, new HardMediumSoftBigDecimalScoreJacksonSerializer());
    addDeserializer(
        HardMediumSoftBigDecimalScore.class,
        new HardMediumSoftBigDecimalScoreJacksonDeserializer());
    addSerializer(BendableScore.class, new BendableScoreJacksonSerializer());
    addDeserializer(BendableScore.class, new BendableScoreJacksonDeserializer());
    addSerializer(BendableLongScore.class, new BendableLongScoreJacksonSerializer());
    addDeserializer(BendableLongScore.class, new BendableLongScoreJacksonDeserializer());
    addSerializer(BendableBigDecimalScore.class, new BendableBigDecimalScoreJacksonSerializer());
    addDeserializer(
        BendableBigDecimalScore.class, new BendableBigDecimalScoreJacksonDeserializer());

    // Score analysis
    addSerializer(ConstraintRef.class, new ConstraintRefJacksonSerializer());
    addDeserializer(ConstraintRef.class, new ConstraintRefJacksonDeserializer());
    addSerializer(ScoreAnalysis.class, new ScoreAnalysisJacksonSerializer());
    var serializer = (JsonSerializer) new RecommendedAssignmentJacksonSerializer<>();
    addSerializer(RecommendedAssignment.class, serializer);
    addSerializer(DefaultRecommendedAssignment.class, serializer);
    addSerializer(RecommendedFit.class, (JsonSerializer) new RecommendedFitJacksonSerializer<>());

    // Constraint weights
    addSerializer(ConstraintWeightOverrides.class, new ConstraintWeightOverridesSerializer());
    addSerializer(
        DefaultConstraintWeightOverrides.class, new ConstraintWeightOverridesSerializer());

    // Constraint collectors
    addSerializer(Break.class, new BreakJacksonSerializer());
    addDeserializer(Break.class, new BreakJacksonDeserializer<>());
    addSerializer(Sequence.class, new SequenceJacksonSerializer());
    addDeserializer(Sequence.class, new SequenceJacksonDeserializer<>());
    addSerializer(SequenceChain.class, new SequenceChainJacksonSerializer());
    addDeserializer(SequenceChain.class, new SequenceChainJacksonDeserializer<>());
    addSerializer(LoadBalance.class, new LoadBalanceJacksonSerializer());
    addDeserializer(LoadBalance.class, new LoadBalanceJacksonDeserializer<>());

    // Solution diff
    addSerializer(PlanningSolutionDiff.class, new PlanningSolutionDiffJacksonSerializer());
    addSerializer(PlanningEntityDiff.class, new PlanningEntityDiffJacksonSerializer());
    addSerializer(PlanningVariableDiff.class, new PlanningVariableDiffJacksonSerializer());
  }
}
