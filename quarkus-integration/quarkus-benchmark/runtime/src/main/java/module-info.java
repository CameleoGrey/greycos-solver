module ai.greycos.solver.quarkus.benchmark {
  exports ai.greycos.solver.benchmark.quarkus;
  exports ai.greycos.solver.benchmark.quarkus.config;

  requires transitive ai.greycos.solver.benchmark;
  requires transitive ai.greycos.solver.quarkus;
  requires arc;
  requires io.smallrye.config;
  requires jakarta.cdi;
  requires jakarta.inject;
  requires org.eclipse.microprofile.config;
  requires quarkus.core;
}
