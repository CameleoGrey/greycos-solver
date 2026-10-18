package greycos.solver.quarkus.jackson.score.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.impl.score.analysis.DefaultConstraintAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultIndictmentAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultIndictmentMatchAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultMatchAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultMatchOccurrenceAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultRawScoreAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultScoreAnalysis;
import greycos.solver.core.impl.score.analysis.IndictmentMaps;
import greycos.solver.quarkus.jackson.GreyCOSJacksonModule;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

class ScoreAnalysisRawJustificationJacksonTest {
  private static final ConstraintRef REF = ConstraintRef.of("constraint");
  private static final String SELECTED = "selected";

  @Test
  void customJustificationIdentityIsSharedByRawAndGroupedMatches() throws Exception {
    var analysis = analysis(new IdentityJustification("shared"), 2);
    var mapper = mapper();
    ScoreAnalysis<SimpleScore> restored =
        mapper.readValue(mapper.writeValueAsString(analysis), ScoreAnalysis.class);
    assertThat(restored).isEqualTo(analysis);
    var justification = restored.rawAnalysis().matchOccurrences().getFirst().justification();
    assertThat(restored.rawAnalysis().matchOccurrences().get(1).justification())
        .isSameAs(justification);
    assertThat(restored.getConstraintAnalysis(REF).matches().getFirst().justification())
        .isSameAs(justification);
    assertThat(restored.indictmentMap().get(SELECTED).matches().getFirst().justification())
        .isSameAs(justification);
  }

  @Test
  void diffMayDefineAnEqualGroupedJustificationThatIsAbsentFromRawRows() throws Exception {
    var beforeJustification = new IdentityJustification("shared");
    var afterJustification = new IdentityJustification("shared");
    var difference = analysis(afterJustification, 1).diff(analysis(beforeJustification, 2));
    assertThat(difference.rawAnalysis().matchOccurrences().getFirst().justification())
        .isSameAs(beforeJustification);
    assertThat(difference.getConstraintAnalysis(REF).matches().getFirst().justification())
        .isSameAs(afterJustification);

    var mapper = mapper();
    ScoreAnalysis<SimpleScore> restored =
        mapper.readValue(mapper.writeValueAsString(difference), ScoreAnalysis.class);
    assertThat(restored).isEqualTo(difference);
    var removal = restored.rawAnalysis().matchOccurrences().getFirst();
    var groupedJustification =
        restored.getConstraintAnalysis(REF).matches().getFirst().justification();
    assertThat(removal.justification())
        .isEqualTo(groupedJustification)
        .isNotSameAs(groupedJustification);
    assertThat(restored.indictmentMap().get(SELECTED).matches().getFirst().justification())
        .isSameAs(groupedJustification);
    assertThat(removal.occurrenceSign()).isEqualTo(-1);
    assertThat(removal.matchScore()).isEqualTo(SimpleScore.of(-1));
    assertThat(restored.score()).isEqualTo(SimpleScore.ONE);
    assertThat(restored.getConstraintAnalysis(REF).rawMatchCount()).isEqualTo(-1L);
  }

  private static ScoreAnalysis<SimpleScore> analysis(
      IdentityJustification justification, int count) {
    var rows = new ArrayList<MatchOccurrenceAnalysis<SimpleScore>>();
    for (int i = 0; i < count; i++) {
      rows.add(
          new DefaultMatchOccurrenceAnalysis<>(
              REF, SimpleScore.of(-1), justification, 1, List.of(SELECTED), null));
    }
    var score = SimpleScore.of(-count);
    var constraint =
        new DefaultConstraintAnalysis<>(
            REF,
            SimpleScore.ONE,
            score,
            List.of(new DefaultMatchAnalysis<>(REF, score, justification)),
            1,
            (long) count);
    var groups = new LinkedHashMap<Object, IndictmentAnalysis<SimpleScore>>();
    groups.put(
        SELECTED,
        new DefaultIndictmentAnalysis<>(
            SELECTED,
            score,
            List.of(new DefaultIndictmentMatchAnalysis<>(REF, score, justification, count)),
            count));
    return new DefaultScoreAnalysis<>(
        score,
        Map.of(REF, constraint),
        true,
        List.of(),
        new IndictmentMaps<>(groups, null),
        new DefaultRawScoreAnalysis<>(rows, false));
  }

  private static JsonMapper mapper() {
    var module = new SimpleModule();
    module.addDeserializer(ScoreAnalysis.class, new TypedDeserializer());
    return JsonMapper.builder()
        .addModule(GreyCOSJacksonModule.createModule())
        .addModule(module)
        .build();
  }

  @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
  public static final class IdentityJustification implements ConstraintJustification {
    public String rule;

    public IdentityJustification() {}

    IdentityJustification(String rule) {
      this.rule = rule;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof IdentityJustification justification
          && Objects.equals(rule, justification.rule);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(rule);
    }
  }

  private static final class TypedDeserializer
      extends AbstractScoreAnalysisJacksonDeserializer<SimpleScore> {
    @Override
    protected SimpleScore parseScore(String scoreString) {
      return SimpleScore.parseScore(scoreString);
    }

    @Override
    protected ConstraintJustification deserializeConstraintJustification(
        ConstraintRef ref, JsonNode node, DeserializationContext context, SimpleScore score)
        throws IOException {
      return context.readTreeAsValue(node, IdentityJustification.class);
    }

    @Override
    protected Object deserializeIndictedObject(JsonNode node, DeserializationContext context)
        throws IOException {
      return context.readTreeAsValue(node, String.class);
    }
  }
}
