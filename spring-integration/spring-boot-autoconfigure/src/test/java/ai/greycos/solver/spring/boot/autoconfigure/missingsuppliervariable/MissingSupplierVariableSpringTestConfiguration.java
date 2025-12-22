package ai.greycos.solver.spring.boot.autoconfigure.missingsuppliervariable;

import ai.greycos.solver.spring.boot.autoconfigure.missingsuppliervariable.constraints.TestdataSpringMissingSupplierVariableConstraintProvider;
import ai.greycos.solver.spring.boot.autoconfigure.missingsuppliervariable.domain.TestdataSpringMissingSupplierVariableSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan(
    basePackageClasses = {
      TestdataSpringMissingSupplierVariableSolution.class,
      TestdataSpringMissingSupplierVariableConstraintProvider.class
    })
@AutoConfigurationPackage
public class MissingSupplierVariableSpringTestConfiguration {}
