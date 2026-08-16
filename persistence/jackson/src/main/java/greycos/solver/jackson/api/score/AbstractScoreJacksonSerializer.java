package greycos.solver.jackson.api.score;

import greycos.solver.core.api.score.Score;
import greycos.solver.jackson.api.GreyCOSJacksonModule;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Jackson binding support for a {@link Score} subtype. For a {@link Score} field, use {@link
 * PolymorphicScoreJacksonSerializer} instead, so the score type is recorded too and it can be
 * deserialized.
 *
 * <p>For example: use {@code @JsonSerialize(using =
 * HardSoftScoreJacksonSerializer.class) @JsonDeserialize(using =
 * HardSoftScoreJacksonDeserializer.class)} on a {@code HardSoftScore score} field and it will
 * marshalled to JSON as {@code "score":"-999hard/-999soft"}. Or better yet, use {@link
 * GreyCOSJacksonModule} instead.
 *
 * @see Score
 * @param <Score_> the actual score type
 */
public abstract class AbstractScoreJacksonSerializer<Score_ extends Score<Score_>>
    extends ValueSerializer<Score_> {

  @Override
  public ValueSerializer<?> createContextual(SerializationContext provider, BeanProperty property)
      throws DatabindException {
    JavaType propertyType = property.getType();
    if (Score.class.equals(propertyType.getRawClass())) {
      // If the property type is Score (not HardSoftScore for example),
      // delegate to PolymorphicScoreJacksonSerializer instead to write the score type too
      // This presumes that GreyCOSJacksonModule is registered
      return provider.findValueSerializer(propertyType);
    }
    return this;
  }

  @Override
  public void serialize(Score_ score, JsonGenerator generator, SerializationContext serializers)
      throws JacksonException {
    generator.writeString(score.toString());
  }
}
