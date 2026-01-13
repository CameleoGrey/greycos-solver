package ai.greycos.solver.spring.boot.autoconfigure.inheritance.entity;

import ai.greycos.solver.core.testcotwin.inheritance.entity.multiple.baseannotated.interfaces.childtoo.TestdataMultipleBothAnnotatedInterfaceSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(
    basePackageClasses = {TestdataMultipleBothAnnotatedInterfaceSolution.class})
public class MultipleBothAnnotatedInterfaceSpringTestConfiguration {}
