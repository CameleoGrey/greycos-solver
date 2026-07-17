package ai.greycos.solver.quarkus.jackson.score.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.analysis.ConstraintAnalysis;
import ai.greycos.solver.core.api.score.analysis.MatchAnalysis;
import ai.greycos.solver.core.api.score.analysis.ScoreAnalysis;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintJustification;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.ConstraintRef;
import ai.greycos.solver.core.impl.score.analysis.DefaultConstraintAnalysis;
import ai.greycos.solver.core.impl.score.analysis.DefaultMatchAnalysis;
import ai.greycos.solver.core.impl.score.analysis.DefaultScoreAnalysis;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Extend this to implement {@link ScoreAnalysis} deserialization specific for your cotwin.
 *
 * @param <Score_>
 */
public abstract class AbstractScoreAnalysisJacksonDeserializer<Score_ extends Score<Score_>>
    extends JsonDeserializer<ScoreAnalysis<Score_>> {

  @Override
  public final ScoreAnalysis<Score_> deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    JsonNode node = p.readValueAsTree();
    var score = parseScore(required(node, "score").asText());
    var initialized = required(node, "initialized").asBoolean();
    var constraintAnalysisList = new LinkedHashMap<ConstraintRef, ConstraintAnalysis<Score_>>();
    for (var constraintNode : required(node, "constraints")) {
      var constraintId = required(constraintNode, "id").asText();
      var constraintRef = ConstraintRef.of(constraintId);
      var constraintWeight = parseScore(required(constraintNode, "weight").asText());
      var constraintScore = parseScore(required(constraintNode, "score").asText());
      var matchScoreList = new ArrayList<MatchAnalysis<Score_>>();
      var matchesNode = constraintNode.get("matches");
      var matchCountNode = constraintNode.get("matchCount");
      if (matchesNode == null) {
        constraintAnalysisList.put(
            constraintRef,
            new DefaultConstraintAnalysis<>(
                constraintRef,
                constraintWeight,
                constraintScore,
                null,
                matchCountNode == null ? -1 : parseMatchCount(matchCountNode)));
      } else {
        var matchCount = parseMatchCount(required(constraintNode, "matchCount"));
        for (var matchNode : matchesNode) {
          var matchScore = parseScore(required(matchNode, "score").asText());
          var justificationNode = required(matchNode, "justification");
          var parsedJustification =
              Objects.requireNonNull(
                  deserializeConstraintJustification(
                      constraintRef, justificationNode, ctxt, matchScore),
                  () ->
                      "The constraint justification deserializer returned null for constraint (%s)."
                          .formatted(constraintRef));
          matchScoreList.add(
              new DefaultMatchAnalysis<>(constraintRef, matchScore, parsedJustification));
        }
        constraintAnalysisList.put(
            constraintRef,
            new DefaultConstraintAnalysis<>(
                constraintRef, constraintWeight, constraintScore, matchScoreList, matchCount));
      }
    }
    return new DefaultScoreAnalysis<>(score, constraintAnalysisList, initialized);
  }

  /**
   * The cotwin is based on a single {@link Score} subtype. This method is responsible for parsing
   * the score string into that subtype.
   *
   * @param scoreString never null
   * @return never null
   */
  protected abstract Score_ parseScore(String scoreString);

  /**
   * Each {@link Constraint} in the {@link ConstraintProvider} is justified with a custom
   * implementation {@link ConstraintJustification}. This method is responsible for deserializing
   * the justification.
   *
   * @param constraintRef never null
   * @param constraintJustificationNode never null
   * @param context never null
   * @param score never null
   * @return never null
   */
  protected abstract ConstraintJustification deserializeConstraintJustification(
      ConstraintRef constraintRef,
      JsonNode constraintJustificationNode,
      DeserializationContext context,
      Score_ score)
      throws IOException;

  private static JsonNode required(JsonNode parent, String propertyName) {
    var value = parent.get(propertyName);
    if (value == null || value.isNull()) {
      throw new IllegalArgumentException(
          "The required JSON property (%s) is missing or null.".formatted(propertyName));
    }
    return value;
  }

  private static int parseMatchCount(JsonNode matchCountNode) {
    if (!matchCountNode.isIntegralNumber() || !matchCountNode.canConvertToInt()) {
      throw new IllegalArgumentException(
          "The JSON property (matchCount) must be a 32-bit integer, but was (%s)."
              .formatted(matchCountNode));
    }
    return matchCountNode.intValue();
  }
}
