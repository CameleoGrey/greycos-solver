package ai.greycos.solver.quarkus.jsonb;

import jakarta.inject.Singleton;
import jakarta.json.bind.JsonbConfig;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.jsonb.api.GreyCOSJsonbConfig;

import io.quarkus.jsonb.JsonbConfigCustomizer;

/**
 * GreyCOS doesn't use JSON-B, but it does have optional JSON-B support for {@link Score}, etc.
 *
 * @deprecated Prefer Jackson integration instead.
 */
@Singleton
@Deprecated(forRemoval = true, since = "1.4.0")
public class GreyCOSJsonbConfigCustomizer implements JsonbConfigCustomizer {

  @Override
  public void customize(JsonbConfig config) {
    config.withAdapters(GreyCOSJsonbConfig.getScoreJsonbAdapters());
  }
}
