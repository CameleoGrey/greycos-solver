package ai.greycos.solver.spring.boot.autoconfigure.inheritance.solution;

import ai.greycos.solver.core.testconstraint.DummyConstraintProvider;
import ai.greycos.solver.core.testdomain.inheritance.solution.baseanot.TestdataOnlyChildAnnotatedExtendedSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage
@EntityScan(
    basePackageClasses = {
      TestdataOnlyChildAnnotatedExtendedSolution.class,
      DummyConstraintProvider.class
    })
public class OnlyChildAnnotatedSpringTestConfiguration {}
