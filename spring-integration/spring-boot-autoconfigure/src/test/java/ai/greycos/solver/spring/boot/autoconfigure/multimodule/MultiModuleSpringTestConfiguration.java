package ai.greycos.solver.spring.boot.autoconfigure.multimodule;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage
@EntityScan(basePackages = "ai.greycos.solver.spring.boot.autoconfigure.normal")
public class MultiModuleSpringTestConfiguration {}
