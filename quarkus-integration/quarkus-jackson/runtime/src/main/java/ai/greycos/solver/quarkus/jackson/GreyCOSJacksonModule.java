package ai.greycos.solver.quarkus.jackson;

import ai.greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
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
import ai.greycos.solver.core.api.score.stream.ConstraintRef;
import ai.greycos.solver.core.api.score.stream.common.Break;
import ai.greycos.solver.core.api.score.stream.common.LoadBalance;
import ai.greycos.solver.core.api.score.stream.common.Sequence;
import ai.greycos.solver.core.api.score.stream.common.SequenceChain;
import ai.greycos.solver.core.api.solver.RecommendedAssignment;
import ai.greycos.solver.core.impl.cotwin.solution.DefaultConstraintWeightOverrides;
import ai.greycos.solver.core.impl.solver.DefaultRecommendedAssignment;
import ai.greycos.solver.core.preview.api.cotwin.solution.diff.PlanningEntityDiff;
import ai.greycos.solver.core.preview.api.cotwin.solution.diff.PlanningSolutionDiff;
import ai.greycos.solver.core.preview.api.cotwin.solution.diff.PlanningVariableDiff;
import ai.greycos.solver.quarkus.jackson.cotwin.solution.ConstraintWeightOverridesSerializer;
import ai.greycos.solver.quarkus.jackson.diff.PlanningEntityDiffJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.diff.PlanningSolutionDiffJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.diff.PlanningVariableDiffJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.BendableBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.BendableBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.BendableScoreJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.BendableScoreJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.HardMediumSoftBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.HardMediumSoftBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.HardMediumSoftScoreJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.HardMediumSoftScoreJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.HardSoftBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.HardSoftBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.HardSoftScoreJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.HardSoftScoreJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.PolymorphicScoreJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.PolymorphicScoreJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.SimpleBigDecimalScoreJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.SimpleBigDecimalScoreJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.SimpleScoreJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.SimpleScoreJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.analysis.ScoreAnalysisJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.constraint.ConstraintRefJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.constraint.ConstraintRefJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.stream.common.BreakJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.stream.common.BreakJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.stream.common.LoadBalanceJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.stream.common.LoadBalanceJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.stream.common.SequenceChainJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.stream.common.SequenceChainJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.score.stream.common.SequenceJacksonDeserializer;
import ai.greycos.solver.quarkus.jackson.score.stream.common.SequenceJacksonSerializer;
import ai.greycos.solver.quarkus.jackson.solution.JacksonSolutionFileIO;
import ai.greycos.solver.quarkus.jackson.solver.RecommendedAssignmentJacksonSerializer;

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

  public GreyCOSJacksonModule() {
    this("GreyCOS");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected GreyCOSJacksonModule(String name) {
    super(name);
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

    // Constraint weights
    addSerializer(ConstraintRef.class, new ConstraintRefJacksonSerializer());
    addDeserializer(ConstraintRef.class, new ConstraintRefJacksonDeserializer());
    addSerializer(ScoreAnalysis.class, new ScoreAnalysisJacksonSerializer());
    var recommendationSerializer = (JsonSerializer) new RecommendedAssignmentJacksonSerializer<>();
    addSerializer(RecommendedAssignment.class, recommendationSerializer);
    addSerializer(DefaultRecommendedAssignment.class, recommendationSerializer);
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

    // Native GreyCOS solution diff support.
    addSerializer(PlanningSolutionDiff.class, new PlanningSolutionDiffJacksonSerializer());
    addSerializer(PlanningEntityDiff.class, new PlanningEntityDiffJacksonSerializer());
    addSerializer(PlanningVariableDiff.class, new PlanningVariableDiffJacksonSerializer());
  }
}
