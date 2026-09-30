package greycos.solver.jpa.api.score;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import greycos.solver.core.api.score.HardMediumSoftDoubleScore;

@Converter
public class HardMediumSoftDoubleScoreConverter
    implements AttributeConverter<HardMediumSoftDoubleScore, String> {

  @Override
  public String convertToDatabaseColumn(HardMediumSoftDoubleScore score) {
    if (score == null) {
      return null;
    }

    return score.toString();
  }

  @Override
  public HardMediumSoftDoubleScore convertToEntityAttribute(String scoreString) {
    if (scoreString == null) {
      return null;
    }

    return HardMediumSoftDoubleScore.parseScore(scoreString);
  }
}
