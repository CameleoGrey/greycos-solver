package greycos.solver.quarkus.jackson.score.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.testdata.profile.ConstraintProfileTestData;
import greycos.solver.quarkus.jackson.GreyCOSJacksonModule;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

class ConstraintProfileJacksonSerializerTest {

  @Test
  void emptyProfileSchema() throws Exception {
    assertCanonicalSchema("empty", ConstraintProfileTestData.empty());
  }

  @Test
  void populatedProfileSchema() throws Exception {
    assertCanonicalSchema("populated", ConstraintProfileTestData.populated());
  }

  @Test
  void failedPartialProfileSchema() throws Exception {
    assertCanonicalSchema("failed-partial", ConstraintProfileTestData.failedPartial());
  }

  @Test
  void zeroDurationProfileSchema() throws Exception {
    assertCanonicalSchema("zero-duration", ConstraintProfileTestData.zeroDuration());
  }

  private static void assertCanonicalSchema(String name, ConstraintProfile profile)
      throws Exception {
    for (var inclusion :
        new JsonInclude.Include[] {
          JsonInclude.Include.ALWAYS, JsonInclude.Include.NON_NULL,
          JsonInclude.Include.NON_EMPTY, JsonInclude.Include.NON_DEFAULT
        }) {
      var mapper =
          JsonMapper.builder()
              .addModule(GreyCOSJacksonModule.createModule())
              .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
              .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
              .defaultPropertyInclusion(JsonInclude.Value.construct(inclusion, inclusion))
              .build();
      var expected = mapper.readTree(ConstraintProfileTestData.expectedJson(name));
      var serialized = mapper.writeValueAsString(profile);
      assertThat(mapper.readTree(serialized)).as("%s with %s", name, inclusion).isEqualTo(expected);
      // Also freeze field and array ordering against the same fixture in both Jackson adapters.
      assertThat(serialized).isEqualTo(mapper.writeValueAsString(expected));
      assertThat(mapper.writeValueAsString(profile)).isEqualTo(serialized);
    }
  }

  @Test
  void constraintIdsIgnoreCustomConstraintRefSerializer() throws Exception {
    var overrideModule = new SimpleModule();
    overrideModule.addSerializer(
        ConstraintRef.class,
        new JsonSerializer<>() {
          @Override
          public void serialize(
              ConstraintRef value, JsonGenerator gen, SerializerProvider serializers)
              throws IOException {
            gen.writeString("custom representation");
          }
        });
    var mapper =
        JsonMapper.builder()
            .addModule(GreyCOSJacksonModule.createModule())
            .addModule(overrideModule)
            .build();
    var profile = ConstraintProfileTestData.populated();
    assertThat(mapper.readTree(mapper.writeValueAsString(profile)))
        .isEqualTo(mapper.readTree(ConstraintProfileTestData.expectedJson("populated")));
  }
}
