package ai.greycos.solver.spring.boot.autoconfigure.inheritance.entity;

import ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(basePackageClasses = {TestdataAddVarSolution.class})
public class AddVarSpringTestConfiguration {}
