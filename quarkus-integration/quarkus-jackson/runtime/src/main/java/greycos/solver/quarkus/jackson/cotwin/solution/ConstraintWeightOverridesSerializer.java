package greycos.solver.quarkus.jackson.cotwin.solution;

import java.io.IOException;
import java.util.Objects;

import greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import greycos.solver.core.api.score.Score;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public final class ConstraintWeightOverridesSerializer<Score_ extends Score<Score_>>
    extends JsonSerializer<ConstraintWeightOverrides<Score_>> {

  @Override
  public void serialize(
      ConstraintWeightOverrides<Score_> constraintWeightOverrides,
      JsonGenerator generator,
      SerializerProvider serializerProvider)
      throws IOException {
    generator.writeStartObject();
    for (var constraintId : constraintWeightOverrides.getKnownConstraintIds()) {
      var weight =
          Objects.requireNonNull(constraintWeightOverrides.getConstraintWeight(constraintId));
      generator.writeStringField(constraintId, weight.toString());
    }
    generator.writeEndObject();
  }
}
