package ai.greycos.solver.spring.boot.autoconfigure.inheritance.entity;

import ai.greycos.solver.core.testconstraint.DummyConstraintProvider;
import ai.greycos.solver.core.testdomain.inheritance.entity.single.baseannotated.classes.replacevar.TestdataReplaceVarSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(
    basePackageClasses = {TestdataReplaceVarSolution.class, DummyConstraintProvider.class})
public class ReplaceVarSpringTestConfiguration {}
