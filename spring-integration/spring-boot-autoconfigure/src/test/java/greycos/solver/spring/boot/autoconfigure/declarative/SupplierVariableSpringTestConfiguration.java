package greycos.solver.spring.boot.autoconfigure.declarative;

import greycos.solver.spring.boot.autoconfigure.declarative.constraints.TestdataSpringSupplierVariableConstraintProvider;
import greycos.solver.spring.boot.autoconfigure.declarative.cotwin.TestdataSpringSupplierVariableSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan(
    basePackageClasses = {
      TestdataSpringSupplierVariableSolution.class,
      TestdataSpringSupplierVariableConstraintProvider.class
    })
@AutoConfigurationPackage
public class SupplierVariableSpringTestConfiguration {}
