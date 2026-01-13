package ai.greycos.solver.spring.boot.autoconfigure.inheritance.solution;

import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childtooabstract.TestdataBothAnnotatedAbstractExtendedSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage
@EntityScan(basePackageClasses = {TestdataBothAnnotatedAbstractExtendedSolution.class})
public class BothAnnotatedAbstractSpringTestConfiguration {}
