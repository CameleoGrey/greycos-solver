package ai.greycos.solver.jackson.api;

import ai.greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import ai.greycos.solver.core.api.cotwin.solution.diff.PlanningEntityDiff;
import ai.greycos.solver.core.api.cotwin.solution.diff.PlanningSolutionDiff;
import ai.greycos.solver.core.api.cotwin.solution.diff.PlanningVariableDiff;
import ai.greycos.solver.core.api.score.BendableBigDecimalScore;
import ai.greycos.solver.core.api.score.BendableScore;
import ai.greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import ai.greycos.solver.core.api.score.HardMediumSoftScore;
import ai.greycos.solver.core.api.score.HardSoftBigDecimalScore;
import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.SimpleBigDecimalScore;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.analysis.ScoreAnalysis;
import ai.greycos.solver.core.api.score.constraint.ConstraintRef;
import ai.greycos.solver.core.api.score.stream.common.Break;
import ai.greycos.solver.core.api.score.stream.common.LoadBalance;
import ai.greycos.solver.core.api.score.stream.common.Sequence;
import ai.greycos.solver.core.api.score.stream.common.SequenceChain;
import ai.greycos.solver.core.api.solver.RecommendedAssignment;
import ai.greycos.solver.core.impl.cotwin.solution.DefaultConstraintWeightOverrides;
import ai.greycos.solver.core.impl.solver.DefaultRecommendedAssignment;
import ai.greycos.solver.jackson.api.cotwin.solution.ConstraintWeightOverridesSerializer;
import ai.greycos.solver.jackson.api.score.PolymorphicScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.PolymorphicScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.analysis.ScoreAnalysisJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.BendableBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.BendableBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.BendableScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.BendableScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.HardMediumSoftBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.HardMediumSoftBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.HardMediumSoftScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.HardMediumSoftScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.HardSoftBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.HardSoftBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.HardSoftScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.HardSoftScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.SimpleBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.SimpleBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.jackson.api.score.buildin.SimpleScoreJacksonDeserializer;
import ai.greycos.solver.jackson.api.score.buildin.SimpleScoreJacksonSerializer;
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
import ai.greycos.solver.jackson.impl.cotwin.solution.JacksonSolutionFileIO;
import ai.greycos.solver.jackson.preview.api.cotwin.solution.diff.PlanningEntityDiffJacksonSerializer;
import ai.greycos.solver.jackson.preview.api.cotwin.solution.diff.PlanningSolutionDiffJacksonSerializer;
import ai.greycos.solver.jackson.preview.api.cotwin.solution.diff.PlanningVariableDiffJacksonSerializer;

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

  @SuppressWarnings({"rawtypes", "unchecked"})
  public GreyCOSJacksonModule() {
    super("GreyCOS");
    // For non-subtype Score fields/properties, we also need to record the score type
    addSerializer(Score.class, new PolymorphicScoreJacksonSerializer());
    addDeserializer(Score.class, new PolymorphicScoreJacksonDeserializer());

    addSerializer(SimpleScore.class, new SimpleScoreJacksonSerializer());
    addDeserializer(SimpleScore.class, new SimpleScoreJacksonDeserializer());
    addSerializer(SimpleBigDecimalScore.class, new SimpleBigDecimalScoreJacksonSerializer());
    addDeserializer(SimpleBigDecimalScore.class, new SimpleBigDecimalScoreJacksonDeserializer());
    addSerializer(HardSoftScore.class, new HardSoftScoreJacksonSerializer());
    addDeserializer(HardSoftScore.class, new HardSoftScoreJacksonDeserializer());
    addSerializer(HardSoftBigDecimalScore.class, new HardSoftBigDecimalScoreJacksonSerializer());
    addDeserializer(
        HardSoftBigDecimalScore.class, new HardSoftBigDecimalScoreJacksonDeserializer());
    addSerializer(HardMediumSoftScore.class, new HardMediumSoftScoreJacksonSerializer());
    addDeserializer(HardMediumSoftScore.class, new HardMediumSoftScoreJacksonDeserializer());
    addSerializer(
        HardMediumSoftBigDecimalScore.class, new HardMediumSoftBigDecimalScoreJacksonSerializer());
    addDeserializer(
        HardMediumSoftBigDecimalScore.class,
        new HardMediumSoftBigDecimalScoreJacksonDeserializer());
    addSerializer(BendableScore.class, new BendableScoreJacksonSerializer());
    addDeserializer(BendableScore.class, new BendableScoreJacksonDeserializer());
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
