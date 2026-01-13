package ai.greycos.solver.spring.boot.autoconfigure.inheritance.entity;

import ai.greycos.solver.core.testconstraint.DummyConstraintProvider;
import ai.greycos.solver.core.testcotwin.inheritance.entity.single.basenot.classes.TestdataBaseNotAnnotatedSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(
    basePackageClasses = {TestdataBaseNotAnnotatedSolution.class, DummyConstraintProvider.class})
public class OnlyChildAnnotatedSpringTestConfiguration {}
