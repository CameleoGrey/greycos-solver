package ai.greycos.solver.spring.boot.autoconfigure.inheritance.entity;

import ai.greycos.solver.core.testdomain.inheritance.entity.multiple.baseannotated.classes.childnot.TestdataMultipleChildNotAnnotatedSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(basePackageClasses = {TestdataMultipleChildNotAnnotatedSolution.class})
public class MultipleOnlyBaseAnnotatedSpringTestConfiguration {}
