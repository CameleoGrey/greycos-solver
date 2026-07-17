package ai.greycos.solver.jackson.preview.api.cotwin.solution.diff;

import ai.greycos.solver.core.preview.api.cotwin.solution.diff.PlanningVariableDiff;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public final class PlanningVariableDiffJacksonSerializer<Solution_, Entity_, Value_>
    extends ValueSerializer<PlanningVariableDiff<Solution_, Entity_, Value_>> {

  @Override
  public void serialize(
      PlanningVariableDiff<Solution_, Entity_, Value_> variableDiff,
      JsonGenerator jsonGenerator,
      SerializationContext serializerProvider)
      throws JacksonException {
    jsonGenerator.writePOJO(SerializablePlanningVariableDiff.of(variableDiff));
  }
}
