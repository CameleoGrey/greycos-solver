package greycos.solver.quarkus.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import greycos.solver.core.api.solver.RecommendedAssignment;
import greycos.solver.core.impl.score.analysis.DefaultConstraintAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultMatchAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultScoreAnalysis;
import greycos.solver.core.impl.solver.DefaultRecommendedAssignment;
import greycos.solver.core.impl.util.Pair;
import greycos.solver.quarkus.jackson.cotwin.solution.AbstractConstraintWeightOverridesDeserializer;
import greycos.solver.quarkus.jackson.score.analysis.AbstractScoreAnalysisJacksonDeserializer;
import greycos.solver.quarkus.jackson.solver.AbstractRecommendedAssignmentJacksonDeserializer;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;

class GreyCOSJacksonModuleTest extends AbstractJacksonRoundTripTest {

  /**
   * According to official specification (see {@link Class#getDeclaredMethods()}), "The elements in
   * the returned array are not sorted and are not in any particular order." Enabling {@link
   * MapperFeature#SORT_PROPERTIES_ALPHABETICALLY} makes this test work on all JDK implementations.
   */
  @Test
  void polymorphicScore() {
    var objectMapper =
        JsonMapper.builder()
            .addModule(GreyCOSJacksonModule.createModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    var input = new TestGreyCOSJacksonModuleWrapper();
    input.setBendableScore(BendableScore.of(new long[] {1000, 200}, new long[] {34}));
    input.setHardSoftScore(HardSoftScore.of(-1, -20));
    input.setPolymorphicScore(HardSoftScore.of(-20, -300));
    var output = serializeAndDeserialize(objectMapper, input);
    assertThat(output.getBendableScore())
        .isEqualTo(BendableScore.of(new long[] {1000, 200}, new long[] {34}));
    assertThat(output.getHardSoftScore()).isEqualTo(HardSoftScore.of(-1, -20));
    assertThat(output.getPolymorphicScore()).isEqualTo(HardSoftScore.of(-20, -300));

    input.setPolymorphicScore(
        BendableScore.of(new long[] {-1, -20}, new long[] {-300, -4000, -50000}));
    output = serializeAndDeserialize(objectMapper, input);
    assertThat(output.getBendableScore())
        .isEqualTo(BendableScore.of(new long[] {1000, 200}, new long[] {34}));
    assertThat(output.getHardSoftScore()).isEqualTo(HardSoftScore.of(-1, -20));
    assertThat(output.getPolymorphicScore())
        .isEqualTo(BendableScore.of(new long[] {-1, -20}, new long[] {-300, -4000, -50000}));
  }

  @Test
  void scoreAnalysisWithoutMatches() throws JsonProcessingException {
    var objectMapper =
        JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .defaultPropertyInclusion(
                JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .addModule(GreyCOSJacksonModule.createModule())
            .addModule(new CustomJacksonModule())
            .build();

    var constraintRef1 = ConstraintRef.of("constraint1");
    var constraintRef2 = ConstraintRef.of("constraint2");
    var constraintAnalysis1 =
        new DefaultConstraintAnalysis<>(
            constraintRef1, HardSoftScore.ofSoft(1), HardSoftScore.ofSoft(2), null);
    var constraintAnalysis2 =
        new DefaultConstraintAnalysis<>(
            constraintRef2, HardSoftScore.ofHard(1), HardSoftScore.ofHard(1), null, 2);
    var originalScoreAnalysis =
        new DefaultScoreAnalysis<>(
            HardSoftScore.of(1, 2),
            Map.of(constraintRef1, constraintAnalysis1, constraintRef2, constraintAnalysis2));

    // Hardest constraints first, package name second.
    var serialized = objectMapper.writeValueAsString(originalScoreAnalysis);
    assertThat(serialized)
        .isEqualToIgnoringWhitespace(
            """
                        {
                           "score" : "1hard/2soft",
                           "initialized" : true,
                           "constraints" : [ {
                             "id" : "constraint2",
                             "weight" : "1hard/0soft",
                             "score" : "1hard/0soft",
                             "matchCount" : 2
                           }, {
                             "id" : "constraint1",
                             "weight" : "0hard/1soft",
                             "score" : "0hard/2soft"
                           } ]
                         }""");

    ScoreAnalysis<HardSoftScore> deserialized =
        objectMapper.readValue(serialized, ScoreAnalysis.class);
    assertThat(deserialized).isEqualTo(originalScoreAnalysis);
  }

  @Test
  void scoreAnalysisRejectsLegacyConstraintName() {
    var objectMapper =
        JsonMapper.builder()
            .addModule(GreyCOSJacksonModule.createModule())
            .addModule(new CustomJacksonModule())
            .build();

    assertThatThrownBy(
            () ->
                objectMapper.readValue(
                    """
                {
                  "score": "0hard/0soft",
                  "initialized": true,
                  "constraints": [ {
                    "name": "legacyConstraintName",
                    "weight": "0hard/1soft",
                    "score": "0hard/0soft"
                  } ]
                }
                """,
                    ScoreAnalysis.class))
        .hasMessageContaining("required JSON property (id)");
  }

  @Test
  void scoreAnalysisWithMatches() throws JsonProcessingException {
    var objectMapper =
        JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .defaultPropertyInclusion(
                JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .addModule(GreyCOSJacksonModule.createModule())
            .addModule(new CustomJacksonModule())
            .build();

    var originalScoreAnalysis = getScoreAnalysis();
    var serialized = objectMapper.writeValueAsString(originalScoreAnalysis);
    assertThat(serialized).isEqualToIgnoringWhitespace(getSerializedScoreAnalysis());

    ScoreAnalysis<HardSoftScore> deserialized =
        objectMapper.readValue(serialized, ScoreAnalysis.class);
    assertThat(deserialized).isEqualTo(originalScoreAnalysis);
  }

  @Test
  void scoreAnalysisWithDiffMatchCounts() throws JsonProcessingException {
    var objectMapper =
        JsonMapper.builder()
            .addModule(GreyCOSJacksonModule.createModule())
            .addModule(new CustomJacksonModule())
            .build();

    var negativeCountConstraintRef = ConstraintRef.of("negativeCount");
    var negativeMatchScore = HardSoftScore.ofHard(-1);
    var negativeMatch =
        new DefaultMatchAnalysis<>(
            negativeCountConstraintRef,
            negativeMatchScore,
            DefaultConstraintJustification.of(negativeMatchScore, "removed"));
    var negativeCountConstraintAnalysis =
        new DefaultConstraintAnalysis<>(
            negativeCountConstraintRef,
            HardSoftScore.ofHard(1),
            negativeMatchScore,
            List.of(negativeMatch),
            -1);

    var unequalCountConstraintRef = ConstraintRef.of("unequalCount");
    var unequalCountMatchScore = HardSoftScore.ofSoft(2);
    var unequalCountMatch =
        new DefaultMatchAnalysis<>(
            unequalCountConstraintRef,
            unequalCountMatchScore,
            DefaultConstraintJustification.of(unequalCountMatchScore, "changed"));
    var unequalCountConstraintAnalysis =
        new DefaultConstraintAnalysis<>(
            unequalCountConstraintRef,
            HardSoftScore.ofSoft(1),
            unequalCountMatchScore,
            List.of(unequalCountMatch),
            3);

    var originalScoreAnalysis =
        new DefaultScoreAnalysis<>(
            HardSoftScore.of(-1, 2),
            Map.of(
                negativeCountConstraintRef,
                negativeCountConstraintAnalysis,
                unequalCountConstraintRef,
                unequalCountConstraintAnalysis));

    var serialized = objectMapper.writeValueAsString(originalScoreAnalysis);
    assertThat(serialized).contains("\"matchCount\":-1", "\"matchCount\":3");

    ScoreAnalysis<HardSoftScore> deserialized =
        objectMapper.readValue(serialized, ScoreAnalysis.class);
    assertThat(deserialized).isEqualTo(originalScoreAnalysis);
  }

  @Test
  void scoreAnalysisWithMatchesRequiresMatchCount() {
    var objectMapper =
        JsonMapper.builder()
            .addModule(GreyCOSJacksonModule.createModule())
            .addModule(new CustomJacksonModule())
            .build();

    assertThatThrownBy(
            () ->
                objectMapper.readValue(
                    """
                {
                  "score": "0hard/0soft",
                  "initialized": true,
                  "constraints": [ {
                    "id": "missingMatchCount",
                    "weight": "0hard/1soft",
                    "score": "0hard/0soft",
                    "matches": []
                  } ]
                }
                """,
                    ScoreAnalysis.class))
        .hasMessageContaining("required JSON property (matchCount)");
  }

  @Test
  void scoreAnalysisRejectsNonIntegralMatchCount() {
    var objectMapper =
        JsonMapper.builder()
            .addModule(GreyCOSJacksonModule.createModule())
            .addModule(new CustomJacksonModule())
            .build();

    assertThatThrownBy(
            () ->
                objectMapper.readValue(
                    """
                {
                  "score": "0hard/0soft",
                  "initialized": true,
                  "constraints": [ {
                    "id": "invalidMatchCount",
                    "weight": "0hard/1soft",
                    "score": "0hard/0soft",
                    "matches": [],
                    "matchCount": "1"
                  } ]
                }
                """,
                    ScoreAnalysis.class))
        .hasMessageContaining("matchCount", "must be a 32-bit integer");
  }

  private static ScoreAnalysis<HardSoftScore> getScoreAnalysis() {
    var constraintRef1 = ConstraintRef.of("constraint1");
    var constraintRef2 = ConstraintRef.of("constraint2");
    var matchAnalysis1 =
        new DefaultMatchAnalysis<>(
            constraintRef1,
            HardSoftScore.ofHard(1),
            DefaultConstraintJustification.of(HardSoftScore.ofHard(1), "A", "B"));
    var matchAnalysis2 =
        new DefaultMatchAnalysis<>(
            constraintRef1,
            HardSoftScore.ofHard(1),
            DefaultConstraintJustification.of(HardSoftScore.ofHard(1), "B", "C", "D"));
    var matchAnalysis3 =
        new DefaultMatchAnalysis<>(
            constraintRef2,
            HardSoftScore.ofSoft(1),
            DefaultConstraintJustification.of(HardSoftScore.ofSoft(1), "D"));
    var matchAnalysis4 =
        new DefaultMatchAnalysis<>(
            constraintRef2,
            HardSoftScore.ofSoft(3),
            DefaultConstraintJustification.of(HardSoftScore.ofSoft(3), "A", "C"));
    var constraintAnalysis1 =
        new DefaultConstraintAnalysis<>(
            constraintRef1,
            HardSoftScore.ofHard(1),
            HardSoftScore.ofHard(2),
            List.of(matchAnalysis1, matchAnalysis2));
    var constraintAnalysis2 =
        new DefaultConstraintAnalysis<>(
            constraintRef2,
            HardSoftScore.ofSoft(1),
            HardSoftScore.ofSoft(4),
            List.of(matchAnalysis3, matchAnalysis4));
    return new DefaultScoreAnalysis<>(
        HardSoftScore.of(2, 4),
        Map.of(constraintRef1, constraintAnalysis1, constraintRef2, constraintAnalysis2));
  }

  private static String getSerializedScoreAnalysis() {
    return """
                {
                  "score" : "2hard/4soft",
                  "initialized" : true,
                  "constraints" : [ {
                    "id" : "constraint1",
                    "weight" : "1hard/0soft",
                    "score" : "2hard/0soft",
                    "matches" : [ {
                            "score" : "1hard/0soft",
                            "justification" : [ "A", "B" ]
                        }, {
                            "score" : "1hard/0soft",
                            "justification" : [ "B", "C", "D" ]
                        }
                     ],
                    "matchCount" : 2
                  }, {
                    "id" : "constraint2",
                    "weight" : "0hard/1soft",
                    "score" : "0hard/4soft",
                    "matches" : [ {
                            "score" : "0hard/1soft",
                            "justification" : [ "D" ]
                        }, {
                            "score" : "0hard/3soft",
                            "justification" : [ "A", "C" ]
                    } ],
                    "matchCount" : 2
                  } ]
                }""";
  }

  @Test
  void recommendedAssignment() throws JsonProcessingException {
    var objectMapper =
        JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .defaultPropertyInclusion(
                JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .addModule(GreyCOSJacksonModule.createModule())
            .addModule(new CustomJacksonModule())
            .build();

    var proposition = new Pair<>("A", "1");
    var originalScoreAnalysis = getScoreAnalysis();
    var originalRecommendedAssignment =
        new DefaultRecommendedAssignment<>(0, proposition, originalScoreAnalysis);
    var fitList = List.of(originalRecommendedAssignment);

    var serialized = objectMapper.writeValueAsString(fitList);
    assertThat(serialized)
        .isEqualToIgnoringWhitespace(
            """
                        [ {
                             "proposition" : {
                               "key" : "A",
                               "value" : "1"
                             },
                             "scoreDiff" : %s
                           } ]"""
                .formatted(getSerializedScoreAnalysis()));

    List<RecommendedAssignment<Pair<String, String>, HardSoftScore>> deserialized =
        objectMapper.readValue(
            serialized,
            TypeFactory.defaultInstance()
                .constructCollectionType(List.class, RecommendedAssignment.class));
    assertThat(deserialized).hasSize(1).first().isEqualTo(originalRecommendedAssignment);
  }

  @Test
  void constraintWeightOverrides() throws JsonProcessingException {
    var objectMapper =
        JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .defaultPropertyInclusion(
                JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .addModule(GreyCOSJacksonModule.createModule())
            .addModule(new CustomJacksonModule())
            .build();

    var constraintWeightOverrides =
        ConstraintWeightOverrides.of(
            Map.of(
                "constraint1", HardSoftScore.ofHard(1),
                "constraint2", HardSoftScore.ofSoft(2)));

    var serialized = objectMapper.writeValueAsString(constraintWeightOverrides);
    assertThat(serialized)
        .isEqualToIgnoringWhitespace(
            """
                        {
                            "constraint1":"1hard/0soft",
                            "constraint2":"0hard/2soft"
                        }""");

    var deserialized = objectMapper.readValue(serialized, ConstraintWeightOverrides.class);
    assertThat(deserialized).isEqualTo(constraintWeightOverrides);
  }

  public static final class CustomJacksonModule extends SimpleModule {

    public CustomJacksonModule() {
      super("GreyCOS Custom");
      addDeserializer(ScoreAnalysis.class, new CustomScoreAnalysisJacksonDeserializer());
      addDeserializer(
          RecommendedAssignment.class, new CustomRecommendedAssignmentJacksonDeserializer());
      addDeserializer(
          ConstraintWeightOverrides.class, new CustomConstraintWeightOverridesDeserializer());
    }
  }

  public static final class CustomConstraintWeightOverridesDeserializer
      extends AbstractConstraintWeightOverridesDeserializer<HardSoftScore> {

    @Override
    protected HardSoftScore parseScore(String scoreString) {
      return HardSoftScore.parseScore(scoreString);
    }
  }

  public static final class CustomScoreAnalysisJacksonDeserializer
      extends AbstractScoreAnalysisJacksonDeserializer<HardSoftScore> {

    @Override
    protected HardSoftScore parseScore(String scoreString) {
      return HardSoftScore.parseScore(scoreString);
    }

    @Override
    protected ConstraintJustification deserializeConstraintJustification(
        ConstraintRef constraintRef,
        JsonNode constraintJustificationNode,
        DeserializationContext context,
        HardSoftScore score) {
      List<Object> justificationList = new ArrayList<>();
      constraintJustificationNode.forEach(factNode -> justificationList.add(factNode.asText()));
      return DefaultConstraintJustification.of(score, justificationList);
    }
  }

  public static final class CustomRecommendedAssignmentJacksonDeserializer
      extends AbstractRecommendedAssignmentJacksonDeserializer<
          Pair<String, String>, HardSoftScore> {

    @Override
    protected Class<Pair<String, String>> getPropositionClass() {
      return (Class) Pair.class;
    }
  }

  public static class TestGreyCOSJacksonModuleWrapper {

    private BendableScore bendableScore;
    private HardSoftScore hardSoftScore;
    private Score polymorphicScore;

    @SuppressWarnings("unused")
    private TestGreyCOSJacksonModuleWrapper() {}

    public BendableScore getBendableScore() {
      return bendableScore;
    }

    public void setBendableScore(BendableScore bendableScore) {
      this.bendableScore = bendableScore;
    }

    public HardSoftScore getHardSoftScore() {
      return hardSoftScore;
    }

    public void setHardSoftScore(HardSoftScore hardSoftScore) {
      this.hardSoftScore = hardSoftScore;
    }

    public Score getPolymorphicScore() {
      return polymorphicScore;
    }

    public void setPolymorphicScore(Score polymorphicScore) {
      this.polymorphicScore = polymorphicScore;
    }
  }
}
