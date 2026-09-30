package greycos.solver.jpa.api.score;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import greycos.solver.core.api.score.HardMediumSoftFloatScore;

@Converter
public class HardMediumSoftFloatScoreConverter
    implements AttributeConverter<HardMediumSoftFloatScore, String> {

  @Override
  public String convertToDatabaseColumn(HardMediumSoftFloatScore score) {
    if (score == null) {
      return null;
    }

    return score.toString();
  }

  @Override
  public HardMediumSoftFloatScore convertToEntityAttribute(String scoreString) {
    if (scoreString == null) {
      return null;
    }

    return HardMediumSoftFloatScore.parseScore(scoreString);
  }
}
