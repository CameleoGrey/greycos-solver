package greycos.solver.quarkus.jackson.diff;

import java.io.IOException;

import greycos.solver.core.preview.api.cotwin.solution.diff.PlanningVariableDiff;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public final class PlanningVariableDiffJacksonSerializer<Solution_, Entity_, Value_>
    extends JsonSerializer<PlanningVariableDiff<Solution_, Entity_, Value_>> {

  @Override
  public void serialize(
      PlanningVariableDiff<Solution_, Entity_, Value_> variableDiff,
      JsonGenerator jsonGenerator,
      SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeObject(SerializablePlanningVariableDiff.of(variableDiff));
  }
}
