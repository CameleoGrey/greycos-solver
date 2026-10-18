package greycos.solver.spring.boot.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.spring.boot.it.cotwin.IntegrationTestEntity;
import greycos.solver.spring.boot.it.cotwin.IntegrationTestSolution;
import greycos.solver.spring.boot.it.cotwin.IntegrationTestValue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GreyCOSSolverTestResourceIntegrationTest {

  @LocalServerPort String port;

  @Value("classpath:solver-full.xml")
  Resource exampleSolverConfigXml;

  @Test
  void testSolve() {
    WebTestClient client =
        WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port + "/integration-test")
            .build();

    var firstEntity = new IntegrationTestEntity("0");
    firstEntity.setValueList(List.of(new IntegrationTestValue("1"), new IntegrationTestValue("2")));
    var secondEntity = new IntegrationTestEntity("1");
    secondEntity.setValueList(
        List.of(new IntegrationTestValue("1"), new IntegrationTestValue("2")));
    var thirdEntity = new IntegrationTestEntity("2");
    thirdEntity.setValueList(List.of(new IntegrationTestValue("1"), new IntegrationTestValue("2")));
    IntegrationTestSolution problem =
        new IntegrationTestSolution(
            List.of(firstEntity, secondEntity, thirdEntity),
            List.of(new IntegrationTestValue("0")));
    client
        .post()
        .bodyValue(problem)
        .exchange()
        .expectBody()
        .jsonPath("score")
        .isEqualTo("0")
        .jsonPath("entityList")
        .isArray()
        .jsonPath("valueList")
        .isArray()
        .jsonPath("entityList[0].id")
        .isEqualTo("0")
        .jsonPath("entityList[0].value.id")
        .isEqualTo("0")
        .jsonPath("entityList[1].id")
        .isEqualTo("1")
        .jsonPath("entityList[1].value.id")
        .isEqualTo("1")
        .jsonPath("entityList[2].id")
        .isEqualTo("2")
        .jsonPath("entityList[2].value.id")
        .isEqualTo("2");
  }

  @Test
  void testSolveMultipleLists() {
    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port + "/integration-test/multiple-lists")
        .build()
        .post()
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("0;lists=3/4;pins=1/2;shadows=valid;clone=independent");
  }

  @Test
  void testIndictments() {
    var value = new IntegrationTestValue("value");
    var entity = new IntegrationTestEntity("entity");
    entity.setValue(value);
    entity.setValueList(List.of(value));
    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port + "/integration-test/indictments")
        .build()
        .post()
        .bodyValue(new IntegrationTestSolution(List.of(entity), List.of(value)))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("score")
        .isEqualTo("-1")
        .jsonPath("indictments.length()")
        .isEqualTo(1)
        .jsonPath("indictments[0].indictedObject.id")
        .isEqualTo("entity")
        .jsonPath("indictments[0].score")
        .isEqualTo("-1")
        .jsonPath("indictments[0].matchCount")
        .isEqualTo(1)
        .jsonPath("indictments[0].matches[0].matchCount")
        .isEqualTo(1)
        .jsonPath("originalIndictments.length()")
        .isEqualTo(1)
        .jsonPath("originalIndictments[0].indictedObject.id")
        .isEqualTo("entity")
        .jsonPath("originalIndictments[0].score")
        .isEqualTo("-1")
        .jsonPath("originalIndictments[0].matchCount")
        .isEqualTo(1);
  }

  @Test
  void testEmptyIndictments() {
    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port + "/integration-test/indictments")
        .build()
        .post()
        .bodyValue(new IntegrationTestSolution(List.of(), List.of()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("score")
        .isEqualTo("0")
        .jsonPath("indictments")
        .isArray()
        .jsonPath("indictments.length()")
        .isEqualTo(0)
        .jsonPath("originalIndictments")
        .isArray()
        .jsonPath("originalIndictments.length()")
        .isEqualTo(0);
  }

  @Test
  void testRawAnalysisReferences() {
    var value = new IntegrationTestValue("value");
    var entity = new IntegrationTestEntity("entity");
    entity.setValue(value);
    entity.setValueList(List.of(value));
    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port + "/integration-test/raw-analysis")
        .build()
        .post()
        .bodyValue(new IntegrationTestSolution(List.of(entity), List.of(value)))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("score")
        .isEqualTo("-1")
        .jsonPath("constraints[0].rawMatchCount")
        .isEqualTo(1)
        .jsonPath("rawAnalysis.originalsFetched")
        .isEqualTo(true)
        .jsonPath("rawAnalysis.objects.length()")
        .isEqualTo(1)
        .jsonPath("rawAnalysis.objects[0].id")
        .isEqualTo("entity")
        .jsonPath("rawAnalysis.matches.length()")
        .isEqualTo(1)
        .jsonPath("rawAnalysis.matches[0].matchScore")
        .isEqualTo("-1")
        .jsonPath("rawAnalysis.matches[0].occurrenceSign")
        .isEqualTo(1)
        .jsonPath("rawAnalysis.matches[0].indictedObjectIndexes[0]")
        .isEqualTo(0)
        .jsonPath("rawAnalysis.matches[0].originalIndictedObjectIndexes[0]")
        .isEqualTo(0)
        .jsonPath("rawAnalysis.matches[0].justification.type")
        .isEqualTo("default")
        .jsonPath("rawAnalysis.matches[0].justification.impact")
        .isEqualTo("-1")
        .jsonPath("rawAnalysis.matches[0].justification.factIndexes[0]")
        .isEqualTo(0)
        .jsonPath("indictments[0].objectIndex")
        .isEqualTo(0)
        .jsonPath("indictments[0].indictedObject")
        .doesNotExist()
        .jsonPath("originalIndictments[0].objectIndex")
        .isEqualTo(0);
  }

  @Test
  void testEmptyRawAnalysis() {
    WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port + "/integration-test/raw-analysis")
        .build()
        .post()
        .bodyValue(new IntegrationTestSolution(List.of(), List.of()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("rawAnalysis.originalsFetched")
        .isEqualTo(true)
        .jsonPath("rawAnalysis.objects")
        .isArray()
        .jsonPath("rawAnalysis.objects.length()")
        .isEqualTo(0)
        .jsonPath("rawAnalysis.matches")
        .isArray()
        .jsonPath("rawAnalysis.matches.length()")
        .isEqualTo(0);
  }

  @Test
  void testSolverXmlParsing() throws IOException {
    // Test to verify parsing a complex SolverConfig will work in native image.
    // XML file was generated by taking the XSD file available at
    // the solver XSD and generating an XML file from it using
    // the "Tools > XML Actions > Generate XML Document from XML Schema..." action in IDEA
    SolverConfigIO solverConfigIO = new SolverConfigIO();
    SolverConfig solverConfig =
        solverConfigIO.read(new InputStreamReader(exampleSolverConfigXml.getInputStream()));
    assertThat(solverConfig).isNotNull();
    assertThat(solverConfig.getSolutionClass()).isEqualTo(Object.class);
    assertThat(solverConfig.getPhaseConfigList()).isNotEmpty();
  }
}
