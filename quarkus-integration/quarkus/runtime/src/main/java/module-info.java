module ai.greycos.solver.quarkus {
  exports ai.greycos.solver.quarkus;
  exports ai.greycos.solver.quarkus.bean;
  exports ai.greycos.solver.quarkus.config;
  exports ai.greycos.solver.quarkus.devui;
  exports ai.greycos.solver.quarkus.gizmo;

  requires transitive ai.greycos.solver.core;
  requires arc;
  requires io.smallrye.config;
  requires io.vertx.core;
  requires jakarta.cdi;
  requires jakarta.inject;
  requires org.eclipse.microprofile.config;
  requires org.graalvm.nativeimage;
  requires org.jboss.logging;
  requires org.jspecify;
  requires quarkus.core;
}
