module ai.greycos.solver.jackson {
  exports ai.greycos.solver.jackson.api;
  exports ai.greycos.solver.jackson.api.cotwin.solution;
  exports ai.greycos.solver.jackson.api.score.analysis;
  exports ai.greycos.solver.jackson.api.solver;

  provides tools.jackson.databind.JacksonModule with
      ai.greycos.solver.jackson.api.GreyCOSJacksonModule;

  requires transitive ai.greycos.solver.core;
  requires org.jspecify;
  requires tools.jackson.databind;

  uses tools.jackson.databind.JacksonModule;
}
