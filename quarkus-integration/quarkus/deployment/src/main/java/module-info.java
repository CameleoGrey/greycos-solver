module greycos.solver.quarkus.deployment {
  exports greycos.solver.quarkus.deployment.api to
      greycos.solver.quarkus.benchmark.deployment;

  requires transitive greycos.solver.quarkus;
  requires arc.processor;
  requires io.quarkus.core;
  requires io.quarkus.gizmo;
  requires io.quarkus.gizmo2;
  requires io.smallrye.config;
  requires jakarta.cdi;
  requires org.jboss.jandex;
  requires org.jboss.logging;
  requires org.jspecify;
  requires org.objectweb.asm;
  requires quarkus.arc.deployment;
  requires quarkus.builder;
  requires quarkus.core.deployment;
  requires quarkus.devui.deployment.spi;
}
