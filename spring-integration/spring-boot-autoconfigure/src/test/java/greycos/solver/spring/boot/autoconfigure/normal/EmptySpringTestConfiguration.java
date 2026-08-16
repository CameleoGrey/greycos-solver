package greycos.solver.spring.boot.autoconfigure.normal;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan(basePackages = "greycos.solver.spring.boot.autoconfigure.empty")
public class EmptySpringTestConfiguration {}
