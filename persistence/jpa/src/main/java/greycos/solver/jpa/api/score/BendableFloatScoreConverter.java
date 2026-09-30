package greycos.solver.jpa.api.score;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import greycos.solver.core.api.score.BendableFloatScore;

@Converter
public class BendableFloatScoreConverter implements AttributeConverter<BendableFloatScore, String> {

  @Override
  public String convertToDatabaseColumn(BendableFloatScore score) {
    if (score == null) {
      return null;
    }

    return score.toString();
  }

  @Override
  public BendableFloatScore convertToEntityAttribute(String scoreString) {
    if (scoreString == null) {
      return null;
    }

    return BendableFloatScore.parseScore(scoreString);
  }
}
