package ai.greycos.solver.spring.boot.autoconfigure.inheritance.entity;

import ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.interfaces.childnot.TestdataChildNotAnnotatedInterfaceSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(basePackageClasses = {TestdataChildNotAnnotatedInterfaceSolution.class})
public class OnlyBaseAnnotatedInterfaceSpringTestConfiguration {}
