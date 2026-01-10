package ai.greycos.solver.quarkus.benchmark.it;

import org.junit.jupiter.api.Disabled;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/** Test various GreyCOS operations running in native mode */
@QuarkusIntegrationTest
@Disabled("greycos-solver-quarkus-benchmark cannot compile to native")
public class GreyCOSBenchmarkResourceIT extends GreyCOSBenchmarkResourceTest {}
