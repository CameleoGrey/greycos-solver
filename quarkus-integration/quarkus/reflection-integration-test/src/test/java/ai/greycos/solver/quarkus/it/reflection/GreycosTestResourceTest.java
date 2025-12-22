package ai.greycos.solver.quarkus.it.reflection;

import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

/** Test various Greycos operations running in Quarkus */
@QuarkusTest
class GreycosTestResourceTest {

  @Test
  @Timeout(600)
  void solveWithSolverFactory() throws Exception {
    RestAssured.given()
        .header("Content-Type", "application/json")
        .when()
        .post("/greycos/test/solver-factory")
        .then()
        .body(is("0hard/6soft"));
  }
}
