package greycos.solver.jackson.api.score.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import greycos.solver.core.api.solver.RecommendedAssignment;
import greycos.solver.core.impl.score.analysis.DefaultConstraintAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultIndictmentAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultIndictmentMatchAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultMatchAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultMatchOccurrenceAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultRawScoreAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultScoreAnalysis;
import greycos.solver.core.impl.score.analysis.IndictmentMaps;
import greycos.solver.core.impl.solver.DefaultRecommendedAssignment;
import greycos.solver.jackson.api.GreyCOSJacksonModule;
import greycos.solver.jackson.api.solver.AbstractRecommendedAssignmentJacksonDeserializer;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class ScoreAnalysisRawJacksonTest {
  private static final ConstraintRef REF = ConstraintRef.of("constraint");

  @Test
  void sharedGraphAndEqualDistinctObjectsRoundTrip() throws Exception {
    var deserializer = new TypedDeserializer();
    var mapper = mapper(deserializer, new ScoreAnalysisJacksonSerializer<>());
    var analysis = sharedGraph();
    var json = mapper.writeValueAsString(analysis);
    var tree = mapper.readTree(json);
    assertThat(tree.get("rawAnalysis").get("objects").size()).isEqualTo(3);
    assertThat(tree.get("indictments").get(0).has("indictedObject")).isFalse();
    assertThat(tree.get("indictments").get(0).get("objectIndex").intValue()).isEqualTo(2);
    assertThat(tree.get("constraints").get(0).get("rawMatchCount").longValue()).isEqualTo(2L);
    assertThat(tree.get("rawAnalysis").get("matches").size()).isEqualTo(2);
    ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
    assertThat(deserializer.objectCalls).isEqualTo(3);
    assertSharedGraph(restored);
    assertThat(restored.constraintMap().get(REF).rawMatchCount()).isEqualTo(2L);
  }

  @Test
  void emptyRawViewWorksWithLegacySubclass() throws Exception {
    var mapper = mapper(new LegacyDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    var analysis = rawAnalysis(List.of(), false);
    var json = mapper.writeValueAsString(analysis);
    ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
    assertThat(restored.rawAnalysis()).isNotNull();
    assertThat(restored.rawAnalysis().matchOccurrences()).isEmpty();
    assertThat(restored.rawAnalysis().originalIndictmentMap()).isNull();
    assertThat(mapper.readTree(json).get("rawAnalysis").get("objects").isArray()).isTrue();
  }

  @Test
  void defaultCustomHookDelegatesToLegacyBehavior() throws Exception {
    var row = row(new PlainExplanation("custom"), 1, List.of(), null);
    var mapper = mapper(new LegacyDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    var json = mapper.writeValueAsString(rawAnalysis(List.of(row), false));
    ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
    assertThat(restored.rawAnalysis().matchOccurrences().getFirst().justification())
        .isEqualTo(new PlainExplanation("custom"));
    assertThat(
            mapper
                .readTree(json)
                .get("rawAnalysis")
                .get("matches")
                .get(0)
                .get("justification")
                .get("type")
                .asString())
        .isEqualTo("custom");
  }

  @Test
  void customHooksRegisterLateObjectsAndRestoreReferences() throws Exception {
    var selected = new Item("selected");
    var nested = new Item("nested-only");
    var row = row(new LinkedExplanation(selected, nested), 1, List.of(selected), null);
    var deserializer = new ReferenceDeserializer();
    var mapper = mapper(deserializer, new ReferenceSerializer());
    var json = mapper.writeValueAsString(rawAnalysis(List.of(row), false));
    var tree = mapper.readTree(json);
    assertThat(tree.get("rawAnalysis").get("objects").size()).isEqualTo(2);
    ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
    var restoredRow = restored.rawAnalysis().matchOccurrences().getFirst();
    var justification = (LinkedExplanation) restoredRow.justification();
    assertThat(justification.selected()).isSameAs(restoredRow.indictedObjects().getFirst());
    assertThat(justification.nested()).isEqualTo(nested);
    assertThat(deserializer.objectCalls).isEqualTo(2);

    var groups = new LinkedHashMap<Object, IndictmentAnalysis<SimpleScore>>();
    groups.put(
        selected,
        new DefaultIndictmentAnalysis<>(
            selected,
            SimpleScore.of(-2),
            List.of(
                new DefaultIndictmentMatchAnalysis<>(
                    REF, SimpleScore.of(-2), row.justification(), 1)),
            1));
    var constraint =
        new DefaultConstraintAnalysis<>(
            REF,
            SimpleScore.ONE,
            SimpleScore.of(-2),
            List.of(new DefaultMatchAnalysis<>(REF, SimpleScore.of(-2), row.justification())),
            1,
            1L);
    var withGroups =
        new DefaultScoreAnalysis<>(
            SimpleScore.of(-2),
            Map.of(REF, constraint),
            true,
            List.of(),
            new IndictmentMaps<>(groups, null),
            new DefaultRawScoreAnalysis<>(List.of(row), false));
    ScoreAnalysis<SimpleScore> restoredGroups =
        mapper.readValue(mapper.writeValueAsString(withGroups), ScoreAnalysis.class);
    var shared =
        restoredGroups.rawAnalysis().matchOccurrences().getFirst().indictedObjects().getFirst();
    assertThat(restoredGroups.indictmentMap().firstEntry().getKey()).isSameAs(shared);
    assertThat(
            ((LinkedExplanation)
                    restoredGroups.constraintMap().get(REF).matches().getFirst().justification())
                .selected())
        .isSameAs(shared);
    assertThat(
            ((LinkedExplanation)
                    restoredGroups
                        .indictmentMap()
                        .firstEntry()
                        .getValue()
                        .matches()
                        .getFirst()
                        .justification())
                .nested())
        .isSameAs(
            ((LinkedExplanation)
                    restoredGroups.rawAnalysis().matchOccurrences().getFirst().justification())
                .nested());
  }

  @Test
  void rawDiffSurvivesWhenAllGroupedContributionsCancel() throws Exception {
    var a = new Item("a");
    var b = new Item("b");
    var c = new Item("c");
    var d = new Item("d");
    var justification = new PlainExplanation("same");
    var left =
        rawAnalysis(
            List.of(
                row(justification, 1, List.of(a, b), null),
                row(justification, 1, List.of(c, d), null)),
            false);
    var right =
        rawAnalysis(
            List.of(
                row(justification, 1, List.of(a, c), null),
                row(justification, 1, List.of(b, d), null)),
            false);
    var difference = left.diff(right);
    assertThat(difference.constraintMap()).isEmpty();
    assertThat(difference.indictmentMap()).isEmpty();
    var mapper = mapper(new TypedDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    var json = mapper.writeValueAsString(difference);
    ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
    var rows = restored.rawAnalysis().matchOccurrences();
    assertThat(rows).hasSize(4);
    assertThat(rows)
        .extracting(MatchOccurrenceAnalysis::occurrenceSign)
        .containsExactly(1, 1, -1, -1);
    assertThat(rows)
        .extracting(MatchOccurrenceAnalysis::matchScore)
        .containsOnly(SimpleScore.of(-2));
    assertThat(rows.get(0).indictedObjects()).containsExactly(a, b);
    assertThat(rows.get(2).indictedObjects()).containsExactly(a, c);
    assertThat(restored.rawAnalysis().indictmentMap().get(a).getFirst()).isSameAs(rows.getFirst());
    assertThat(restored.indictmentMap()).isEmpty();
  }

  @Test
  void duplicateOccurrenceAndSelectionMultiplicityRoundTrip() throws Exception {
    var item = new Item("a");
    var justification = new PlainExplanation("same");
    var left =
        rawAnalysis(
            List.of(
                row(justification, 1, List.of(item, item), null),
                row(justification, 1, List.of(item, item), null)),
            false);
    var right = rawAnalysis(List.of(row(justification, 1, List.of(item, item), null)), false);
    var mapper = mapper(new TypedDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    ScoreAnalysis<SimpleScore> restored =
        mapper.readValue(mapper.writeValueAsString(left.diff(right)), ScoreAnalysis.class);
    var row = restored.rawAnalysis().matchOccurrences().getFirst();
    assertThat(restored.rawAnalysis().matchOccurrences()).hasSize(1);
    assertThat(row.indictedObjects()).hasSize(2);
    assertThat(row.indictedObjects().get(0)).isSameAs(row.indictedObjects().get(1));
  }

  @Test
  void removedRawOccurrencesPreserveOriginalScoresAndSignedCounts() throws Exception {
    var difference = rawAnalysis(List.of(), true).diff(sharedGraph());
    var mapper = mapper(new TypedDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    ScoreAnalysis<SimpleScore> restored =
        mapper.readValue(mapper.writeValueAsString(difference), ScoreAnalysis.class);
    assertThat(restored.constraintMap().get(REF).rawMatchCount()).isEqualTo(-2L);
    assertThat(restored.rawAnalysis().matchOccurrences())
        .hasSize(2)
        .allSatisfy(
            row -> {
              assertThat(row.occurrenceSign()).isEqualTo(-1);
              assertThat(row.matchScore()).isEqualTo(SimpleScore.of(-2));
              assertThat(row.score()).isEqualTo(SimpleScore.of(2));
              assertThat(
                      ((DefaultConstraintJustification) row.justification())
                          .<SimpleScore>getImpact())
                  .isEqualTo(SimpleScore.of(7));
            });
  }

  @Test
  void recommendationsUseTheSameReferenceGraph() throws Exception {
    var mapper = mapper(new TypedDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    var recommendation = new DefaultRecommendedAssignment<>(0L, "choose", sharedGraph());
    var json = mapper.writeValueAsString(recommendation);
    RecommendedAssignment<String, SimpleScore> restored =
        mapper.readValue(json, RecommendedAssignment.class);
    assertThat(restored.proposition()).isEqualTo("choose");
    assertSharedGraph(restored.scoreAnalysisDiff());
  }

  @Test
  void malformedIndexesSignsAndAvailabilityAreRejected() {
    var mapper = mapper(new TypedDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    for (var invalidIndex : List.of("-1", "2", "0.5", "2147483648", "null")) {
      var invalid =
          rawJson()
              .replace(
                  "\"indictedObjectIndexes\":[0,0,1]",
                  "\"indictedObjectIndexes\":[" + invalidIndex + "]");
      assertThatThrownBy(() -> mapper.readValue(invalid, ScoreAnalysis.class))
          .hasMessageContaining("index");
    }
    for (var invalidSign : List.of("0", "2", "-2", "1.5", "\"1\"")) {
      var invalid = rawJson().replace("\"occurrenceSign\":1", "\"occurrenceSign\":" + invalidSign);
      assertThatThrownBy(() -> mapper.readValue(invalid, ScoreAnalysis.class))
          .hasMessageContaining("occurrenceSign");
    }
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    rawJson().replace("\"originalsFetched\":true", "\"originalsFetched\":false"),
                    ScoreAnalysis.class))
        .hasMessageContaining("must be absent");
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    rawJson().replace(",\"originalIndictedObjectIndexes\":[0,1]", ""),
                    ScoreAnalysis.class))
        .hasMessageContaining("originalIndictedObjectIndexes");
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    rawJson()
                        .replace(
                            "\"originalIndictedObjectIndexes\":[0,1]",
                            "\"originalIndictedObjectIndexes\":[0,0]"),
                    ScoreAnalysis.class))
        .hasMessageContaining("distinct object references");
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    rawJson().replace("\"type\":\"default\"", "\"type\":\"unknown\""),
                    ScoreAnalysis.class))
        .hasMessageContaining("default or custom");
  }

  @Test
  void groupedObjectIndexesMustUseTheRawTable() {
    var mapper = mapper(new TypedDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    var entry = "{\"objectIndex\":2,\"score\":\"0\",\"matchCount\":0,\"matches\":[]}";
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    rawJson().replace("\"indictments\":[]", "\"indictments\":[" + entry + "]"),
                    ScoreAnalysis.class))
        .hasMessageContaining("outside the object table");
    var mixedEntry =
        entry.replace(
            "\"objectIndex\":2", "\"objectIndex\":0,\"indictedObject\":{\"id\":\"same\"}");
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    rawJson().replace("\"indictments\":[]", "\"indictments\":[" + mixedEntry + "]"),
                    ScoreAnalysis.class))
        .hasMessageContaining("must use objectIndex");
    var legacy =
        "{\"score\":\"0\",\"initialized\":true,\"constraints\":[],\"indictments\":[" + entry + "]}";
    assertThatThrownBy(() -> mapper.readValue(legacy, ScoreAnalysis.class))
        .hasMessageContaining("requires rawAnalysis");
  }

  @Test
  void typedHookMustNotCollapseEqualDistinctSlots() {
    var common = new Item("same");
    var mapper =
        mapper(
            new TypedDeserializer() {
              @Override
              protected Object deserializeIndictedObject(
                  JsonNode node, DeserializationContext context) {
                return common;
              }
            },
            new ScoreAnalysisJacksonSerializer<>());
    assertThatThrownBy(() -> mapper.readValue(rawJson(), ScoreAnalysis.class))
        .hasMessageContaining("collapsed distinct slots");
  }

  @Test
  void missingRawCountsAreDerivedIncludingSignedAndZeroCounts() throws Exception {
    var mapper = mapper(new TypedDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    for (var signs : List.of(List.of(1), List.of(-1), List.of(1, -1), List.<Integer>of())) {
      var rows =
          signs.stream()
              .map(sign -> row(new PlainExplanation("count"), sign, List.of(), null))
              .toList();
      long expected = signs.stream().mapToLong(Integer::longValue).sum();
      var json = mapper.writeValueAsString(countAnalysis(rows, null));
      assertThat(mapper.readTree(json).get("constraints").get(0).get("rawMatchCount").longValue())
          .isEqualTo(expected);
      var withoutCount = json.replace(",\"rawMatchCount\":" + expected, "");
      assertThat(withoutCount).doesNotContain("rawMatchCount");
      ScoreAnalysis<SimpleScore> restored = mapper.readValue(withoutCount, ScoreAnalysis.class);
      assertThat(restored.constraintMap().get(REF).rawMatchCount()).isEqualTo(expected);
    }
  }

  @Test
  void explicitRawCountsMustMatchCanonicalOccurrences() throws Exception {
    var mapper = mapper(new TypedDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    for (var signs : List.of(List.of(1), List.of(-1), List.of(1, -1), List.<Integer>of())) {
      var rows =
          signs.stream()
              .map(sign -> row(new PlainExplanation("count"), sign, List.of(), null))
              .toList();
      long expected = signs.stream().mapToLong(Integer::longValue).sum();
      long wrong = expected == 0 ? 1 : 0;
      assertThatThrownBy(() -> mapper.writeValueAsString(countAnalysis(rows, wrong)))
          .hasMessageContaining("rawMatchCount")
          .hasMessageContaining("signed raw occurrence count");
      var json = mapper.writeValueAsString(countAnalysis(rows, expected));
      var wrongCount = json.replace("\"rawMatchCount\":" + expected, "\"rawMatchCount\":" + wrong);
      assertThatThrownBy(() -> mapper.readValue(wrongCount, ScoreAnalysis.class))
          .hasMessageContaining("rawMatchCount")
          .hasMessageContaining("signed raw occurrence count");
    }
  }

  @Test
  void rawJsonRequiresConsistentFetchedViewAvailability() throws Exception {
    var mapper = mapper(new TypedDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    for (var json :
        List.of(
            rawJson().replace("\"indictments\":[],", ""),
            rawJson().replace("\"indictments\":[]", "\"indictments\":null"))) {
      assertThatThrownBy(() -> mapper.readValue(json, ScoreAnalysis.class))
          .hasMessageContaining("fetched terminal indictments");
    }
    for (var json :
        List.of(
            rawJson().replace("\"originalIndictments\":[],", ""),
            rawJson().replace("\"originalIndictments\":[]", "\"originalIndictments\":null"))) {
      assertThatThrownBy(() -> mapper.readValue(json, ScoreAnalysis.class))
          .hasMessageContaining("originalIndictments availability");
    }
    var unexpectedOriginals =
        rawJson()
            .replace("\"originalsFetched\":true", "\"originalsFetched\":false")
            .replace(",\"originalIndictedObjectIndexes\":[0,1]", "");
    assertThatThrownBy(() -> mapper.readValue(unexpectedOriginals, ScoreAnalysis.class))
        .hasMessageContaining("originalIndictments availability");
    ScoreAnalysis<SimpleScore> fetchedEmpty = mapper.readValue(rawJson(), ScoreAnalysis.class);
    assertThat(fetchedEmpty.indictmentMap()).isEmpty();
    assertThat(fetchedEmpty.originalIndictmentMap()).isEmpty();
  }

  @Test
  void rawSerializerRequiresConsistentFetchedViewAvailability() {
    var mapper = mapper(new TypedDeserializer(), new ScoreAnalysisJacksonSerializer<>());
    var terminalOnly = new DefaultRawScoreAnalysis<SimpleScore>(List.of(), false);
    var withOriginals = new DefaultRawScoreAnalysis<SimpleScore>(List.of(), true);
    assertThatThrownBy(
            () ->
                mapper.writeValueAsString(
                    new DefaultScoreAnalysis<>(
                        SimpleScore.ZERO,
                        Map.of(),
                        true,
                        List.of(),
                        IndictmentMaps.unavailable(),
                        terminalOnly)))
        .hasMessageContaining("fetched terminal indictments");
    assertThatThrownBy(
            () ->
                mapper.writeValueAsString(
                    new DefaultScoreAnalysis<>(
                        SimpleScore.ZERO,
                        Map.of(),
                        true,
                        List.of(),
                        new IndictmentMaps<>(new LinkedHashMap<>(), null),
                        withOriginals)))
        .hasMessageContaining("originalIndictments availability");
    assertThatThrownBy(
            () ->
                mapper.writeValueAsString(
                    new DefaultScoreAnalysis<>(
                        SimpleScore.ZERO,
                        Map.of(),
                        true,
                        List.of(),
                        new IndictmentMaps<>(new LinkedHashMap<>(), new LinkedHashMap<>()),
                        terminalOnly)))
        .hasMessageContaining("originalIndictments availability");
  }

  private static ScoreAnalysis<SimpleScore> countAnalysis(
      List<MatchOccurrenceAnalysis<SimpleScore>> rows, Long rawCount) {
    var constraint =
        new DefaultConstraintAnalysis<SimpleScore>(
            REF, SimpleScore.ONE, SimpleScore.ZERO, List.of(), 0, rawCount);
    return new DefaultScoreAnalysis<>(
        SimpleScore.ZERO,
        Map.of(REF, constraint),
        true,
        List.of(),
        new IndictmentMaps<>(new LinkedHashMap<>(), null),
        new DefaultRawScoreAnalysis<>(rows, false));
  }

  private static void assertSharedGraph(ScoreAnalysis<SimpleScore> restored) {
    var raw = restored.rawAnalysis();
    var rows = raw.matchOccurrences();
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).isNotSameAs(rows.get(1));
    var first = rows.getFirst();
    var a = first.indictedObjects().get(0);
    var b = first.indictedObjects().get(2);
    assertThat(a).isEqualTo(b).isNotSameAs(b);
    assertThat(first.indictedObjects().get(1)).isSameAs(a);
    assertThat(first.originalIndictedObjects().get(0)).isSameAs(a);
    assertThat(first.originalIndictedObjects().get(1)).isSameAs(b);
    assertThat(rows.get(1).indictedObjects().get(0)).isSameAs(a);
    assertThat(raw.constraintMap().get(REF).getFirst()).isSameAs(first);
    assertThat(raw.indictmentMap().get(a).getFirst()).isSameAs(first);
    assertThat(raw.originalIndictmentMap().get(a).getFirst()).isSameAs(first);
    var representative = restored.indictmentMap().firstEntry().getKey();
    assertThat(representative).isEqualTo(a).isNotSameAs(a).isNotSameAs(b);
    assertThat(restored.originalIndictmentMap().firstEntry().getKey()).isSameAs(representative);
    var defaultJustification = (DefaultConstraintJustification) first.justification();
    assertThat(defaultJustification.<SimpleScore>getImpact()).isEqualTo(SimpleScore.of(7));
    assertThat(defaultJustification.getFacts().get(0)).isSameAs(a);
    assertThat(defaultJustification.getFacts().get(1)).isSameAs(b);
    assertThat(defaultJustification.getFacts().get(2)).isNull();
    var grouped =
        (DefaultConstraintJustification)
            restored.constraintMap().get(REF).matches().getFirst().justification();
    assertThat(grouped.getFacts().get(0)).isSameAs(a);
    assertThat(grouped.<SimpleScore>getImpact()).isEqualTo(SimpleScore.of(7));
  }

  private static ScoreAnalysis<SimpleScore> sharedGraph() {
    var a = new Item("same");
    var b = new Item("same");
    var representative = new Item("same");
    var justification =
        DefaultConstraintJustification.of(SimpleScore.of(7), Arrays.asList(a, b, null, a));
    var rows =
        List.of(
            row(justification, 1, List.of(a, a, b), List.of(a, b)),
            row(justification, 1, List.of(a, a, b), List.of(a, b)));
    var group =
        new DefaultIndictmentAnalysis<>(
            representative,
            SimpleScore.of(-4),
            List.of(
                new DefaultIndictmentMatchAnalysis<>(REF, SimpleScore.of(-4), justification, 2)),
            2);
    var groups = new LinkedHashMap<Object, IndictmentAnalysis<SimpleScore>>();
    groups.put(representative, group);
    var constraint =
        new DefaultConstraintAnalysis<>(
            REF,
            SimpleScore.ONE,
            SimpleScore.of(-4),
            List.of(new DefaultMatchAnalysis<>(REF, SimpleScore.of(-4), justification)),
            1,
            2L);
    return new DefaultScoreAnalysis<>(
        SimpleScore.of(-4),
        Map.of(REF, constraint),
        true,
        List.of(),
        new IndictmentMaps<>(groups, groups),
        new DefaultRawScoreAnalysis<>(rows, true));
  }

  private static MatchOccurrenceAnalysis<SimpleScore> row(
      ConstraintJustification justification, int sign, List<?> terminal, List<?> original) {
    return new DefaultMatchOccurrenceAnalysis<>(
        REF, SimpleScore.of(-2), justification, sign, terminal, original);
  }

  private static ScoreAnalysis<SimpleScore> rawAnalysis(
      List<MatchOccurrenceAnalysis<SimpleScore>> rows, boolean originalsFetched) {
    return new DefaultScoreAnalysis<>(
        SimpleScore.ZERO,
        Map.of(),
        true,
        List.of(),
        new IndictmentMaps<>(
            new LinkedHashMap<>(), originalsFetched ? new LinkedHashMap<>() : null),
        new DefaultRawScoreAnalysis<>(rows, originalsFetched));
  }

  private static String rawJson() {
    return """
        {"score":"-1","initialized":true,"constraints":[],"indictments":[],"originalIndictments":[],
         "rawAnalysis":{"originalsFetched":true,"objects":[{"id":"same"},{"id":"same"}],
         "matches":[{"id":"constraint","matchScore":"-1","occurrenceSign":1,
         "justification":{"type":"default","impact":"-1","factIndexes":[0,null]},
         "indictedObjectIndexes":[0,0,1],"originalIndictedObjectIndexes":[0,1]}]}}
        """;
  }

  private static JsonMapper mapper(
      AbstractScoreAnalysisJacksonDeserializer<SimpleScore> deserializer,
      ScoreAnalysisJacksonSerializer<SimpleScore> serializer) {
    var module = new SimpleModule();
    module.addDeserializer(ScoreAnalysis.class, deserializer);
    module.addSerializer((Class) ScoreAnalysis.class, serializer);
    module.addDeserializer(RecommendedAssignment.class, new RecommendationDeserializer());
    return JsonMapper.builder()
        .addModule(GreyCOSJacksonModule.createModule())
        .addModule(module)
        .build();
  }

  public record Item(String id) {}

  public record PlainExplanation(String message) implements ConstraintJustification {}

  public record LinkedExplanation(Item selected, Item nested) implements ConstraintJustification {}

  private static class LegacyDeserializer
      extends AbstractScoreAnalysisJacksonDeserializer<SimpleScore> {
    @Override
    protected SimpleScore parseScore(String scoreString) {
      return SimpleScore.parseScore(scoreString);
    }

    @Override
    protected ConstraintJustification deserializeConstraintJustification(
        ConstraintRef ref, JsonNode node, DeserializationContext context, SimpleScore score)
        throws JacksonException {
      return context.readTreeAsValue(node, PlainExplanation.class);
    }
  }

  private static class TypedDeserializer extends LegacyDeserializer {
    int objectCalls;

    @Override
    protected Object deserializeIndictedObject(JsonNode node, DeserializationContext context)
        throws JacksonException {
      objectCalls++;
      return context.readTreeAsValue(node, Item.class);
    }
  }

  private static final class ReferenceSerializer
      extends ScoreAnalysisJacksonSerializer<SimpleScore> {
    @Override
    protected Object serializeConstraintJustification(
        ConstraintRef ref,
        ConstraintJustification justification,
        SerializationContext context,
        SimpleScore score,
        ToIntFunction<Object> registerObject) {
      var linked = (LinkedExplanation) justification;
      return Map.of(
          "selectedIndex",
          registerObject.applyAsInt(linked.selected()),
          "nestedIndex",
          registerObject.applyAsInt(linked.nested()));
    }
  }

  private static final class ReferenceDeserializer extends TypedDeserializer {
    @Override
    protected ConstraintJustification deserializeConstraintJustification(
        ConstraintRef ref,
        JsonNode node,
        DeserializationContext context,
        SimpleScore score,
        IntFunction<Object> resolveObject) {
      return new LinkedExplanation(
          (Item) resolveObject.apply(node.get("selectedIndex").intValue()),
          (Item) resolveObject.apply(node.get("nestedIndex").intValue()));
    }
  }

  private static final class RecommendationDeserializer
      extends AbstractRecommendedAssignmentJacksonDeserializer<String, SimpleScore> {
    @Override
    protected Class<String> getPropositionClass() {
      return String.class;
    }
  }
}
