package greycos.solver.spring.boot.autoconfigure.dummy;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(
    basePackages = {
      "greycos.solver.spring.boot.autoconfigure.normal.cotwin",
      "greycos.solver.spring.boot.autoconfigure.normal.constraints",
      "greycos.solver.spring.boot.autoconfigure.gizmo.constraints"
    })
public class MultipleConstraintProviderSpringTestConfiguration {}
