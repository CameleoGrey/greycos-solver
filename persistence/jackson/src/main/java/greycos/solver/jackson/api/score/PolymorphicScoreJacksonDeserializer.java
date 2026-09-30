package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.BendableBigDecimalScore;
import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleBigDecimalScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.score.SimpleScore;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson binding support for a {@link Score} type (but not a subtype). For a {@link Score} subtype
 * field, use {@link HardSoftScoreJacksonDeserializer} or similar instead.
 *
 * <p>For example: use {@code @JsonSerialize(using =
 * PolymorphicScoreJacksonSerializer.class) @JsonDeserialize(using =
 * PolymorphicScoreJacksonDeserializer.class)} on a {@code Score score} field which contains a
 * {@link HardSoftScore} instance and it will marshalled to JSON as {@code
 * "score":{"type":"HARD_SOFT",score:"-999hard/-999soft"}}.
 *
 * @see Score
 * @see PolymorphicScoreJacksonDeserializer
 */
public class PolymorphicScoreJacksonDeserializer extends ValueDeserializer<Score> {

  @Override
  public Score deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    parser.nextToken();
    String scoreClassSimpleName = parser.currentName();
    parser.nextToken();
    String scoreString = parser.getValueAsString();
    var score = parseScore(scoreClassSimpleName, scoreString);
    if (parser.nextToken() != JsonToken.END_OBJECT) {
      throw new IllegalArgumentException(
          "A polymorphic score must contain exactly one score type.");
    }
    return score;
  }

  private Score parseScore(String scoreClassSimpleName, String scoreString) {
    if (scoreClassSimpleName.equals(SimpleScore.class.getSimpleName())) {
      return SimpleScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(SimpleBigDecimalScore.class.getSimpleName())) {
      return SimpleBigDecimalScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(HardSoftScore.class.getSimpleName())) {
      return HardSoftScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(HardSoftBigDecimalScore.class.getSimpleName())) {
      return HardSoftBigDecimalScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(HardMediumSoftScore.class.getSimpleName())) {
      return HardMediumSoftScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(HardMediumSoftBigDecimalScore.class.getSimpleName())) {
      return HardMediumSoftBigDecimalScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(BendableScore.class.getSimpleName())) {
      return BendableScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(BendableBigDecimalScore.class.getSimpleName())) {
      return BendableBigDecimalScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(SimpleFloatScore.class.getSimpleName())) {
      return SimpleFloatScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(SimpleDoubleScore.class.getSimpleName())) {
      return SimpleDoubleScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(HardSoftFloatScore.class.getSimpleName())) {
      return HardSoftFloatScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(HardSoftDoubleScore.class.getSimpleName())) {
      return HardSoftDoubleScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(HardMediumSoftFloatScore.class.getSimpleName())) {
      return HardMediumSoftFloatScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(HardMediumSoftDoubleScore.class.getSimpleName())) {
      return HardMediumSoftDoubleScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(BendableFloatScore.class.getSimpleName())) {
      return BendableFloatScore.parseScore(scoreString);
    } else if (scoreClassSimpleName.equals(BendableDoubleScore.class.getSimpleName())) {
      return BendableDoubleScore.parseScore(scoreString);
    } else {
      throw new IllegalArgumentException(
          "Unrecognized scoreClassSimpleName (%s) for scoreString (%s)."
              .formatted(scoreClassSimpleName, scoreString));
    }
  }
}
