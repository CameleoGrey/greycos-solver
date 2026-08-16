module greycos.solver.jackson {
  exports greycos.solver.jackson.api;
  exports greycos.solver.jackson.api.cotwin.solution;
  exports greycos.solver.jackson.api.score.analysis;
  exports greycos.solver.jackson.api.solver;

  provides tools.jackson.databind.JacksonModule with
      greycos.solver.jackson.api.GreyCOSJacksonModule;

  requires transitive greycos.solver.core;
  requires org.jspecify;
  requires tools.jackson.databind;

  uses tools.jackson.databind.JacksonModule;
}
