package greycos.solver.jackson.api.score.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import greycos.solver.core.impl.score.analysis.DefaultConstraintAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultIndictmentAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultIndictmentMatchAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultScoreAnalysis;
import greycos.solver.core.impl.score.analysis.IndictmentMaps;
import greycos.solver.jackson.api.GreyCOSJacksonModule;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class ScoreAnalysisIndictmentJacksonTest {

  @Test
  void countOnlyRemovalRetainsAvailableMinusOne() throws Exception {
    var ref = ConstraintRef.of("count");
    var empty =
        new DefaultScoreAnalysis<>(
            SimpleScore.ZERO,
            Map.of(
                ref,
                new DefaultConstraintAnalysis<>(ref, SimpleScore.ONE, SimpleScore.ZERO, null, 0)));
    var populated =
        new DefaultScoreAnalysis<>(
            SimpleScore.ONE,
            Map.of(
                ref,
                new DefaultConstraintAnalysis<>(ref, SimpleScore.ONE, SimpleScore.ONE, null, 1)));
    var difference = empty.diff(populated);
    var mapper = mapper(new LegacyDeserializer());
    var json = mapper.writeValueAsString(difference);
    assertThat(mapper.readTree(json).get("constraints").get(0).get("matchCount").intValue())
        .isEqualTo(-1);
    ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
    assertThat(restored.getConstraintAnalysis(ref).isMatchCountAvailable()).isTrue();
    assertThat(restored.getConstraintAnalysis(ref).matchCount()).isEqualTo(-1);
    assertThat(restored).isEqualTo(difference);
    assertThat(empty.diff(restored).getConstraintAnalysis(ref).matchCount()).isOne();
  }

  @Test
  void absentMatchCountRemainsUnavailable() throws Exception {
    var ref = ConstraintRef.of("shallow");
    var analysis =
        new DefaultScoreAnalysis<>(
            SimpleScore.ZERO,
            Map.of(
                ref,
                new DefaultConstraintAnalysis<>(ref, SimpleScore.ONE, SimpleScore.ZERO, null)));
    var mapper = mapper(new LegacyDeserializer());
    var json = mapper.writeValueAsString(analysis);
    assertThat(mapper.readTree(json).get("constraints").get(0).has("matchCount")).isFalse();
    ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
    assertThat(restored.getConstraintAnalysis(ref).isMatchCountAvailable()).isFalse();
    assertThat(restored.getConstraintAnalysis(ref).matchCount()).isEqualTo(-1);
    assertThat(restored).isEqualTo(analysis);
  }

  @Test
  void legacyOutputAndDeserializerRemainCompatible() throws Exception {
    var mapper = mapper(new LegacyDeserializer());
    var analysis = new DefaultScoreAnalysis<>(SimpleScore.ZERO, Map.of());
    var json = mapper.writeValueAsString(analysis);
    assertThat(json).isEqualTo("{\"score\":\"0\",\"initialized\":true,\"constraints\":[]}");
    var restored = mapper.readValue(json, ScoreAnalysis.class);
    assertThat(restored.indictmentMap()).isNull();
    assertThat(restored.originalIndictmentMap()).isNull();
  }

  @Test
  void emptyFetchedViewsDoNotRequireTypedHook() throws Exception {
    var mapper = mapper(new LegacyDeserializer());
    var analysis = analysis(new LinkedHashMap<>(), new LinkedHashMap<>());
    var json = mapper.writeValueAsString(analysis);
    assertThat(mapper.readTree(json).get("indictments").isArray()).isTrue();
    assertThat(mapper.readTree(json).get("originalIndictments").isArray()).isTrue();
    var restored = mapper.readValue(json, ScoreAnalysis.class);
    assertThat(restored.indictmentMap()).isEmpty();
    assertThat(restored.originalIndictmentMap()).isEmpty();
  }

  @Test
  void typedRoundTripPreservesBothViewsLongCountsAndCustomJustifications() throws Exception {
    var first = new Item("first");
    var second = new Item("second");
    // Identical textual keys must not collapse two different cotwin objects.
    assertThat(first.toString()).isEqualTo(second.toString());
    var terminal = new LinkedHashMap<Object, IndictmentAnalysis<SimpleScore>>();
    terminal.put(first, group(first, -7, -4_000_000_000L, new Explanation("custom")));
    terminal.put(
        second,
        group(
            second,
            3,
            4_000_000_000L,
            DefaultConstraintJustification.of(SimpleScore.of(3), List.of("fact"))));
    var original = new LinkedHashMap<Object, IndictmentAnalysis<SimpleScore>>();
    original.put(second, terminal.get(second));
    var analysis = analysis(terminal, original);
    var mapper = mapper(new TypedDeserializer());

    var json = mapper.writeValueAsString(analysis);
    var tree = mapper.readTree(json);
    assertThat(tree.get("indictments").isArray()).isTrue();
    assertThat(tree.get("indictments").size()).isEqualTo(2);
    assertThat(tree.get("indictments").get(0).get("indictedObject").get("id").asString())
        .isEqualTo("first");
    assertThat(tree.get("indictments").get(0).get("matchCount").longValue())
        .isEqualTo(-4_000_000_000L);
    assertThat(tree.get("indictments").get(0).get("matches").get(0).get("id").asString())
        .isEqualTo("custom");
    ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
    assertThat(restored).isEqualTo(analysis);
    assertThat(restored.indictmentMap().keySet()).containsExactly(first, second);
    assertThat(restored.indictmentMap().get(first).indictedObject()).isInstanceOf(Item.class);
    assertThat(restored.indictmentMap().get(first).matches().getFirst().justification())
        .isEqualTo(new Explanation("custom"));
    assertThat(restored.originalIndictmentMap().keySet()).containsExactly(second);
  }

  @Test
  void diffPreservesZeroGroupWithOppositeMatchContributions() throws Exception {
    var item = new Item("same");
    var left = new LinkedHashMap<Object, IndictmentAnalysis<SimpleScore>>();
    left.put(item, group(item, -1, 1, new Explanation("left")));
    var right = new LinkedHashMap<Object, IndictmentAnalysis<SimpleScore>>();
    right.put(item, group(item, -1, 1, new Explanation("right")));
    var difference = analysis(left, null).diff(analysis(right, null));
    var mapper = mapper(new TypedDeserializer());
    var json = mapper.writeValueAsString(difference);
    ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);

    assertThat(restored).isEqualTo(difference);
    var group = restored.indictmentMap().get(item);
    assertThat(group.score()).isEqualTo(SimpleScore.ZERO);
    assertThat(group.matchCount()).isZero();
    assertThat(group.matches()).extracting(match -> match.matchCount()).containsExactly(1L, -1L);
    assertThat(restored.originalIndictmentMap()).isNull();
    assertThat(mapper.readTree(json).has("originalIndictments")).isFalse();
  }

  @Test
  void nonemptyViewsRequireTypedHook() throws Exception {
    var item = new Item("typed");
    var groups = new LinkedHashMap<Object, IndictmentAnalysis<SimpleScore>>();
    groups.put(item, group(item, -1, 1, new Explanation("match")));
    var mapper = mapper(new LegacyDeserializer());
    for (var analysis : List.of(analysis(groups, null), analysis(null, groups))) {
      var json = mapper.writeValueAsString(analysis);
      assertThatThrownBy(() -> mapper.readValue(json, ScoreAnalysis.class))
          .hasMessageContaining("deserializeIndictedObject")
          .hasMessageContaining("cotwin type");
    }
  }

  @Test
  void invalidIndictmentCountsAreRejected() {
    var mapper = mapper(new TypedDeserializer());
    for (var invalidCount : List.of("1.5", "\"1\"", "9223372036854775808")) {
      for (var json : List.of(json(invalidCount, "1"), json("1", invalidCount))) {
        assertThatThrownBy(() -> mapper.readValue(json, ScoreAnalysis.class))
            .hasMessageContaining("64-bit integer");
      }
    }
    assertThatThrownBy(() -> mapper.readValue(json("2", "1"), ScoreAnalysis.class))
        .hasMessageContaining("differs from the sum");
  }

  @Test
  void invalidArraysAndDuplicateObjectsAreRejected() {
    var mapper = mapper(new TypedDeserializer());
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    "{\"score\":\"0\",\"initialized\":true,\"constraints\":[],\"indictments\":{}}",
                    ScoreAnalysis.class))
        .hasMessageContaining("must be an array");
    var entry =
        "{\"indictedObject\":{\"id\":\"duplicate\"},\"score\":\"0\",\"matchCount\":0,\"matches\":[]}";
    var duplicate =
        "{\"score\":\"0\",\"initialized\":true,\"constraints\":[],\"indictments\":["
            + entry
            + ","
            + entry
            + "]}";
    assertThatThrownBy(() -> mapper.readValue(duplicate, ScoreAnalysis.class))
        .hasMessageContaining("duplicate indicted objects");
    var invalidMatches =
        "{\"score\":\"0\",\"initialized\":true,\"constraints\":[],\"indictments\":["
            + entry.replace("\"matches\":[]", "\"matches\":{}")
            + "]}";
    assertThatThrownBy(() -> mapper.readValue(invalidMatches, ScoreAnalysis.class))
        .hasMessageContaining("must be an array");
  }

  @Test
  void nullTypedObjectIsRejected() {
    var mapper =
        mapper(
            new TypedDeserializer() {
              @Override
              protected Object deserializeIndictedObject(
                  JsonNode node, DeserializationContext context) {
                return null;
              }
            });
    assertThatThrownBy(() -> mapper.readValue(json("1", "1"), ScoreAnalysis.class))
        .hasMessageContaining("indicted object deserializer returned null");
  }

  private static String json(String groupCount, String matchCount) {
    return """
    {"score":"-1","initialized":true,"constraints":[],"indictments":[{
      "indictedObject":{"id":"object"},"score":"-1","matchCount":%s,"matches":[{
        "id":"custom","score":"-1","justification":{"reason":"match"},"matchCount":%s
      }]}]}
    """
        .formatted(groupCount, matchCount);
  }

  private static DefaultIndictmentAnalysis<SimpleScore> group(
      Item item, long score, long count, ConstraintJustification justification) {
    var match =
        new DefaultIndictmentMatchAnalysis<>(
            ConstraintRef.of(justification instanceof Explanation ? "custom" : "default"),
            SimpleScore.of(score),
            justification,
            count);
    return new DefaultIndictmentAnalysis<>(item, SimpleScore.of(score), List.of(match), count);
  }

  private static ScoreAnalysis<SimpleScore> analysis(
      SequencedMap<Object, IndictmentAnalysis<SimpleScore>> terminal,
      SequencedMap<Object, IndictmentAnalysis<SimpleScore>> original) {
    return new DefaultScoreAnalysis<>(
        SimpleScore.ZERO, Map.of(), true, List.of(), new IndictmentMaps<>(terminal, original));
  }

  private static JsonMapper mapper(
      AbstractScoreAnalysisJacksonDeserializer<SimpleScore> deserializer) {
    var customModule = new SimpleModule();
    customModule.addDeserializer(ScoreAnalysis.class, deserializer);
    return JsonMapper.builder()
        .addModule(GreyCOSJacksonModule.createModule())
        .addModule(customModule)
        .build();
  }

  public record Item(String id) {
    @Override
    public String toString() {
      return "same text";
    }
  }

  public record Explanation(String reason) implements ConstraintJustification {}

  private static class LegacyDeserializer
      extends AbstractScoreAnalysisJacksonDeserializer<SimpleScore> {
    @Override
    protected SimpleScore parseScore(String scoreString) {
      return SimpleScore.parseScore(scoreString);
    }

    @Override
    protected ConstraintJustification deserializeConstraintJustification(
        ConstraintRef constraintRef,
        JsonNode node,
        DeserializationContext context,
        SimpleScore score)
        throws JacksonException {
      if (constraintRef.id().equals("custom")) {
        return context.readTreeAsValue(node, Explanation.class);
      }
      var facts = new ArrayList<Object>();
      node.forEach(fact -> facts.add(fact.asString()));
      return DefaultConstraintJustification.of(score, facts);
    }
  }

  private static class TypedDeserializer extends LegacyDeserializer {
    @Override
    protected Object deserializeIndictedObject(JsonNode node, DeserializationContext context)
        throws JacksonException {
      return context.readTreeAsValue(node, Item.class);
    }
  }
}
