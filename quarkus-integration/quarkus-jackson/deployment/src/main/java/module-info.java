module greycos.solver.quarkus.jackson.deployment {
  exports greycos.solver.quarkus.jackson.deployment;

  requires transitive greycos.solver.quarkus.jackson;
  requires io.quarkus.core;
  requires quarkus.core.deployment;
  requires quarkus.jackson.spi;
}
