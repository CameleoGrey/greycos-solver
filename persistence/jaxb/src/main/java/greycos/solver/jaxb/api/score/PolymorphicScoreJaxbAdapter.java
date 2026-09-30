package greycos.solver.jaxb.api.score;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;

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

public class PolymorphicScoreJaxbAdapter
    extends XmlAdapter<PolymorphicScoreJaxbAdapter.JaxbAdaptedScore, Score> {

  @Override
  public Score unmarshal(JaxbAdaptedScore jaxbAdaptedScore) {
    if (jaxbAdaptedScore == null) {
      return null;
    }
    String scoreClassName = jaxbAdaptedScore.scoreClassName;
    String scoreString = jaxbAdaptedScore.scoreString;
    // TODO Can this delegate to ScoreUtils.parseScore()?
    if (scoreClassName.equals(SimpleScore.class.getName())) {
      return SimpleScore.parseScore(scoreString);
    } else if (scoreClassName.equals(SimpleBigDecimalScore.class.getName())) {
      return SimpleBigDecimalScore.parseScore(scoreString);
    } else if (scoreClassName.equals(HardSoftScore.class.getName())) {
      return HardSoftScore.parseScore(scoreString);
    } else if (scoreClassName.equals(HardSoftBigDecimalScore.class.getName())) {
      return HardSoftBigDecimalScore.parseScore(scoreString);
    } else if (scoreClassName.equals(HardMediumSoftScore.class.getName())) {
      return HardMediumSoftScore.parseScore(scoreString);
    } else if (scoreClassName.equals(HardMediumSoftBigDecimalScore.class.getName())) {
      return HardMediumSoftBigDecimalScore.parseScore(scoreString);
    } else if (scoreClassName.equals(BendableScore.class.getName())) {
      return BendableScore.parseScore(scoreString);
    } else if (scoreClassName.equals(BendableBigDecimalScore.class.getName())) {
      return BendableBigDecimalScore.parseScore(scoreString);
    } else if (scoreClassName.equals(SimpleFloatScore.class.getName())) {
      return SimpleFloatScore.parseScore(scoreString);
    } else if (scoreClassName.equals(SimpleDoubleScore.class.getName())) {
      return SimpleDoubleScore.parseScore(scoreString);
    } else if (scoreClassName.equals(HardSoftFloatScore.class.getName())) {
      return HardSoftFloatScore.parseScore(scoreString);
    } else if (scoreClassName.equals(HardSoftDoubleScore.class.getName())) {
      return HardSoftDoubleScore.parseScore(scoreString);
    } else if (scoreClassName.equals(HardMediumSoftFloatScore.class.getName())) {
      return HardMediumSoftFloatScore.parseScore(scoreString);
    } else if (scoreClassName.equals(HardMediumSoftDoubleScore.class.getName())) {
      return HardMediumSoftDoubleScore.parseScore(scoreString);
    } else if (scoreClassName.equals(BendableFloatScore.class.getName())) {
      return BendableFloatScore.parseScore(scoreString);
    } else if (scoreClassName.equals(BendableDoubleScore.class.getName())) {
      return BendableDoubleScore.parseScore(scoreString);
    } else {
      throw new IllegalArgumentException(
          "Unrecognized scoreClassName ("
              + scoreClassName
              + ") for scoreString ("
              + scoreString
              + ").");
    }
  }

  @Override
  public JaxbAdaptedScore marshal(Score score) {
    if (score == null) {
      return null;
    }
    return new JaxbAdaptedScore(score);
  }

  static class JaxbAdaptedScore {

    @XmlAttribute(name = "class")
    private String scoreClassName;

    @XmlValue private String scoreString;

    private JaxbAdaptedScore() {
      // Required by JAXB
    }

    public JaxbAdaptedScore(Score score) {
      this.scoreClassName = score.getClass().getName();
      this.scoreString = score.toString();
    }

    String getScoreClassName() {
      return scoreClassName;
    }

    String getScoreString() {
      return scoreString;
    }
  }
}
