package ai.greycos.solver.jackson.api.solver;

import java.io.IOException;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.solver.RecommendedAssignment;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public final class RecommendedAssignmentJacksonSerializer<
        Proposition_, Score_ extends Score<Score_>>
    extends JsonSerializer<RecommendedAssignment<Proposition_, Score_>> {
  @Override
  public void serialize(
      RecommendedAssignment<Proposition_, Score_> value,
      JsonGenerator gen,
      SerializerProvider serializerProvider)
      throws IOException {
    gen.writeStartObject();
    gen.writeObjectField("proposition", value.proposition());
    gen.writeObjectField("scoreDiff", value.scoreAnalysisDiff());
    gen.writeEndObject();
  }
}
