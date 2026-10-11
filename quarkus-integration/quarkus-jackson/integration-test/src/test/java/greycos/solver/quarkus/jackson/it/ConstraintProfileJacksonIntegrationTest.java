package greycos.solver.quarkus.jackson.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.api.score.profile.ConstraintProfileSection;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ConstraintProfileJacksonIntegrationTest {

  @Inject ObjectMapper mapper;

  @Test
  void automaticallyRegisteredModulePreservesProfileSchema() throws Exception {
    var section = new ConstraintProfileSection(0, 0, 0, 0, 0, null, List.of());
    var profile =
        new ConstraintProfile(
            1,
            ConstraintProfile.Outcome.COMPLETED,
            section,
            section,
            section,
            List.of(),
            List.of());
    var configuredMapper =
        mapper
            .copy()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    var json = configuredMapper.readTree(configuredMapper.writeValueAsString(profile));
    assertEquals(1, json.get("schemaVersion").intValue());
    assertFalse(json.has("schema_version"));
    assertEquals("COMPLETED", json.get("outcome").textValue());
    for (var name : List.of("initialization", "search", "combined")) {
      var serializedSection = json.get(name);
      assertTrue(serializedSection.has("scoringWallNanos"));
      assertTrue(serializedSection.get("coverage").isNull());
      assertTrue(serializedSection.get("buckets").isEmpty());
      assertEquals(7, serializedSection.size());
    }
  }
}
