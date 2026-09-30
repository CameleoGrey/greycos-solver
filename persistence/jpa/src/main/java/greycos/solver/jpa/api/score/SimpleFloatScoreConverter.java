package greycos.solver.jpa.api.score;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import greycos.solver.core.api.score.SimpleFloatScore;

@Converter
public class SimpleFloatScoreConverter implements AttributeConverter<SimpleFloatScore, String> {

  @Override
  public String convertToDatabaseColumn(SimpleFloatScore score) {
    if (score == null) {
      return null;
    }

    return score.toString();
  }

  @Override
  public SimpleFloatScore convertToEntityAttribute(String scoreString) {
    if (scoreString == null) {
      return null;
    }

    return SimpleFloatScore.parseScore(scoreString);
  }
}
