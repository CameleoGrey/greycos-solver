package greycos.solver.jackson.api.score.profile;

import java.util.List;

import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.api.score.profile.ConstraintProfileSection;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.Nullable;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Writes the versioned profile schema independently of bean and null-inclusion settings. */
public final class ConstraintProfileJacksonSerializer extends ValueSerializer<ConstraintProfile> {

  @Override
  public void serialize(
      ConstraintProfile value, JsonGenerator gen, SerializationContext serializers)
      throws JacksonException {
    gen.writeStartObject();
    gen.writeNumberProperty("schemaVersion", value.schemaVersion());
    gen.writeStringProperty("outcome", value.outcome().name());
    writeSection(gen, "initialization", value.initialization());
    writeSection(gen, "search", value.search());
    writeSection(gen, "combined", value.combined());
    gen.writeArrayPropertyStart("generations");
    for (var generation : value.generations()) {
      gen.writeStartObject();
      gen.writeNumberProperty("generationId", generation.generationId());
      gen.writeBooleanProperty("initializationCompleted", generation.initializationCompleted());
      gen.writeArrayPropertyStart("ownershipEpochs");
      for (var epoch : generation.ownershipEpochs()) {
        gen.writeStartObject();
        gen.writeNumberProperty("ownershipEpoch", epoch.ownershipEpoch());
        gen.writeStringProperty("ownershipState", epoch.ownershipState().name());
        gen.writeEndObject();
      }
      gen.writeEndArray();
      gen.writeArrayPropertyStart("constraints");
      for (var constraint : generation.constraints()) {
        gen.writeStartObject();
        gen.writeStringProperty("constraintRef", constraint.constraintRef().id());
        gen.writeStringProperty("activity", constraint.activity().name());
        gen.writeEndObject();
      }
      gen.writeEndArray();
      gen.writeEndObject();
    }
    gen.writeEndArray();
    gen.writeArrayPropertyStart("units");
    for (var unit : value.units()) {
      gen.writeStartObject();
      gen.writeNumberProperty("generationId", unit.generationId());
      gen.writeNumberProperty("unitId", unit.unitId());
      gen.writeStringProperty("kind", unit.kind());
      gen.writeStringProperty("role", unit.role().name());
      gen.writeArrayPropertyStart("sourceLocations");
      for (var source : unit.sourceLocations()) {
        gen.writeStartObject();
        gen.writeStringProperty("className", source.className());
        gen.writeStringProperty("methodName", source.methodName());
        if (source.lineNumber() == null) {
          gen.writeNullProperty("lineNumber");
        } else {
          gen.writeNumberProperty("lineNumber", source.lineNumber());
        }
        gen.writeEndObject();
      }
      gen.writeEndArray();
      gen.writeArrayPropertyStart("measurements");
      for (var measurement : unit.measurements()) {
        gen.writeStartObject();
        gen.writeStringProperty("stage", measurement.stage().name());
        gen.writeNumberProperty("ownershipEpoch", measurement.ownershipEpoch());
        gen.writeStringProperty("ownershipState", measurement.ownershipState().name());
        gen.writeStringProperty("operation", measurement.operation().name());
        writeOwners(gen, measurement.owners());
        gen.writeNumberProperty("exclusiveNanos", measurement.exclusiveNanos());
        gen.writeNumberProperty("eventCount", measurement.eventCount());
        gen.writeNumberProperty("failureCount", measurement.failureCount());
        gen.writeBooleanProperty("partialInvocations", measurement.partialInvocations());
        gen.writeEndObject();
      }
      gen.writeEndArray();
      gen.writeEndObject();
    }
    gen.writeEndArray();
    gen.writeEndObject();
  }

  private static void writeSection(JsonGenerator gen, String name, ConstraintProfileSection section)
      throws JacksonException {
    gen.writeName(name);
    gen.writeStartObject();
    gen.writeNumberProperty("scoringWallNanos", section.scoringWallNanos());
    gen.writeNumberProperty("measuredNanos", section.measuredNanos());
    gen.writeNumberProperty("unmeasuredNanos", section.unmeasuredNanos());
    gen.writeNumberProperty("eventCount", section.eventCount());
    gen.writeNumberProperty("failureCount", section.failureCount());
    writeNullableNumber(gen, "coverage", section.coverage());
    gen.writeArrayPropertyStart("buckets");
    for (var bucket : section.buckets()) {
      gen.writeStartObject();
      writeOwners(gen, bucket.owners());
      gen.writeNumberProperty("exclusiveNanos", bucket.exclusiveNanos());
      gen.writeNumberProperty("eventCount", bucket.eventCount());
      gen.writeNumberProperty("failureCount", bucket.failureCount());
      gen.writeBooleanProperty("partialInvocations", bucket.partialInvocations());
      writeNullableNumber(gen, "measuredShare", bucket.measuredShare());
      writeNullableNumber(gen, "averageNanosPerCall", bucket.averageNanosPerCall());
      gen.writeEndObject();
    }
    gen.writeEndArray();
    gen.writeEndObject();
  }

  private static void writeOwners(JsonGenerator gen, List<ConstraintRef> owners)
      throws JacksonException {
    gen.writeArrayPropertyStart("owners");
    for (var owner : owners) {
      gen.writeString(owner.id());
    }
    gen.writeEndArray();
  }

  private static void writeNullableNumber(JsonGenerator gen, String name, @Nullable Double value)
      throws JacksonException {
    if (value == null) {
      gen.writeNullProperty(name);
    } else {
      gen.writeNumberProperty(name, value);
    }
  }
}
