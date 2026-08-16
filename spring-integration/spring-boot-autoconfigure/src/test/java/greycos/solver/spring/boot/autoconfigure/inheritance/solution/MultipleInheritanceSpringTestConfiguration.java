package greycos.solver.spring.boot.autoconfigure.inheritance.solution;

import greycos.solver.core.testconstraint.DummyConstraintProvider;
import greycos.solver.core.testcotwin.inheritance.solution.baseannotated.multiple.TestdataMultipleInheritanceExtendedSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage
@EntityScan(
    basePackageClasses = {
      TestdataMultipleInheritanceExtendedSolution.class,
      DummyConstraintProvider.class
    })
public class MultipleInheritanceSpringTestConfiguration {}
