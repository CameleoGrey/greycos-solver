package greycos.solver.spring.boot.autoconfigure.nodesharing;

import greycos.solver.spring.boot.autoconfigure.normal.cotwin.TestdataSpringSolution;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(
    basePackageClasses = {
      TestdataSpringSolution.class,
      TestdataSpringNodeSharingConstraintProvider.class
    })
public class NodeSharingSpringTestConfiguration {}
