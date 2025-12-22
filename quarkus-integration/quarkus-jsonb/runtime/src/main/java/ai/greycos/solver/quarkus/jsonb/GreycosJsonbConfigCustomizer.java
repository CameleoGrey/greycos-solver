package ai.greycos.solver.quarkus.jsonb;

import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbConfig;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.jsonb.api.GreycosJsonbConfig;

import io.quarkus.jsonb.JsonbConfigCustomizer;

/**
 * Greycos doesn't use JSON-B, but it does have optional JSON-B support for {@link Score}, etc.
 *
 * @deprecated Prefer Jackson integration instead.
 */
@Singleton
@Deprecated(forRemoval = true, since = "1.4.0")
public class GreycosJsonbConfigCustomizer implements JsonbConfigCustomizer {

  @Override
  public void customize(JsonbConfig config) {
    config.withAdapters(GreycosJsonbConfig.getScoreJsonbAdapters());
  }
}
