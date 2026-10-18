package greycos.solver.quarkus.jackson.it;

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
  void solveWithSolverFactory() {
    RestAssured.given()
        .header("Content-Type", "application/json")
        .when()
        .body("{\"valueList\":[\"v1\",\"v2\"],\"entityList\":[{},{}]}")
        .post("/greycos/test/solver-factory")
        .then()
        .body(
            is(
                "{\"valueList\":[\"v1\",\"v2\"],\"entityList\":[{\"value\":\"v1\"},{\"value\":\"v2\"}],\"score\":\"0\"}"));
  }

  @Test
  void analyzeIndictments() {
    RestAssured.given()
        .header("Content-Type", "application/json")
        .body("{\"valueList\":[\"v1\"],\"entityList\":[{\"value\":\"v1\"},{\"value\":\"v1\"}]}")
        .when()
        .post("/greycos/test/indictments")
        .then()
        .statusCode(200)
        .body("score", is("-2"))
        .body("indictments.size()", is(2))
        .body("indictments[0].indictedObject.value", is("v1"))
        .body("indictments[0].score", is("-2"))
        .body("indictments[0].matchCount", is(2))
        .body("indictments[0].matches.size()", is(2))
        .body("indictments[0].matches[0].matchCount", is(1))
        .body("originalIndictments.size()", is(2))
        .body("originalIndictments[0].indictedObject.value", is("v1"))
        .body("originalIndictments[0].score", is("-2"))
        .body("originalIndictments[0].matchCount", is(2));
  }

  @Test
  void analyzeEmptyIndictments() {
    RestAssured.given()
        .header("Content-Type", "application/json")
        .body("{\"valueList\":[],\"entityList\":[]}")
        .when()
        .post("/greycos/test/indictments")
        .then()
        .statusCode(200)
        .body("score", is("0"))
        .body("indictments.size()", is(0))
        .body("originalIndictments.size()", is(0));
  }

  @Test
  void analyzeRawReferences() {
    RestAssured.given()
        .header("Content-Type", "application/json")
        .body("{\"valueList\":[\"v1\"],\"entityList\":[{\"value\":\"v1\"},{\"value\":\"v1\"}]}")
        .when()
        .post("/greycos/test/raw-analysis")
        .then()
        .statusCode(200)
        .body("score", is("-2"))
        .body("constraints[0].rawMatchCount", is(2))
        .body("rawAnalysis.originalsFetched", is(true))
        .body("rawAnalysis.objects.size()", is(2))
        .body("rawAnalysis.objects[0].value", is("v1"))
        .body("rawAnalysis.objects[1].value", is("v1"))
        .body("rawAnalysis.matches.size()", is(2))
        .body("rawAnalysis.matches[0].matchScore", is("-1"))
        .body("rawAnalysis.matches[0].occurrenceSign", is(1))
        .body("rawAnalysis.matches[1].occurrenceSign", is(1))
        .body("rawAnalysis.matches[0].indictedObjectIndexes.size()", is(2))
        .body("rawAnalysis.matches[0].originalIndictedObjectIndexes.size()", is(2))
        .body("rawAnalysis.matches[0].justification.type", is("default"))
        .body("rawAnalysis.matches[0].justification.impact", is("-1"))
        .body("rawAnalysis.matches[0].justification.factIndexes.size()", is(2))
        .body("indictments.size()", is(2))
        .body("indictments[0].containsKey('objectIndex')", is(true))
        .body("indictments[0].containsKey('indictedObject')", is(false))
        .body("originalIndictments[0].containsKey('objectIndex')", is(true));
  }

  @Test
  void analyzeEmptyRaw() {
    RestAssured.given()
        .header("Content-Type", "application/json")
        .body("{\"valueList\":[],\"entityList\":[]}")
        .when()
        .post("/greycos/test/raw-analysis")
        .then()
        .statusCode(200)
        .body("rawAnalysis.originalsFetched", is(true))
        .body("rawAnalysis.objects.size()", is(0))
        .body("rawAnalysis.matches.size()", is(0));
  }
}
