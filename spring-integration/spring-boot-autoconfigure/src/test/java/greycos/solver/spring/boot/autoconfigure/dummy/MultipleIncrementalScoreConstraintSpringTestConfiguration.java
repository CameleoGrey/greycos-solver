package greycos.solver.spring.boot.autoconfigure.dummy;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(
    basePackages = {
      "greycos.solver.spring.boot.autoconfigure.normal.cotwin",
      "greycos.solver.spring.boot.autoconfigure.dummy.normal.constraints.incremental",
      "greycos.solver.spring.boot.autoconfigure.dummy.gizmo.constraints.incremental"
    })
public class MultipleIncrementalScoreConstraintSpringTestConfiguration {}
