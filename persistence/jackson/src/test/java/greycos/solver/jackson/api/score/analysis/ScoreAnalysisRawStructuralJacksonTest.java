package greycos.solver.jackson.api.score.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.EntityVariablePair;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.analysis.VariableLoop;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import greycos.solver.core.impl.score.analysis.DefaultConstraintAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultIndictmentAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultIndictmentMatchAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultMatchAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultMatchOccurrenceAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultRawScoreAnalysis;
import greycos.solver.core.impl.score.analysis.DefaultScoreAnalysis;
import greycos.solver.core.impl.score.analysis.IndictmentMaps;
import greycos.solver.jackson.api.GreyCOSJacksonModule;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class ScoreAnalysisRawStructuralJacksonTest {
  private static final ConstraintRef REF = ConstraintRef.of("constraint");

  @Test
  void sharedLoopEntitiesUseTheRawHookAndKeepIdentity() throws Exception {
    for (var entity : List.of(new GeneratedEntity("shared"), new PropertyEntity("shared"))) {
      var deserializer = new TypedDeserializer(entity.getClass(), null);
      var mapper = mapper(deserializer);
      var analysis =
          analysis(
              entity,
              loop(
                  new EntityVariablePair(entity, "first"),
                  new EntityVariablePair(entity, "second")));
      var json = mapper.writeValueAsString(analysis);
      var tree = mapper.readTree(json);
      assertThat(tree.get("rawAnalysis").get("objects").size()).isOne();
      assertThat(tree.get("rawAnalysis").has("structuralObjectIndexes")).isFalse();
      assertThat(
              tree.get("structuralFlawAnalysis")
                  .get("variableLoops")
                  .get(0)
                  .get("involvedVariableSet")
                  .get(0)
                  .has("entity"))
          .isFalse();

      ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
      var row = restored.rawAnalysis().matchOccurrences().getFirst();
      var shared = row.indictedObjects().getFirst();
      assertThat(row.originalIndictedObjects().getFirst()).isSameAs(shared);
      assertThat(restored.indictmentMap().firstEntry().getKey()).isSameAs(shared);
      assertThat(restored.originalIndictmentMap().firstEntry().getKey()).isSameAs(shared);
      assertThat(((DefaultConstraintJustification) row.justification()).getFacts().getFirst())
          .isSameAs(shared);
      var groupedJustification =
          (DefaultConstraintJustification)
              restored.constraintMap().get(REF).matches().getFirst().justification();
      assertThat(groupedJustification.getFacts().getFirst()).isSameAs(shared);
      assertThat(
              restored.structuralFlawAnalysis().getVariableLoops().getFirst().involvedVariableSet())
          .hasSize(2)
          .allSatisfy(pair -> assertThat(pair.entity()).isSameAs(shared));
      assertThat(deserializer.objectCalls).isOne();
      assertThat(deserializer.structuralCalls).isZero();
    }
  }

  @Test
  void rawAnalysesShareTheActiveJacksonIdentityContext() throws Exception {
    for (boolean reuseEntity : List.of(false, true)) {
      var first = new GeneratedEntity("first");
      var second = reuseEntity ? first : new GeneratedEntity("second");
      var deserializer = new TypedDeserializer(GeneratedEntity.class, null);
      var mapper = mapper(deserializer);
      var analyses =
          List.of(
              analysis(first, loop(new EntityVariablePair(first, "first"))),
              analysis(second, loop(new EntityVariablePair(second, "second"))));
      var json = mapper.writeValueAsString(analyses);
      ScoreAnalysis<?>[] restored = mapper.readValue(json, ScoreAnalysis[].class);
      var firstRestored =
          restored[0].rawAnalysis().matchOccurrences().getFirst().indictedObjects().getFirst();
      var secondRestored =
          restored[1].rawAnalysis().matchOccurrences().getFirst().indictedObjects().getFirst();
      assertThat(((GeneratedEntity) firstRestored).id).isEqualTo("first");
      if (reuseEntity) {
        assertThat(secondRestored).isSameAs(firstRestored);
      } else {
        assertThat(secondRestored).isNotSameAs(firstRestored);
        assertThat(((GeneratedEntity) secondRestored).id).isEqualTo("second");
      }
      assertThat(deserializer.objectCalls).isEqualTo(2);
      assertThat(deserializer.structuralCalls).isZero();
    }
  }

  @Test
  void mixedSharedAndStructuralOnlyEntitiesUseTheirOwnHooks() throws Exception {
    var shared = new GeneratedEntity("shared");
    var structural = new PropertyEntity("structural");
    var deserializer = new TypedDeserializer(GeneratedEntity.class, PropertyEntity.class);
    var mapper = mapper(deserializer);
    var json =
        mapper.writeValueAsString(
            analysis(
                shared,
                loop(
                    new EntityVariablePair(shared, "first"),
                    new EntityVariablePair(structural, "second")),
                loop(new EntityVariablePair(structural, "third"))));
    var tree = mapper.readTree(json);
    assertThat(tree.get("rawAnalysis").get("objects").size()).isEqualTo(2);
    assertThat(tree.get("rawAnalysis").get("structuralObjectIndexes").toString()).isEqualTo("[1]");

    ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
    var pairs =
        restored.structuralFlawAnalysis().getVariableLoops().getFirst().involvedVariableSet();
    var sharedEntity = pairs.iterator().next().entity();
    var structuralEntity = pairs.stream().skip(1).findFirst().orElseThrow().entity();
    assertThat(sharedEntity)
        .isInstanceOf(GeneratedEntity.class)
        .isSameAs(
            restored.rawAnalysis().matchOccurrences().getFirst().indictedObjects().getFirst());
    assertThat(structuralEntity).isInstanceOf(PropertyEntity.class);
    assertThat(
            restored
                .structuralFlawAnalysis()
                .getVariableLoops()
                .get(1)
                .involvedVariableSet()
                .iterator()
                .next()
                .entity())
        .isSameAs(structuralEntity);
    assertThat(deserializer.objectCalls).isOne();
    assertThat(deserializer.structuralCalls).isOne();
  }

  @Test
  void structuralOnlyEntitiesNeedNoIndictedObjectHookEvenWithEmptyRawRows() throws Exception {
    for (var entity : List.of(new GeneratedEntity("only"), new PropertyEntity("only"))) {
      var deserializer = new TypedDeserializer(null, entity.getClass());
      var mapper = mapper(deserializer);
      var json =
          mapper.writeValueAsString(analysis(null, loop(new EntityVariablePair(entity, "first"))));
      ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
      assertThat(restored.rawAnalysis().matchOccurrences()).isEmpty();
      assertThat(restored.rawAnalysis().indictmentMap()).isEmpty();
      assertThat(
              restored
                  .structuralFlawAnalysis()
                  .getVariableLoops()
                  .getFirst()
                  .involvedVariableSet()
                  .iterator()
                  .next()
                  .entity())
          .isInstanceOf(entity.getClass());
      assertThat(deserializer.objectCalls).isZero();
      assertThat(deserializer.structuralCalls).isOne();
    }
  }

  @Test
  void equalDistinctStructuralEntitiesKeepDistinctSlots() throws Exception {
    var first = new Item("same");
    var second = new Item("same");
    var deserializer = new TypedDeserializer(null, Item.class);
    var mapper = mapper(deserializer);
    var json =
        mapper.writeValueAsString(
            analysis(
                null,
                loop(
                    new EntityVariablePair(first, "first"),
                    new EntityVariablePair(second, "second"))));
    ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
    var entities =
        restored
            .structuralFlawAnalysis()
            .getVariableLoops()
            .getFirst()
            .involvedVariableSet()
            .stream()
            .map(EntityVariablePair::entity)
            .toList();
    assertThat(entities).hasSize(2);
    assertThat(entities.get(0)).isEqualTo(entities.get(1)).isNotSameAs(entities.get(1));
    assertThat(mapper.readTree(json).get("rawAnalysis").get("structuralObjectIndexes").toString())
        .isEqualTo("[0,1]");
    assertThat(deserializer.structuralCalls).isEqualTo(2);
  }

  @Test
  void inlineStructuralPayloadsKeepTheirExistingHookAndShape() throws Exception {
    for (boolean rawMode : List.of(false, true)) {
      var deserializer = new TypedDeserializer(null, Item.class);
      var mapper = mapper(deserializer);
      var raw =
          rawMode
              ? ",\"indictments\":[],\"rawAnalysis\":{\"originalsFetched\":false,\"objects\":[],\"matches\":[]}"
              : "";
      var json =
          """
          {"score":"0","initialized":true,"constraints":[],"structuralFlawAnalysis":{
           "variableLoops":[{"involvedVariableSet":[{"entity":{"id":"legacy"},"variableName":"first"}]}]}%s}
          """
              .formatted(raw);
      ScoreAnalysis<SimpleScore> restored = mapper.readValue(json, ScoreAnalysis.class);
      assertThat(
              restored
                  .structuralFlawAnalysis()
                  .getVariableLoops()
                  .getFirst()
                  .involvedVariableSet()
                  .iterator()
                  .next()
                  .entity())
          .isEqualTo(new Item("legacy"));
      assertThat(deserializer.objectCalls).isZero();
      assertThat(deserializer.structuralCalls).isOne();
      if (!rawMode) {
        var serialized = mapper.readTree(mapper.writeValueAsString(restored));
        var pair =
            serialized
                .get("structuralFlawAnalysis")
                .get("variableLoops")
                .get(0)
                .get("involvedVariableSet")
                .get(0);
        assertThat(pair.has("entity")).isTrue();
        assertThat(pair.has("entityIndex")).isFalse();
        assertThat(serialized.has("rawAnalysis")).isFalse();
      }
    }
  }

  @Test
  void invalidStructuralIndexesAndRolesAreRejected() {
    var mapper = mapper(new TypedDeserializer(Item.class, Item.class));
    for (var index : List.of("-1", "1", "0.5", "2147483648", "null", "\"0\"")) {
      assertThatThrownBy(
              () ->
                  mapper.readValue(
                      structuralJson().replace("\"entityIndex\":0", "\"entityIndex\":" + index),
                      ScoreAnalysis.class))
          .hasMessageMatching("(?is).*index.*");
      assertThatThrownBy(
              () ->
                  mapper.readValue(
                      structuralJson()
                          .replace(
                              "\"structuralObjectIndexes\":[0]",
                              "\"structuralObjectIndexes\":[" + index + "]"),
                      ScoreAnalysis.class))
          .hasMessageContaining("index");
    }
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    structuralJson()
                        .replace(
                            "\"structuralObjectIndexes\":[0]", "\"structuralObjectIndexes\":[0,0]"),
                    ScoreAnalysis.class))
        .hasMessageContaining("duplicate index");
    for (var marker : List.of("null", "{}", "0")) {
      assertThatThrownBy(
              () ->
                  mapper.readValue(
                      structuralJson()
                          .replace(
                              "\"structuralObjectIndexes\":[0]",
                              "\"structuralObjectIndexes\":" + marker),
                      ScoreAnalysis.class))
          .hasMessageContaining("must be an array");
    }
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    structuralJson().replace("\"entityIndex\":0", "\"entity\":{\"id\":\"inline\"}"),
                    ScoreAnalysis.class))
        .hasMessageContaining("not referenced");
    for (var entityFields : List.of("", "\"entityIndex\":0,\"entity\":{\"id\":\"both\"},")) {
      assertThatThrownBy(
              () ->
                  mapper.readValue(
                      structuralJson().replace("\"entityIndex\":0,", entityFields),
                      ScoreAnalysis.class))
          .hasMessageContaining("exactly one");
    }
    var withoutRaw =
        """
        {"score":"0","initialized":true,"constraints":[],"structuralFlawAnalysis":{
        "variableLoops":[{"involvedVariableSet":[{"entityIndex":0,"variableName":"first"}]}]}}
        """;
    assertThatThrownBy(() -> mapper.readValue(withoutRaw, ScoreAnalysis.class))
        .hasMessageContaining("requires rawAnalysis");
  }

  @Test
  void structuralOnlyRolesCannotBeUsedByRawReferences() {
    var mapper = mapper(new TypedDeserializer(Item.class, Item.class));
    var row =
        """
        {"id":"constraint","matchScore":"0","occurrenceSign":1,
        "justification":{"type":"default","impact":"0","factIndexes":[]},"indictedObjectIndexes":[]}
        """;
    for (var invalidRow :
        List.of(
            row.replace("\"indictedObjectIndexes\":[]", "\"indictedObjectIndexes\":[0]"),
            row.replace("\"factIndexes\":[]", "\"factIndexes\":[0]"),
            row.replace(
                "{\"type\":\"default\",\"impact\":\"0\",\"factIndexes\":[]}",
                "{\"type\":\"custom\",\"value\":0}"))) {
      assertThatThrownBy(
              () ->
                  mapper.readValue(
                      structuralJson()
                          .replace("\"matches\":[]", "\"matches\":[" + invalidRow + "]"),
                      ScoreAnalysis.class))
          .hasMessageContaining("structural-only");
    }
    var group = "{\"objectIndex\":0,\"score\":\"0\",\"matchCount\":0,\"matches\":[]}";
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    structuralJson()
                        .replace("\"indictments\":[]", "\"indictments\":[" + group + "]"),
                    ScoreAnalysis.class))
        .hasMessageContaining("structural-only");
  }

  private static String structuralJson() {
    return """
    {"score":"0","initialized":true,"constraints":[],"indictments":[],
     "structuralFlawAnalysis":{"variableLoops":[{"involvedVariableSet":[{"entityIndex":0,"variableName":"first"}]}]},
     "rawAnalysis":{"originalsFetched":false,"objects":[{"id":"only"}],"matches":[],"structuralObjectIndexes":[0]}}
    """;
  }

  private static ScoreAnalysis<SimpleScore> analysis(Object selected, VariableLoop... loops) {
    var groups = new LinkedHashMap<Object, IndictmentAnalysis<SimpleScore>>();
    if (selected == null) {
      return new DefaultScoreAnalysis<>(
          SimpleScore.ZERO,
          Map.of(),
          true,
          List.of(loops),
          new IndictmentMaps<>(groups, groups),
          new DefaultRawScoreAnalysis<>(List.of(), true));
    }
    var justification = DefaultConstraintJustification.of(SimpleScore.ONE, selected);
    var row =
        new DefaultMatchOccurrenceAnalysis<>(
            REF, SimpleScore.ONE, justification, 1, List.of(selected), List.of(selected));
    groups.put(
        selected,
        new DefaultIndictmentAnalysis<>(
            selected,
            SimpleScore.ONE,
            List.of(new DefaultIndictmentMatchAnalysis<>(REF, SimpleScore.ONE, justification, 1)),
            1));
    var constraint =
        new DefaultConstraintAnalysis<>(
            REF,
            SimpleScore.ONE,
            SimpleScore.ONE,
            List.of(new DefaultMatchAnalysis<>(REF, SimpleScore.ONE, justification)),
            1,
            1L);
    return new DefaultScoreAnalysis<>(
        SimpleScore.ONE,
        Map.of(REF, constraint),
        true,
        List.of(loops),
        new IndictmentMaps<>(groups, groups),
        new DefaultRawScoreAnalysis<>(List.of(row), true));
  }

  private static VariableLoop loop(EntityVariablePair... pairs) {
    return new VariableLoop(new LinkedHashSet<>(Arrays.asList(pairs)));
  }

  private static JsonMapper mapper(
      AbstractScoreAnalysisJacksonDeserializer<SimpleScore> deserializer) {
    var module = new SimpleModule();
    module.addDeserializer(ScoreAnalysis.class, deserializer);
    return JsonMapper.builder()
        .addModule(GreyCOSJacksonModule.createModule())
        .addModule(module)
        .build();
  }

  public record Item(String id) {}

  @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
  public static final class GeneratedEntity {
    public String id;

    public GeneratedEntity() {}

    GeneratedEntity(String id) {
      this.id = id;
    }
  }

  @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
  public static final class PropertyEntity {
    public String id;

    public PropertyEntity() {}

    PropertyEntity(String id) {
      this.id = id;
    }
  }

  private static final class TypedDeserializer
      extends AbstractScoreAnalysisJacksonDeserializer<SimpleScore> {
    private final Class<?> objectType;
    private final Class<?> structuralType;
    int objectCalls;
    int structuralCalls;

    TypedDeserializer(Class<?> objectType, Class<?> structuralType) {
      this.objectType = objectType;
      this.structuralType = structuralType;
    }

    @Override
    protected SimpleScore parseScore(String scoreString) {
      return SimpleScore.parseScore(scoreString);
    }

    @Override
    protected ConstraintJustification deserializeConstraintJustification(
        ConstraintRef ref, JsonNode node, DeserializationContext context, SimpleScore score) {
      throw new AssertionError("This fixture has no legacy custom justifications.");
    }

    @Override
    protected ConstraintJustification deserializeConstraintJustification(
        ConstraintRef ref,
        JsonNode node,
        DeserializationContext context,
        SimpleScore score,
        IntFunction<Object> resolveObject) {
      return DefaultConstraintJustification.of(score, resolveObject.apply(node.intValue()));
    }

    @Override
    protected Object deserializeIndictedObject(JsonNode node, DeserializationContext context)
        throws JacksonException {
      objectCalls++;
      if (objectType == null) {
        throw new AssertionError("The indicted-object hook must not be called.");
      }
      return context.readTreeAsValue(node, objectType);
    }

    @Override
    protected Object deserializeStructuralFlawEntity(JsonNode node, DeserializationContext context)
        throws JacksonException {
      structuralCalls++;
      if (structuralType == null) {
        throw new AssertionError("The structural-entity hook must not be called.");
      }
      return context.readTreeAsValue(node, structuralType);
    }
  }
}
