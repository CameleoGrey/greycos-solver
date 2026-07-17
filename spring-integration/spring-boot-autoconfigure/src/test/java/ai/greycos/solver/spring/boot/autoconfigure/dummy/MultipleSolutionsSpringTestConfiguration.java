package ai.greycos.solver.spring.boot.autoconfigure.dummy;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan(
    basePackages = {
      "ai.greycos.solver.spring.boot.autoconfigure.normal.cotwin",
      "ai.greycos.solver.spring.boot.autoconfigure.gizmo.cotwin"
    })
public class MultipleSolutionsSpringTestConfiguration {}
