package greycos.solver.spring.boot.autoconfigure.inheritance.entity;

import greycos.solver.core.testconstraint.DummyConstraintProvider;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.interfaces.replacevar.TestdataReplaceVarInterfaceSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(
    basePackageClasses = {TestdataReplaceVarInterfaceSolution.class, DummyConstraintProvider.class})
public class ReplaceVarInterfaceSpringTestConfiguration {}
