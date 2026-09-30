package greycos.solver.jpa.api.score;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import greycos.solver.core.api.score.SimpleDoubleScore;

@Converter
public class SimpleDoubleScoreConverter implements AttributeConverter<SimpleDoubleScore, String> {

  @Override
  public String convertToDatabaseColumn(SimpleDoubleScore score) {
    if (score == null) {
      return null;
    }

    return score.toString();
  }

  @Override
  public SimpleDoubleScore convertToEntityAttribute(String scoreString) {
    if (scoreString == null) {
      return null;
    }

    return SimpleDoubleScore.parseScore(scoreString);
  }
}
