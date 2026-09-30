package greycos.solver.jpa.api.score;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import greycos.solver.core.api.score.HardSoftFloatScore;

@Converter
public class HardSoftFloatScoreConverter implements AttributeConverter<HardSoftFloatScore, String> {

  @Override
  public String convertToDatabaseColumn(HardSoftFloatScore score) {
    if (score == null) {
      return null;
    }

    return score.toString();
  }

  @Override
  public HardSoftFloatScore convertToEntityAttribute(String scoreString) {
    if (scoreString == null) {
      return null;
    }

    return HardSoftFloatScore.parseScore(scoreString);
  }
}
