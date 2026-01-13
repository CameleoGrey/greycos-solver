package ai.greycos.solver.spring.boot.autoconfigure.inheritance.solution;

import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot.TestdataOnlyBaseAnnotatedExtendedSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage
@EntityScan(basePackageClasses = {TestdataOnlyBaseAnnotatedExtendedSolution.class})
public class OnlyBaseAnnotatedSpringTestConfiguration {}
