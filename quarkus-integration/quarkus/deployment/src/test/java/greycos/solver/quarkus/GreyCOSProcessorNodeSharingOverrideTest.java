package greycos.solver.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import jakarta.inject.Inject;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.quarkus.testcotwin.nodesharing.TestdataQuarkusNodeSharingConstraintProvider;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusEntity;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

public class GreyCOSProcessorNodeSharingOverrideTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .overrideConfigKey(
              "quarkus.greycos.solver-config-xml",
              "greycos/solver/quarkus/solverConfigWithNodeSharing.xml")
          .overrideConfigKey(
              "quarkus.greycos.solver.constraint-stream-automatic-node-sharing", "false")
          .setArchiveProducer(
              () ->
                  ShrinkWrap.create(JavaArchive.class)
                      .addClasses(
                          TestdataQuarkusEntity.class,
                          TestdataQuarkusSolution.class,
                          TestdataQuarkusNodeSharingConstraintProvider.class)
                      .addAsResource("greycos/solver/quarkus/solverConfigWithNodeSharing.xml"));

  @Inject SolverConfig solverConfig;
  @Inject SolverFactory<TestdataQuarkusSolution> solverFactory;
  @Inject SolutionManager<TestdataQuarkusSolution, SimpleScore> solutionManager;

  @Test
  void propertyOverridesSolverConfigAndScoresWithoutSharing() {
    assertFalse(
        solverConfig.getScoreDirectorFactoryConfig().getConstraintStreamAutomaticNodeSharing());
    assertEquals(
        TestdataQuarkusNodeSharingConstraintProvider.class,
        solverConfig.getScoreDirectorFactoryConfig().getConstraintProviderClass());
    assertNotNull(solverFactory.buildSolver());

    var blocked = new TestdataQuarkusEntity();
    blocked.setValue("blocked");
    var allowed = new TestdataQuarkusEntity();
    allowed.setValue("allowed");
    var problem = new TestdataQuarkusSolution();
    problem.setValueList(List.of("blocked", "allowed"));
    problem.setEntityList(List.of(blocked, allowed));

    TestdataQuarkusNodeSharingConstraintProvider.blockedValue = "blocked";
    TestdataQuarkusNodeSharingConstraintProvider.predicateCalls = 0;
    assertEquals(SimpleScore.of(-3), solutionManager.update(problem));
    assertEquals(4, TestdataQuarkusNodeSharingConstraintProvider.predicateCalls);

    TestdataQuarkusNodeSharingConstraintProvider.blockedValue = "absent";
    TestdataQuarkusNodeSharingConstraintProvider.predicateCalls = 0;
    assertEquals(SimpleScore.ZERO, solutionManager.update(problem));
    assertEquals(4, TestdataQuarkusNodeSharingConstraintProvider.predicateCalls);
  }
}
