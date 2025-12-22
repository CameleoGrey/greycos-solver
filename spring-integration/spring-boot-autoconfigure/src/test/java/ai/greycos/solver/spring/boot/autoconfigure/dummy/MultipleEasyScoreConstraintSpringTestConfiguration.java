package ai.greycos.solver.spring.boot.autoconfigure.dummy;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(
    basePackages = {
      "ai.greycos.solver.spring.boot.autoconfigure.normal.domain",
      "ai.greycos.solver.spring.boot.autoconfigure.dummy.normal.constraints.easy",
      "ai.greycos.solver.spring.boot.autoconfigure.dummy.chained.constraints.easy"
    })
public class MultipleEasyScoreConstraintSpringTestConfiguration {}
