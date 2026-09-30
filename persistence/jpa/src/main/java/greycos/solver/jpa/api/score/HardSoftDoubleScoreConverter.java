package greycos.solver.jpa.api.score;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import greycos.solver.core.api.score.HardSoftDoubleScore;

@Converter
public class HardSoftDoubleScoreConverter
    implements AttributeConverter<HardSoftDoubleScore, String> {

  @Override
  public String convertToDatabaseColumn(HardSoftDoubleScore score) {
    if (score == null) {
      return null;
    }

    return score.toString();
  }

  @Override
  public HardSoftDoubleScore convertToEntityAttribute(String scoreString) {
    if (scoreString == null) {
      return null;
    }

    return HardSoftDoubleScore.parseScore(scoreString);
  }
}
