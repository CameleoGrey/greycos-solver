package ai.greycos.solver.spring.boot.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ai.greycos.solver.core.api.cotwin.common.CotwinAccessType;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.spring.boot.autoconfigure.config.GreyCOSProperties;
import ai.greycos.solver.spring.boot.autoconfigure.gizmo.GizmoSpringTestConfiguration;
import ai.greycos.solver.spring.boot.autoconfigure.normal.cotwin.TestdataSpringSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.TestExecutionListeners;

@TestExecutionListeners
@Execution(ExecutionMode.CONCURRENT)
class GreyCOSSolverGizmoAutoConfigurationTest {

  private final ApplicationContextRunner gizmoContextRunner;
  private final FilteredClassLoader noGizmoFilteredClassLoader;

  public GreyCOSSolverGizmoAutoConfigurationTest() {
    gizmoContextRunner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    GreyCOSSolverAutoConfiguration.class, GreyCOSSolverBeanFactory.class))
            .withUserConfiguration(GizmoSpringTestConfiguration.class);
    noGizmoFilteredClassLoader =
        new FilteredClassLoader(
            FilteredClassLoader.PackageFilter.of("io.quarkus.gizmo2"),
            FilteredClassLoader.ClassPathResourceFilter.of(
                new ClassPathResource(GreyCOSProperties.DEFAULT_SOLVER_CONFIG_URL)));
  }

  @Test
  void solverProperties() {
    gizmoContextRunner
        .withPropertyValues("greycos.solver.cotwin-access-type=GIZMO")
        .run(
            context -> {
              var solverConfig = context.getBean(SolverConfig.class);
              assertThat(solverConfig.getCotwinAccessType()).isEqualTo(CotwinAccessType.GIZMO);
              assertThat(context.getBean(SolverFactory.class)).isNotNull();
            });
    gizmoContextRunner
        .withPropertyValues("greycos.solver.solver1.cotwin-access-type=GIZMO")
        .withPropertyValues("greycos.solver.solver2.cotwin-access-type=REFLECTION")
        .run(
            context -> {
              var solver1 =
                  (SolverManager<TestdataSpringSolution, Long>) context.getBean("solver1");
              var solver2 =
                  (SolverManager<TestdataSpringSolution, Long>) context.getBean("solver2");
              assertThat(solver1).isNotNull();
              assertThat(solver2).isNotNull();
            });
  }

  @Test
  void gizmoThrowsIfGizmoNotPresent() {
    assertThatCode(
            () ->
                gizmoContextRunner
                    .withClassLoader(noGizmoFilteredClassLoader)
                    .withPropertyValues(
                        "greycos.solver-config-xml=ai/greycos/solver/spring/boot/autoconfigure/gizmoSpringBootSolverConfig.xml")
                    .run(context -> context.getBean(SolverFactory.class)))
        .hasRootCauseMessage(
            "When using the cotwinAccessType ("
                + CotwinAccessType.GIZMO
                + ") the classpath or modulepath must contain io.quarkus.gizmo:gizmo2.\n"
                + "Maybe add a dependency to io.quarkus.gizmo:gizmo2.");
  }
}
