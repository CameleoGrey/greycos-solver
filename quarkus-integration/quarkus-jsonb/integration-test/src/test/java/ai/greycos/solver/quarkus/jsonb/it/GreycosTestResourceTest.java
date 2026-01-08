package ai.greycos.solver.quarkus.jsonb.it;

import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

/** Test various GreyCOS operations running in Quarkus */
@QuarkusTest
class GreyCOSTestResourceTest {

  @Test
  @Timeout(600)
  void solveWithSolverFactory() throws Exception {
    RestAssured.given()
        .header("Content-Type", "application/json")
        .when()
        .body("{\"valueList\":[\"v1\",\"v2\"],\"entityList\":[{},{}]}")
        .post("/greycos/test/solver-factory")
        .then()
        .body(
            is(
                "{\"entityList\":[{\"value\":\"v1\"},{\"value\":\"v2\"}],\"score\":\"0\",\"valueList\":[\"v1\",\"v2\"]}"));
  }
}
