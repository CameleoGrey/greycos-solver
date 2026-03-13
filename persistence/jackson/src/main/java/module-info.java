module ai.greycos.solver.jackson {
  exports ai.greycos.solver.jackson.api;

  provides com.fasterxml.jackson.databind.Module with
      ai.greycos.solver.jackson.api.GreyCOSJacksonModule;

  requires transitive ai.greycos.solver.core;
  requires com.fasterxml.jackson.databind;
  requires org.jspecify;

  uses com.fasterxml.jackson.databind.Module;
}
