package greycos.solver.quarkus.jackson.score.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.RawScoreAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.analysis.StructuralFlawAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class ScoreAnalysisJacksonSerializer<Score_ extends Score<Score_>>
    extends JsonSerializer<ScoreAnalysis<Score_>> {
  @Override
  public void serialize(
      ScoreAnalysis<Score_> value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    var raw = value.rawAnalysis();
    var terminalMap = value.indictmentMap();
    var originalMap = value.originalIndictmentMap();
    Map<ConstraintRef, Long> rawCounts = null;
    if (raw != null) {
      if (terminalMap == null) {
        throw new IllegalArgumentException("Raw analysis requires fetched terminal indictments.");
      }
      if (raw.originalsFetched() != (originalMap != null)) {
        throw new IllegalArgumentException(
            "The raw originalsFetched flag must match originalIndictments availability.");
      }
      rawCounts = new LinkedHashMap<>();
      for (var occurrence : raw.matchOccurrences()) {
        rawCounts.merge(
            occurrence.constraintRef(), (long) occurrence.occurrenceSign(), Math::addExact);
      }
    }
    var objects = raw == null ? null : new ObjectTable();
    // Build every representation before writing the table: custom hooks may register more objects.
    var constraints = new ArrayList<Map<String, Object>>();
    for (var constraintAnalysis : value.constraintMap().values()) {
      var constraintRef = constraintAnalysis.constraintRef();
      var entry = new LinkedHashMap<String, Object>();
      entry.put("id", constraintRef.id());
      entry.put("weight", constraintAnalysis.weight().toString());
      entry.put("score", constraintAnalysis.score().toString());
      if (constraintAnalysis.matches() != null) {
        var matches = new ArrayList<Map<String, Object>>(constraintAnalysis.matches().size());
        for (var match : constraintAnalysis.matches()) {
          var matchEntry = new LinkedHashMap<String, Object>();
          matchEntry.put("score", match.score().toString());
          matchEntry.put(
              "justification",
              justification(
                  constraintRef, match.justification(), match.score(), serializers, objects));
          matches.add(matchEntry);
        }
        entry.put("matches", matches);
      }
      if (constraintAnalysis.isMatchCountAvailable()) {
        entry.put("matchCount", constraintAnalysis.matchCount());
      }
      if (rawCounts != null) {
        long actualRawCount = rawCounts.getOrDefault(constraintRef, 0L);
        var declaredRawCount = constraintAnalysis.rawMatchCount();
        if (declaredRawCount != null && declaredRawCount.longValue() != actualRawCount) {
          throw new IllegalArgumentException(
              "The rawMatchCount (%d) for constraint (%s) differs from the signed raw occurrence count (%d)."
                  .formatted(declaredRawCount, constraintRef, actualRawCount));
        }
        entry.put("rawMatchCount", actualRawCount);
      }
      constraints.add(entry);
    }
    var indictments =
        terminalMap == null ? null : serializeIndictments(terminalMap, serializers, objects);
    var originalIndictments =
        originalMap == null ? null : serializeIndictments(originalMap, serializers, objects);
    var rawRepresentation = raw == null ? null : serializeRawAnalysis(raw, serializers, objects);
    // Structural-only slots come last, after every custom hook has registered its raw objects.
    var structuralRepresentation =
        value.structuralFlawAnalysis() == null
            ? null
            : serializeStructuralFlaws(value.structuralFlawAnalysis(), objects);
    if (rawRepresentation != null && !objects.structuralObjectIndexes.isEmpty()) {
      rawRepresentation.put("structuralObjectIndexes", objects.structuralObjectIndexes);
    }

    gen.writeStartObject();
    gen.writeStringField("score", value.score().toString());
    gen.writeBooleanField("initialized", value.isSolutionInitialized());
    // The reader restores raw rows before grouped justifications, including Jackson object IDs.
    if (rawRepresentation != null) {
      writeProperty(gen, serializers, true, "rawAnalysis", rawRepresentation);
    }
    if (structuralRepresentation != null) {
      writeProperty(
          gen, serializers, raw != null, "structuralFlawAnalysis", structuralRepresentation);
    }
    writeProperty(gen, serializers, raw != null, "constraints", constraints);
    if (indictments != null) {
      writeProperty(gen, serializers, raw != null, "indictments", indictments);
    }
    if (originalIndictments != null) {
      writeProperty(gen, serializers, raw != null, "originalIndictments", originalIndictments);
    }
    gen.writeEndObject();
  }

  private static void writeProperty(
      JsonGenerator gen, SerializerProvider context, boolean rawMode, String name, Object value)
      throws IOException {
    if (rawMode) {
      // Keep all raw objects in the active serialization context, including Jackson object IDs.
      context.defaultSerializeField(name, value, gen);
    } else {
      gen.writeObjectField(name, value);
    }
  }

  private static Map<String, Object> serializeStructuralFlaws(
      StructuralFlawAnalysis flaws, ObjectTable objects) {
    if (objects == null) {
      return Map.of("variableLoops", flaws.getVariableLoops());
    }
    var loops = new ArrayList<Map<String, Object>>();
    for (var loop : flaws.getVariableLoops()) {
      var variables = new ArrayList<Map<String, Object>>();
      for (var pair : loop.involvedVariableSet()) {
        var variable = new LinkedHashMap<String, Object>();
        variable.put("entityIndex", objects.registerStructural(pair.entity()));
        variable.put("variableName", pair.variableName());
        variables.add(variable);
      }
      loops.add(Map.of("involvedVariableSet", variables));
    }
    return Map.of("variableLoops", loops);
  }

  private List<Map<String, Object>> serializeIndictments(
      Map<Object, IndictmentAnalysis<Score_>> indictments,
      SerializerProvider context,
      ObjectTable objects)
      throws IOException {
    var result = new ArrayList<Map<String, Object>>(indictments.size());
    for (var indictment : indictments.values()) {
      var entry = new LinkedHashMap<String, Object>();
      if (objects == null) {
        entry.put("indictedObject", indictment.indictedObject());
      } else {
        entry.put("objectIndex", objects.register(indictment.indictedObject()));
      }
      entry.put("score", indictment.score().toString());
      entry.put("matchCount", indictment.matchCount());
      var matches = new ArrayList<Map<String, Object>>(indictment.matches().size());
      for (var match : indictment.matches()) {
        var matchEntry = new LinkedHashMap<String, Object>();
        matchEntry.put("id", match.constraintRef().id());
        matchEntry.put("score", match.score().toString());
        matchEntry.put(
            "justification",
            justification(
                match.constraintRef(), match.justification(), match.score(), context, objects));
        matchEntry.put("matchCount", match.matchCount());
        matches.add(matchEntry);
      }
      entry.put("matches", matches);
      result.add(entry);
    }
    return result;
  }

  private Map<String, Object> serializeRawAnalysis(
      RawScoreAnalysis<Score_> raw, SerializerProvider context, ObjectTable objects)
      throws IOException {
    var matches = new ArrayList<Map<String, Object>>(raw.matchOccurrences().size());
    var seenMatches = new IdentityHashMap<Object, Boolean>();
    for (var match : raw.matchOccurrences()) {
      if (seenMatches.put(match, Boolean.TRUE) != null) {
        throw new IllegalArgumentException(
            "A canonical raw occurrence instance must not appear more than once.");
      }
      if (match.occurrenceSign() != 1 && match.occurrenceSign() != -1) {
        throw new IllegalArgumentException("The occurrenceSign must be +1 or -1.");
      }
      if (raw.originalsFetched() != (match.originalIndictedObjects() != null)) {
        throw new IllegalArgumentException(
            "The raw occurrence's original objects do not match originalsFetched.");
      }
      var entry = new LinkedHashMap<String, Object>();
      entry.put("id", match.constraintRef().id());
      entry.put("matchScore", match.matchScore().toString());
      entry.put("occurrenceSign", match.occurrenceSign());
      entry.put(
          "justification",
          justification(
              match.constraintRef(), match.justification(), match.matchScore(), context, objects));
      entry.put("indictedObjectIndexes", objectIndexes(match.indictedObjects(), objects, false));
      if (raw.originalsFetched()) {
        entry.put(
            "originalIndictedObjectIndexes",
            objectIndexes(match.originalIndictedObjects(), objects, true));
      }
      matches.add(entry);
    }
    var result = new LinkedHashMap<String, Object>();
    result.put("originalsFetched", raw.originalsFetched());
    result.put("objects", objects.values);
    result.put("matches", matches);
    return result;
  }

  private static List<Integer> objectIndexes(
      List<Object> values, ObjectTable objects, boolean requireDistinct) {
    var result = new ArrayList<Integer>(values.size());
    var seen = requireDistinct ? new IdentityHashMap<Object, Boolean>() : null;
    for (var value : values) {
      if (seen != null && seen.put(value, Boolean.TRUE) != null) {
        throw new IllegalArgumentException(
            "Original indicted objects must contain distinct object references.");
      }
      result.add(objects.register(value));
    }
    return result;
  }

  private Object justification(
      ConstraintRef constraintRef,
      ConstraintJustification justification,
      Score_ score,
      SerializerProvider context,
      ObjectTable objects)
      throws IOException {
    if (objects == null) {
      return justification instanceof DefaultConstraintJustification defaultJustification
          ? defaultJustification.getFacts()
          : justification;
    }
    var envelope = new LinkedHashMap<String, Object>();
    if (justification instanceof DefaultConstraintJustification defaultJustification) {
      envelope.put("type", "default");
      envelope.put("impact", defaultJustification.getImpact().toString());
      var factIndexes = new ArrayList<Integer>(defaultJustification.getFacts().size());
      for (var fact : defaultJustification.getFacts()) {
        factIndexes.add(fact == null ? null : objects.register(fact));
      }
      envelope.put("factIndexes", factIndexes);
    } else {
      envelope.put("type", "custom");
      envelope.put(
          "value",
          Objects.requireNonNull(
              serializeConstraintJustification(
                  constraintRef, justification, context, score, objects::register),
              "The custom justification serializer returned null."));
    }
    return envelope;
  }

  /**
   * Converts a custom justification to its raw-analysis JSON payload. The default preserves its
   * usual Jackson representation. Override and encode references using {@code registerObject} when
   * nested objects must share identity with raw selections or grouped indictments. Arbitrary nested
   * custom graphs are not traversed automatically. The callback is valid only during this
   * invocation; the returned payload must contain the resulting integer indexes, not the callback.
   */
  protected Object serializeConstraintJustification(
      ConstraintRef constraintRef,
      ConstraintJustification justification,
      SerializerProvider context,
      Score_ score,
      ToIntFunction<Object> registerObject)
      throws IOException {
    return justification;
  }

  private static final class ObjectTable {
    private final IdentityHashMap<Object, Integer> indexes = new IdentityHashMap<>();
    private final List<Object> values = new ArrayList<>();
    private final List<Integer> structuralObjectIndexes = new ArrayList<>();

    private int registerStructural(Object value) {
      var existing = indexes.get(value);
      if (existing != null) {
        return existing;
      }
      int index = register(value);
      structuralObjectIndexes.add(index);
      return index;
    }

    private int register(Object value) {
      Objects.requireNonNull(value, "A raw analysis object must not be null.");
      var existing = indexes.get(value);
      if (existing != null) {
        return existing;
      }
      int index = values.size();
      indexes.put(value, index);
      values.add(value);
      return index;
    }
  }
}
