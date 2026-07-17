package ai.greycos.solver.jackson.preview.api.cotwin.solution.diff;

import ai.greycos.solver.core.preview.api.cotwin.solution.diff.PlanningSolutionDiff;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public final class PlanningSolutionDiffJacksonSerializer<Solution_>
    extends ValueSerializer<PlanningSolutionDiff<Solution_>> {

  @Override
  public void serialize(
      PlanningSolutionDiff<Solution_> solutionDiff,
      JsonGenerator jsonGenerator,
      SerializationContext serializerProvider)
      throws JacksonException {
    jsonGenerator.writePOJO(SerializablePlanningSolutionDiff.of(solutionDiff));
  }
}
