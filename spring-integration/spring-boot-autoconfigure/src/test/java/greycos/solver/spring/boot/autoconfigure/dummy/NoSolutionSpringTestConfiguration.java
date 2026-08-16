package greycos.solver.spring.boot.autoconfigure.dummy;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(
    basePackages = {
      "greycos.solver.spring.boot.autoconfigure.dummy.normal.noSolution",
      "greycos.solver.spring.boot.autoconfigure.dummy.normal.constraints.incremental"
    })
public class NoSolutionSpringTestConfiguration {}
