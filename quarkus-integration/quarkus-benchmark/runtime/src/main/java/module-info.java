module greycos.solver.quarkus.benchmark {
  exports greycos.solver.benchmark.quarkus;
  exports greycos.solver.benchmark.quarkus.config;

  requires transitive greycos.solver.benchmark;
  requires transitive greycos.solver.quarkus;
  requires io.quarkus.arc;
  requires io.quarkus.core;
  requires io.smallrye.config;
  requires jakarta.cdi;
  requires jakarta.inject;
  requires org.eclipse.microprofile.config;
}
