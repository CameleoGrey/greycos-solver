package greycos.solver.jackson.api.score.analysis;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.function.IntFunction;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.ConstraintAnalysis;
import greycos.solver.core.api.score.analysis.EntityVariablePair;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.IndictmentMatchAnalysis;
import greycos.solver.core.api.score.analysis.MatchAnalysis;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.analysis.RawScoreAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.analysis.VariableLoop;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintProvider;
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

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Extend this to implement {@link ScoreAnalysis} deserialization specific for your cotwin.
 *
 * @param <Score_>
 */
public abstract class AbstractScoreAnalysisJacksonDeserializer<Score_ extends Score<Score_>>
    extends ValueDeserializer<ScoreAnalysis<Score_>> {

  @Override
  public final ScoreAnalysis<Score_> deserialize(JsonParser p, DeserializationContext ctxt)
      throws JacksonException {
    JsonNode node = p.readValueAsTree();
    var rawNode = node.get("rawAnalysis");
    var objects = rawNode == null || rawNode.isNull() ? null : deserializeObjects(rawNode, ctxt);
    var rawAnalysis = objects == null ? null : deserializeRawAnalysis(rawNode, objects, ctxt);
    Map<ConstraintRef, Long> rawCounts = null;
    if (rawAnalysis != null) {
      var terminalNode = node.get("indictments");
      if (terminalNode == null || terminalNode.isNull()) {
        throw new IllegalArgumentException("Raw analysis requires fetched terminal indictments.");
      }
      var originalNode = node.get("originalIndictments");
      if (rawAnalysis.originalsFetched() != (originalNode != null && !originalNode.isNull())) {
        throw new IllegalArgumentException(
            "The raw originalsFetched flag must match originalIndictments availability.");
      }
      rawCounts = new LinkedHashMap<>();
      for (var occurrence : rawAnalysis.matchOccurrences()) {
        rawCounts.merge(
            occurrence.constraintRef(), (long) occurrence.occurrenceSign(), Math::addExact);
      }
    }
    var score = parseScore(required(node, "score").asString());
    var initialized = required(node, "initialized").asBoolean();
    var constraintAnalysisList = new LinkedHashMap<ConstraintRef, ConstraintAnalysis<Score_>>();
    for (var constraintNode : required(node, "constraints")) {
      var constraintId = required(constraintNode, "id").asString();
      var constraintRef = ConstraintRef.of(constraintId);
      var constraintWeight = parseScore(required(constraintNode, "weight").asString());
      var constraintScore = parseScore(required(constraintNode, "score").asString());
      var matchScoreList = new ArrayList<MatchAnalysis<Score_>>();
      var matchesNode = constraintNode.get("matches");
      var matchCountNode = constraintNode.get("matchCount");
      var rawCountNode = constraintNode.get("rawMatchCount");
      Long rawMatchCount = rawCounts == null ? null : rawCounts.getOrDefault(constraintRef, 0L);
      if (rawMatchCount != null && rawCountNode != null) {
        long declaredRawCount = parseLong(rawCountNode, "rawMatchCount");
        if (declaredRawCount != rawMatchCount.longValue()) {
          throw new IllegalArgumentException(
              "The rawMatchCount (%d) for constraint (%s) differs from the signed raw occurrence count (%d)."
                  .formatted(declaredRawCount, constraintRef, rawMatchCount));
        }
      }
      if (matchesNode == null) {
        constraintAnalysisList.put(
            constraintRef,
            new DefaultConstraintAnalysis<>(
                constraintRef,
                constraintWeight,
                constraintScore,
                null,
                matchCountNode == null ? -1 : parseMatchCount(matchCountNode),
                rawMatchCount,
                matchCountNode != null));
      } else {
        var matchCount = parseMatchCount(required(constraintNode, "matchCount"));
        for (var matchNode : matchesNode) {
          var matchScore = parseScore(required(matchNode, "score").asString());
          var justificationNode = required(matchNode, "justification");
          var parsedJustification =
              Objects.requireNonNull(
                  deserializeJustification(
                      constraintRef, justificationNode, ctxt, matchScore, objects),
                  () ->
                      "The constraint justification deserializer returned null for constraint (%s)."
                          .formatted(constraintRef));
          matchScoreList.add(
              new DefaultMatchAnalysis<>(constraintRef, matchScore, parsedJustification));
        }
        constraintAnalysisList.put(
            constraintRef,
            new DefaultConstraintAnalysis<>(
                constraintRef,
                constraintWeight,
                constraintScore,
                matchScoreList,
                matchCount,
                rawMatchCount,
                true));
      }
    }
    var variableLoops = new ArrayList<VariableLoop>();
    var structuralNode = node.get("structuralFlawAnalysis");
    if (structuralNode != null && !structuralNode.isNull()) {
      var loopsNode = required(structuralNode, "variableLoops");
      requireArray(loopsNode, "variableLoops");
      for (var loopNode : loopsNode) {
        var variables = new LinkedHashSet<EntityVariablePair>();
        var pairsNode = required(loopNode, "involvedVariableSet");
        requireArray(pairsNode, "involvedVariableSet");
        for (var pairNode : pairsNode) {
          if (pairNode.has("entity") == pairNode.has("entityIndex")) {
            throw new IllegalArgumentException(
                "A structural variable pair must contain exactly one of entity or entityIndex.");
          }
          Object entity;
          if (pairNode.has("entityIndex")) {
            if (objects == null) {
              throw new IllegalArgumentException("A structural entityIndex requires rawAnalysis.");
            }
            entity = objects.resolveStructural(required(pairNode, "entityIndex"));
          } else {
            entity = deserializeStructuralFlawEntity(required(pairNode, "entity"), ctxt);
          }
          variables.add(
              new EntityVariablePair(entity, required(pairNode, "variableName").asString()));
        }
        variableLoops.add(new VariableLoop(variables));
      }
    }
    if (objects != null) {
      objects.validateStructuralReferences();
    }
    return new DefaultScoreAnalysis<>(
        score,
        constraintAnalysisList,
        initialized,
        variableLoops,
        new IndictmentMaps<>(
            deserializeIndictments(node, "indictments", ctxt, objects),
            deserializeIndictments(node, "originalIndictments", ctxt, objects)),
        rawAnalysis);
  }

  /**
   * The cotwin is based on a single {@link Score} subtype. This method is responsible for parsing
   * the score string into that subtype.
   *
   * @param scoreString never null
   * @return never null
   */
  protected abstract Score_ parseScore(String scoreString);

  /**
   * Each {@link Constraint} in the {@link ConstraintProvider} is justified with a custom
   * implementation {@link ConstraintJustification}. This method is responsible for deserializing
   * the justification.
   *
   * @param constraintRef never null
   * @param constraintJustificationNode never null
   * @param context never null
   * @param score never null
   * @return never null
   */
  protected abstract ConstraintJustification deserializeConstraintJustification(
      ConstraintRef constraintRef,
      JsonNode constraintJustificationNode,
      DeserializationContext context,
      Score_ score)
      throws JacksonException;

  /**
   * Override to restore the cotwin type of entities involved in variable loops. In raw analysis,
   * this hook restores each structural-only object-table slot once. Entities shared with raw
   * selections, justification facts or custom registered references are instead restored once by
   * {@link #deserializeIndictedObject(JsonNode, DeserializationContext)} and reused by the loops.
   * Legacy inline loop entities continue to use this hook directly.
   */
  protected Object deserializeStructuralFlawEntity(
      JsonNode entityNode, DeserializationContext context) throws JacksonException {
    return context.readTreeAsValue(entityNode, Object.class);
  }

  /**
   * Override to restore the cotwin type of an indicted object. Returning an untyped map would lose
   * the object's equality semantics, so nonempty indictment arrays require this hook. Existing
   * deserializers can still read analyses whose indictment views are absent or empty. This hook
   * also restores each non-structural-only raw object-table slot, including default justification
   * facts and entities shared with variable loops. Structural-only slots use {@link
   * #deserializeStructuralFlawEntity(JsonNode, DeserializationContext)} instead. Exactly one of
   * these hooks is called per slot; they must return distinct references for distinct slots. Equal
   * objects must not be interned or otherwise collapsed.
   *
   * @param indictedObjectNode never null
   * @param context never null
   * @return never null
   */
  protected Object deserializeIndictedObject(
      JsonNode indictedObjectNode, DeserializationContext context) throws JacksonException {
    throw new UnsupportedOperationException(
        "Deserializing nonempty indictment arrays requires overriding deserializeIndictedObject() "
            + "to restore the indicted object's cotwin type.");
  }

  private SequencedMap<Object, IndictmentAnalysis<Score_>> deserializeIndictments(
      JsonNode node, String propertyName, DeserializationContext context, ObjectTable objects)
      throws JacksonException {
    var indictmentsNode = node.get(propertyName);
    if (indictmentsNode == null || indictmentsNode.isNull()) {
      return null;
    }
    if (!indictmentsNode.isArray()) {
      throw new IllegalArgumentException(
          "The JSON property (%s) must be an array, but was (%s)."
              .formatted(propertyName, indictmentsNode));
    }
    var indictments = new LinkedHashMap<Object, IndictmentAnalysis<Score_>>();
    for (var indictmentNode : indictmentsNode) {
      Object indictedObject;
      if (objects == null) {
        if (indictmentNode.has("objectIndex")) {
          throw new IllegalArgumentException("An objectIndex requires rawAnalysis.");
        }
        indictedObject =
            Objects.requireNonNull(
                deserializeIndictedObject(required(indictmentNode, "indictedObject"), context),
                "The indicted object deserializer returned null.");
      } else {
        if (indictmentNode.has("indictedObject")) {
          throw new IllegalArgumentException(
              "A raw analysis indictment must use objectIndex, not indictedObject.");
        }
        indictedObject = objects.resolve(required(indictmentNode, "objectIndex"), "objectIndex");
      }
      var indictmentScore = parseScore(required(indictmentNode, "score").asString());
      var matchCount = parseIndictmentMatchCount(required(indictmentNode, "matchCount"));
      var matchesNode = required(indictmentNode, "matches");
      if (!matchesNode.isArray()) {
        throw new IllegalArgumentException(
            "The indictment JSON property (matches) must be an array, but was (%s)."
                .formatted(matchesNode));
      }
      var matches = new ArrayList<IndictmentMatchAnalysis<Score_>>(matchesNode.size());
      for (var matchNode : matchesNode) {
        var constraintRef = ConstraintRef.of(required(matchNode, "id").asString());
        var matchScore = parseScore(required(matchNode, "score").asString());
        var justification =
            Objects.requireNonNull(
                deserializeJustification(
                    constraintRef,
                    required(matchNode, "justification"),
                    context,
                    matchScore,
                    objects),
                () ->
                    "The constraint justification deserializer returned null for constraint (%s)."
                        .formatted(constraintRef));
        matches.add(
            new DefaultIndictmentMatchAnalysis<>(
                constraintRef,
                matchScore,
                justification,
                parseIndictmentMatchCount(required(matchNode, "matchCount"))));
      }
      if (indictments.putIfAbsent(
              indictedObject,
              new DefaultIndictmentAnalysis<>(indictedObject, indictmentScore, matches, matchCount))
          != null) {
        throw new IllegalArgumentException(
            "The JSON property (%s) contains duplicate indicted objects (%s)."
                .formatted(propertyName, indictedObject));
      }
    }
    return indictments;
  }

  /**
   * Restores a custom justification's raw-analysis payload. The default delegates to the existing
   * hook. Override to resolve explicitly encoded nested references through {@code resolveObject};
   * arbitrary custom object graphs are not reconstructed automatically. The callback is scoped to
   * this analysis and must not be retained.
   */
  protected ConstraintJustification deserializeConstraintJustification(
      ConstraintRef constraintRef,
      JsonNode node,
      DeserializationContext context,
      Score_ score,
      IntFunction<Object> resolveObject)
      throws JacksonException {
    return deserializeConstraintJustification(constraintRef, node, context, score);
  }

  private ConstraintJustification deserializeJustification(
      ConstraintRef constraintRef,
      JsonNode node,
      DeserializationContext context,
      Score_ score,
      ObjectTable objects)
      throws JacksonException {
    if (objects == null) {
      return deserializeConstraintJustification(constraintRef, node, context, score);
    }
    var type = required(node, "type").asString();
    if (type.equals("default")) {
      var impact = parseScore(required(node, "impact").asString());
      var factsNode = required(node, "factIndexes");
      requireArray(factsNode, "factIndexes");
      var facts = new ArrayList<Object>(factsNode.size());
      for (var factIndex : factsNode) {
        facts.add(factIndex.isNull() ? null : objects.resolve(factIndex, "factIndexes"));
      }
      return DefaultConstraintJustification.of(impact, facts);
    } else if (type.equals("custom")) {
      return deserializeConstraintJustification(
          constraintRef, required(node, "value"), context, score, objects::resolve);
    }
    throw new IllegalArgumentException(
        "The raw justification type (%s) must be default or custom.".formatted(type));
  }

  private ObjectTable deserializeObjects(JsonNode rawNode, DeserializationContext context)
      throws JacksonException {
    if (!rawNode.isObject()) {
      throw new IllegalArgumentException("The JSON property (rawAnalysis) must be an object.");
    }
    var objectsNode = required(rawNode, "objects");
    requireArray(objectsNode, "objects");
    var structuralObjects = new BitSet(objectsNode.size());
    var structuralIndexesNode = rawNode.get("structuralObjectIndexes");
    if (structuralIndexesNode != null) {
      requireArray(structuralIndexesNode, "structuralObjectIndexes");
      for (var indexNode : structuralIndexesNode) {
        int index = parseObjectIndex(indexNode, "structuralObjectIndexes", objectsNode.size());
        if (structuralObjects.get(index)) {
          throw new IllegalArgumentException(
              "The JSON property (structuralObjectIndexes) contains duplicate index (%d)."
                  .formatted(index));
        }
        structuralObjects.set(index);
      }
    }
    var objects = new ArrayList<Object>(objectsNode.size());
    var seen = new IdentityHashMap<Object, Integer>();
    for (var objectNode : objectsNode) {
      if (objectNode.isNull()) {
        throw new IllegalArgumentException("A raw object-table payload must not be null.");
      }
      var object =
          Objects.requireNonNull(
              structuralObjects.get(objects.size())
                  ? deserializeStructuralFlawEntity(objectNode, context)
                  : deserializeIndictedObject(objectNode, context),
              "The raw object-table deserializer returned null.");
      var prior = seen.put(object, objects.size());
      if (prior != null) {
        throw new IllegalArgumentException(
            "The raw object-table deserializer collapsed distinct slots (%d) and (%d) to the same reference. Restore a distinct object for each slot."
                .formatted(prior, objects.size()));
      }
      objects.add(object);
    }
    return new ObjectTable(objects, structuralObjects, new BitSet(objects.size()));
  }

  private RawScoreAnalysis<Score_> deserializeRawAnalysis(
      JsonNode rawNode, ObjectTable objects, DeserializationContext context)
      throws JacksonException {
    var originalsNode = required(rawNode, "originalsFetched");
    if (!originalsNode.isBoolean()) {
      throw new IllegalArgumentException("The JSON property (originalsFetched) must be a boolean.");
    }
    boolean originalsFetched = originalsNode.asBoolean();
    var matchesNode = required(rawNode, "matches");
    requireArray(matchesNode, "matches");
    var matches = new ArrayList<MatchOccurrenceAnalysis<Score_>>(matchesNode.size());
    for (var matchNode : matchesNode) {
      var constraintRef = ConstraintRef.of(required(matchNode, "id").asString());
      var matchScore = parseScore(required(matchNode, "matchScore").asString());
      var sign = parseLong(required(matchNode, "occurrenceSign"), "occurrenceSign");
      if (sign != 1 && sign != -1) {
        throw new IllegalArgumentException("The occurrenceSign must be +1 or -1.");
      }
      var justification =
          Objects.requireNonNull(
              deserializeJustification(
                  constraintRef,
                  required(matchNode, "justification"),
                  context,
                  matchScore,
                  objects),
              "The raw justification deserializer returned null.");
      var terminal =
          objectList(
              required(matchNode, "indictedObjectIndexes"),
              objects,
              "indictedObjectIndexes",
              false);
      List<Object> original = null;
      if (originalsFetched) {
        original =
            objectList(
                required(matchNode, "originalIndictedObjectIndexes"),
                objects,
                "originalIndictedObjectIndexes",
                true);
      } else if (matchNode.has("originalIndictedObjectIndexes")) {
        throw new IllegalArgumentException(
            "originalIndictedObjectIndexes must be absent when originalsFetched is false.");
      }
      matches.add(
          new DefaultMatchOccurrenceAnalysis<>(
              constraintRef, matchScore, justification, (int) sign, terminal, original));
    }
    return new DefaultRawScoreAnalysis<>(matches, originalsFetched);
  }

  private static List<Object> objectList(
      JsonNode node, ObjectTable objects, String propertyName, boolean requireDistinct) {
    requireArray(node, propertyName);
    var values = new ArrayList<Object>(node.size());
    var seen = requireDistinct ? new IdentityHashMap<Object, Boolean>() : null;
    for (var indexNode : node) {
      var value = objects.resolve(indexNode, propertyName);
      if (seen != null && seen.put(value, Boolean.TRUE) != null) {
        throw new IllegalArgumentException(
            "The JSON property (%s) must contain distinct object references."
                .formatted(propertyName));
      }
      values.add(value);
    }
    return values;
  }

  private static void requireArray(JsonNode node, String propertyName) {
    if (!node.isArray()) {
      throw new IllegalArgumentException(
          "The JSON property (%s) must be an array, but was (%s).".formatted(propertyName, node));
    }
  }

  private static long parseLong(JsonNode node, String propertyName) {
    if (!node.isIntegralNumber() || !node.canConvertToLong()) {
      throw new IllegalArgumentException(
          "The JSON property (%s) must be a 64-bit integer, but was (%s)."
              .formatted(propertyName, node));
    }
    return node.longValue();
  }

  private static int parseObjectIndex(JsonNode node, String propertyName, int size) {
    if (!node.isIntegralNumber() || !node.canConvertToInt()) {
      throw new IllegalArgumentException(
          "The JSON property (%s) must contain integer object indexes, but was (%s)."
              .formatted(propertyName, node));
    }
    int index = node.intValue();
    validateObjectIndex(index, size);
    return index;
  }

  private static void validateObjectIndex(int index, int size) {
    if (index < 0 || index >= size) {
      throw new IllegalArgumentException(
          "The raw object index (%d) is outside the object table of size (%d)."
              .formatted(index, size));
    }
  }

  private record ObjectTable(
      List<Object> values, BitSet structuralObjects, BitSet structuralReferences) {
    Object resolve(JsonNode node, String propertyName) {
      return resolve(parseObjectIndex(node, propertyName, values.size()));
    }

    Object resolve(int index) {
      validateObjectIndex(index, values.size());
      if (structuralObjects.get(index)) {
        throw new IllegalArgumentException(
            "The structural-only object index (%d) must not be used as a raw object reference."
                .formatted(index));
      }
      return values.get(index);
    }

    Object resolveStructural(JsonNode node) {
      int index = parseObjectIndex(node, "entityIndex", values.size());
      structuralReferences.set(index);
      return values.get(index);
    }

    void validateStructuralReferences() {
      var missing = (BitSet) structuralObjects.clone();
      missing.andNot(structuralReferences);
      if (!missing.isEmpty()) {
        throw new IllegalArgumentException(
            "The structural-only object indexes (%s) are not referenced by a structural entityIndex."
                .formatted(missing));
      }
    }
  }

  private static long parseIndictmentMatchCount(JsonNode matchCountNode) {
    if (!matchCountNode.isIntegralNumber() || !matchCountNode.canConvertToLong()) {
      throw new IllegalArgumentException(
          "The indictment JSON property (matchCount) must be a 64-bit integer, but was (%s)."
              .formatted(matchCountNode));
    }
    return matchCountNode.longValue();
  }

  private static JsonNode required(JsonNode parent, String propertyName) {
    var value = parent.get(propertyName);
    if (value == null || value.isNull()) {
      throw new IllegalArgumentException(
          "The required JSON property (%s) is missing or null.".formatted(propertyName));
    }
    return value;
  }

  private static int parseMatchCount(JsonNode matchCountNode) {
    if (!matchCountNode.isIntegralNumber() || !matchCountNode.canConvertToInt()) {
      throw new IllegalArgumentException(
          "The JSON property (matchCount) must be a 32-bit integer, but was (%s)."
              .formatted(matchCountNode));
    }
    return matchCountNode.intValue();
  }
}
