module greycos.solver.quarkus {
  exports greycos.solver.quarkus;
  exports greycos.solver.quarkus.bean;
  exports greycos.solver.quarkus.config;
  exports greycos.solver.quarkus.devui;
  exports greycos.solver.quarkus.gizmo;

  requires transitive greycos.solver.core;
  requires io.quarkus.arc;
  requires io.quarkus.core;
  requires io.smallrye.config;
  requires io.vertx.core;
  requires jakarta.cdi;
  requires jakarta.inject;
  requires org.eclipse.microprofile.config;
  requires org.graalvm.nativeimage;
  requires org.jboss.logging;
  requires org.jspecify;
}
