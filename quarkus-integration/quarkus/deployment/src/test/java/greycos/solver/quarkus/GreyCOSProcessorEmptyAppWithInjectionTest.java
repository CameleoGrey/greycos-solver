package greycos.solver.quarkus;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.List;

import jakarta.enterprise.util.TypeLiteral;

import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverManager;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;

class GreyCOSProcessorEmptyAppWithInjectionTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest()
          .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses())
          .overrideConfigKey(
              "quarkus.arc.unremovable-types",
              "greycos.solver.core.api.solver.SolverManager,greycos.solver.core.api.solver.SolutionManager");

  @Test
  void emptyAppInjectingSolverManagerCrashes() {
    assertThatIllegalStateException()
        .isThrownBy(() -> Arc.container().instance(SolverManager.class).get())
        .withMessageContaining(
            "The " + SolverManager.class.getName() + " is not available as there are no");
  }

  @Test
  void unavailableFloatingPointSolutionManagersReportMissingDomain() {
    var managerTypes =
        List.<TypeLiteral<?>>of(
            new TypeLiteral<SolutionManager<Object, SimpleFloatScore>>() {},
            new TypeLiteral<SolutionManager<Object, SimpleDoubleScore>>() {},
            new TypeLiteral<SolutionManager<Object, HardSoftFloatScore>>() {},
            new TypeLiteral<SolutionManager<Object, HardSoftDoubleScore>>() {},
            new TypeLiteral<SolutionManager<Object, HardMediumSoftFloatScore>>() {},
            new TypeLiteral<SolutionManager<Object, HardMediumSoftDoubleScore>>() {},
            new TypeLiteral<SolutionManager<Object, BendableFloatScore>>() {},
            new TypeLiteral<SolutionManager<Object, BendableDoubleScore>>() {});
    for (var managerType : managerTypes) {
      assertThatIllegalStateException()
          .isThrownBy(() -> Arc.container().instance(managerType).get())
          .withMessageContaining(
              "The " + SolutionManager.class.getName() + " is not available as there are no");
    }
  }
}
