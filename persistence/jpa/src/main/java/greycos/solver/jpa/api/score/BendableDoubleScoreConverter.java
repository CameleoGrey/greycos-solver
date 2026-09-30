package greycos.solver.jpa.api.score;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import greycos.solver.core.api.score.BendableDoubleScore;

@Converter
public class BendableDoubleScoreConverter
    implements AttributeConverter<BendableDoubleScore, String> {

  @Override
  public String convertToDatabaseColumn(BendableDoubleScore score) {
    if (score == null) {
      return null;
    }

    return score.toString();
  }

  @Override
  public BendableDoubleScore convertToEntityAttribute(String scoreString) {
    if (scoreString == null) {
      return null;
    }

    return BendableDoubleScore.parseScore(scoreString);
  }
}
