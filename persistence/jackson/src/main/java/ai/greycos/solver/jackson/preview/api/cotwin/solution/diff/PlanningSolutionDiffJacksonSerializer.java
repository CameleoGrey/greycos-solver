package ai.greycos.solver.jackson.preview.api.cotwin.solution.diff;

import java.io.IOException;

import ai.greycos.solver.core.api.cotwin.solution.diff.PlanningSolutionDiff;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public final class PlanningSolutionDiffJacksonSerializer<Solution_>
    extends JsonSerializer<PlanningSolutionDiff<Solution_>> {

  @Override
  public void serialize(
      PlanningSolutionDiff<Solution_> solutionDiff,
      JsonGenerator jsonGenerator,
      SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeObject(SerializablePlanningSolutionDiff.of(solutionDiff));
  }
}
