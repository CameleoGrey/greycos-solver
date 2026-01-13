package ai.greycos.solver.spring.boot.autoconfigure.dummy;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan(
    basePackages = {
      "ai.greycos.solver.spring.boot.autoconfigure.normal.cotwin",
      "ai.greycos.solver.spring.boot.autoconfigure.chained.cotwin"
    })
public class MultipleSolutionsSpringTestConfiguration {}
