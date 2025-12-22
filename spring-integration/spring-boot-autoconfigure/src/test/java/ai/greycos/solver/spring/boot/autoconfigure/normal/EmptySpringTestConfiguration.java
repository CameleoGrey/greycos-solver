package ai.greycos.solver.spring.boot.autoconfigure.normal;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan(basePackages = "ai.greycos.solver.spring.boot.autoconfigure.empty")
public class EmptySpringTestConfiguration {}
