package greycos.solver.jackson.api.score.profile;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.testdata.profile.ConstraintProfileTestData;
import greycos.solver.jackson.api.GreyCOSJacksonModule;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

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
              .changeDefaultPropertyInclusion(
                  ignored -> JsonInclude.Value.construct(inclusion, inclusion))
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
        new ValueSerializer<>() {
          @Override
          public void serialize(
              ConstraintRef value, JsonGenerator gen, SerializationContext serializers)
              throws JacksonException {
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
