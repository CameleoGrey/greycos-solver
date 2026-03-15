package ai.greycos.solver.spring.boot.autoconfigure.dummy;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(
    basePackages = {
      "ai.greycos.solver.spring.boot.autoconfigure.normal.cotwin",
      "ai.greycos.solver.spring.boot.autoconfigure.normal.constraints",
      "ai.greycos.solver.spring.boot.autoconfigure.gizmo.constraints"
    })
public class MultipleConstraintProviderSpringTestConfiguration {}
