package ai.greycos.solver.jackson.api.cotwin.solution;

import java.util.LinkedHashMap;

import ai.greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import ai.greycos.solver.core.api.score.Score;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Extend this to implement {@link ConstraintWeightOverrides} deserialization specific for your
 * cotwin.
 *
 * @param <Score_>
 */
public abstract class AbstractConstraintWeightOverridesDeserializer<Score_ extends Score<Score_>>
    extends ValueDeserializer<ConstraintWeightOverrides<Score_>> {

  @Override
  public final ConstraintWeightOverrides<Score_> deserialize(
      JsonParser p, DeserializationContext ctxt) throws JacksonException {
    var resultMap = new LinkedHashMap<String, Score_>();
    JsonNode node = p.readValueAsTree();
    node.properties()
        .iterator()
        .forEachRemaining(
            entry -> {
              var constraintId = entry.getKey();
              var weight = parseScore(entry.getValue().asString());
              resultMap.put(constraintId, weight);
            });
    return ConstraintWeightOverrides.of(resultMap);
  }

  /**
   * The cotwin is based on a single {@link Score} subtype. This method is responsible for parsing
   * the score string into that subtype.
   *
   * @param scoreString never null
   * @return never null
   */
  protected abstract Score_ parseScore(String scoreString);
}
