module ai.greycos.solver.quarkus.jackson.deployment {
  exports ai.greycos.solver.quarkus.jackson.deployment;

  requires transitive ai.greycos.solver.quarkus.jackson;
  requires quarkus.core;
  requires quarkus.core.deployment;
  requires quarkus.jackson.spi;
}
