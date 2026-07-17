package ai.greycos.solver.spring.boot.autoconfigure.normal;

import ai.greycos.solver.spring.boot.autoconfigure.normal.cotwin.TestdataSpringEntity;
import ai.greycos.solver.spring.boot.autoconfigure.normal.cotwin.TestdataSpringSolution;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EntityScan(basePackageClasses = {TestdataSpringEntity.class, TestdataSpringSolution.class})
public class NoConstraintsSpringTestConfiguration {}
