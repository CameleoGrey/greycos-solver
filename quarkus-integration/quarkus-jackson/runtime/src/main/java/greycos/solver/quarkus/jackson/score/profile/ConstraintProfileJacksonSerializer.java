package greycos.solver.quarkus.jackson.score.profile;

import java.io.IOException;
import java.util.List;

import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.api.score.profile.ConstraintProfileSection;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/** Writes the versioned profile schema independently of bean and null-inclusion settings. */
public final class ConstraintProfileJacksonSerializer extends JsonSerializer<ConstraintProfile> {

  @Override
  public void serialize(ConstraintProfile value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeStartObject();
    gen.writeNumberField("schemaVersion", value.schemaVersion());
    gen.writeStringField("outcome", value.outcome().name());
    writeSection(gen, "initialization", value.initialization());
    writeSection(gen, "search", value.search());
    writeSection(gen, "combined", value.combined());
    gen.writeArrayFieldStart("generations");
    for (var generation : value.generations()) {
      gen.writeStartObject();
      gen.writeNumberField("generationId", generation.generationId());
      gen.writeBooleanField("initializationCompleted", generation.initializationCompleted());
      gen.writeArrayFieldStart("ownershipEpochs");
      for (var epoch : generation.ownershipEpochs()) {
        gen.writeStartObject();
        gen.writeNumberField("ownershipEpoch", epoch.ownershipEpoch());
        gen.writeStringField("ownershipState", epoch.ownershipState().name());
        gen.writeEndObject();
      }
      gen.writeEndArray();
      gen.writeArrayFieldStart("constraints");
      for (var constraint : generation.constraints()) {
        gen.writeStartObject();
        gen.writeStringField("constraintRef", constraint.constraintRef().id());
        gen.writeStringField("activity", constraint.activity().name());
        gen.writeEndObject();
      }
      gen.writeEndArray();
      gen.writeEndObject();
    }
    gen.writeEndArray();
    gen.writeArrayFieldStart("units");
    for (var unit : value.units()) {
      gen.writeStartObject();
      gen.writeNumberField("generationId", unit.generationId());
      gen.writeNumberField("unitId", unit.unitId());
      gen.writeStringField("kind", unit.kind());
      gen.writeStringField("role", unit.role().name());
      gen.writeArrayFieldStart("sourceLocations");
      for (var source : unit.sourceLocations()) {
        gen.writeStartObject();
        gen.writeStringField("className", source.className());
        gen.writeStringField("methodName", source.methodName());
        if (source.lineNumber() == null) {
          gen.writeNullField("lineNumber");
        } else {
          gen.writeNumberField("lineNumber", source.lineNumber());
        }
        gen.writeEndObject();
      }
      gen.writeEndArray();
      gen.writeArrayFieldStart("measurements");
      for (var measurement : unit.measurements()) {
        gen.writeStartObject();
        gen.writeStringField("stage", measurement.stage().name());
        gen.writeNumberField("ownershipEpoch", measurement.ownershipEpoch());
        gen.writeStringField("ownershipState", measurement.ownershipState().name());
        gen.writeStringField("operation", measurement.operation().name());
        writeOwners(gen, measurement.owners());
        gen.writeNumberField("exclusiveNanos", measurement.exclusiveNanos());
        gen.writeNumberField("eventCount", measurement.eventCount());
        gen.writeNumberField("failureCount", measurement.failureCount());
        gen.writeBooleanField("partialInvocations", measurement.partialInvocations());
        gen.writeEndObject();
      }
      gen.writeEndArray();
      gen.writeEndObject();
    }
    gen.writeEndArray();
    gen.writeEndObject();
  }

  private static void writeSection(JsonGenerator gen, String name, ConstraintProfileSection section)
      throws IOException {
    gen.writeFieldName(name);
    gen.writeStartObject();
    gen.writeNumberField("scoringWallNanos", section.scoringWallNanos());
    gen.writeNumberField("measuredNanos", section.measuredNanos());
    gen.writeNumberField("unmeasuredNanos", section.unmeasuredNanos());
    gen.writeNumberField("eventCount", section.eventCount());
    gen.writeNumberField("failureCount", section.failureCount());
    writeNullableNumber(gen, "coverage", section.coverage());
    gen.writeArrayFieldStart("buckets");
    for (var bucket : section.buckets()) {
      gen.writeStartObject();
      writeOwners(gen, bucket.owners());
      gen.writeNumberField("exclusiveNanos", bucket.exclusiveNanos());
      gen.writeNumberField("eventCount", bucket.eventCount());
      gen.writeNumberField("failureCount", bucket.failureCount());
      gen.writeBooleanField("partialInvocations", bucket.partialInvocations());
      writeNullableNumber(gen, "measuredShare", bucket.measuredShare());
      writeNullableNumber(gen, "averageNanosPerCall", bucket.averageNanosPerCall());
      gen.writeEndObject();
    }
    gen.writeEndArray();
    gen.writeEndObject();
  }

  private static void writeOwners(JsonGenerator gen, List<ConstraintRef> owners)
      throws IOException {
    gen.writeArrayFieldStart("owners");
    for (var owner : owners) {
      gen.writeString(owner.id());
    }
    gen.writeEndArray();
  }

  private static void writeNullableNumber(JsonGenerator gen, String name, @Nullable Double value)
      throws IOException {
    if (value == null) {
      gen.writeNullField(name);
    } else {
      gen.writeNumberField(name, value);
    }
  }
}
