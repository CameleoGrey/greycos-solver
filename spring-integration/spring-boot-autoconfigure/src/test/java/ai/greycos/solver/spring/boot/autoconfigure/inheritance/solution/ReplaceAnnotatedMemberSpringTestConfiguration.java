package ai.greycos.solver.spring.boot.autoconfigure.inheritance.solution;

import ai.greycos.solver.core.testconstraint.DummyConstraintProvider;
import ai.greycos.solver.core.testdomain.inheritance.solution.baseannotated.replacemember.TestdataReplaceMemberExtendedSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage
@EntityScan(
    basePackageClasses = {
      TestdataReplaceMemberExtendedSolution.class,
      DummyConstraintProvider.class
    })
public class ReplaceAnnotatedMemberSpringTestConfiguration {}
