package ai.greycos.solver.spring.boot.autoconfigure.dummy;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(
    basePackages = {
      "ai.greycos.solver.spring.boot.autoconfigure.dummy.normal.noSolution",
      "ai.greycos.solver.spring.boot.autoconfigure.dummy.normal.constraints.incremental"
    })
public class NoSolutionSpringTestConfiguration {}
