package greycos.solver.jackson.api;

import greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import greycos.solver.core.api.score.BendableBigDecimalScore;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.common.Break;
import greycos.solver.core.api.score.stream.common.LoadBalance;
import greycos.solver.core.api.score.stream.common.Sequence;
import greycos.solver.core.api.score.stream.common.SequenceChain;
import greycos.solver.core.api.solver.RecommendedAssignment;
import greycos.solver.core.impl.cotwin.solution.DefaultConstraintWeightOverrides;
import greycos.solver.core.impl.solver.DefaultRecommendedAssignment;
import greycos.solver.core.preview.api.cotwin.solution.diff.PlanningEntityDiff;
import greycos.solver.core.preview.api.cotwin.solution.diff.PlanningSolutionDiff;
import greycos.solver.core.preview.api.cotwin.solution.diff.PlanningVariableDiff;
import greycos.solver.jackson.api.cotwin.solution.ConstraintWeightOverridesSerializer;
import greycos.solver.jackson.api.score.BendableBigDecimalScoreJacksonDeserializer;
import greycos.solver.jackson.api.score.BendableBigDecimalScoreJacksonSerializer;
import greycos.solver.jackson.api.score.BendableScoreJacksonDeserializer;
import greycos.solver.jackson.api.score.BendableScoreJacksonSerializer;
import greycos.solver.jackson.api.score.HardMediumSoftBigDecimalScoreJacksonDeserializer;
import greycos.solver.jackson.api.score.HardMediumSoftBigDecimalScoreJacksonSerializer;
import greycos.solver.jackson.api.score.HardMediumSoftScoreJacksonDeserializer;
import greycos.solver.jackson.api.score.HardMediumSoftScoreJacksonSerializer;
import greycos.solver.jackson.api.score.HardSoftBigDecimalScoreJacksonDeserializer;
import greycos.solver.jackson.api.score.HardSoftBigDecimalScoreJacksonSerializer;
import greycos.solver.jackson.api.score.HardSoftScoreJacksonDeserializer;
import greycos.solver.jackson.api.score.HardSoftScoreJacksonSerializer;
import greycos.solver.jackson.api.score.PolymorphicScoreJacksonDeserializer;
import greycos.solver.jackson.api.score.PolymorphicScoreJacksonSerializer;
import greycos.solver.jackson.api.score.SimpleBigDecimalScoreJacksonDeserializer;
import greycos.solver.jackson.api.score.SimpleBigDecimalScoreJacksonSerializer;
import greycos.solver.jackson.api.score.SimpleScoreJacksonDeserializer;
import greycos.solver.jackson.api.score.SimpleScoreJacksonSerializer;
import greycos.solver.jackson.api.score.analysis.ScoreAnalysisJacksonSerializer;
import greycos.solver.jackson.api.score.constraint.ConstraintRefJacksonDeserializer;
import greycos.solver.jackson.api.score.constraint.ConstraintRefJacksonSerializer;
import greycos.solver.jackson.api.score.stream.common.BreakJacksonDeserializer;
import greycos.solver.jackson.api.score.stream.common.BreakJacksonSerializer;
import greycos.solver.jackson.api.score.stream.common.LoadBalanceJacksonDeserializer;
import greycos.solver.jackson.api.score.stream.common.LoadBalanceJacksonSerializer;
import greycos.solver.jackson.api.score.stream.common.SequenceChainJacksonDeserializer;
import greycos.solver.jackson.api.score.stream.common.SequenceChainJacksonSerializer;
import greycos.solver.jackson.api.score.stream.common.SequenceJacksonDeserializer;
import greycos.solver.jackson.api.score.stream.common.SequenceJacksonSerializer;
import greycos.solver.jackson.api.solver.RecommendedAssignmentJacksonSerializer;
import greycos.solver.jackson.impl.cotwin.solution.JacksonSolutionFileIO;
import greycos.solver.jackson.preview.api.cotwin.solution.diff.PlanningEntityDiffJacksonSerializer;
import greycos.solver.jackson.preview.api.cotwin.solution.diff.PlanningSolutionDiffJacksonSerializer;
import greycos.solver.jackson.preview.api.cotwin.solution.diff.PlanningVariableDiffJacksonSerializer;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/** This class adds all Jackson serializers and deserializers. */
public class GreyCOSJacksonModule extends SimpleModule {

  /**
   * Jackson modules can be loaded automatically via {@link java.util.ServiceLoader}. This will
   * happen if you use {@link JacksonSolutionFileIO}. Otherwise, register the module with {@link
   * JsonMapper.Builder#addModule(JacksonModule)}.
   *
   * @return never null
   */
  public static JacksonModule createModule() {
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
    var recommendationSerializer = (ValueSerializer) new RecommendedAssignmentJacksonSerializer<>();
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
