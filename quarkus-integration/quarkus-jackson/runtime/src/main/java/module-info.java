module ai.greycos.solver.quarkus.jackson {
  exports ai.greycos.solver.quarkus.jackson;
  exports ai.greycos.solver.quarkus.jackson.cotwin.solution;
  exports ai.greycos.solver.quarkus.jackson.score.analysis;
  exports ai.greycos.solver.quarkus.jackson.solution;
  exports ai.greycos.solver.quarkus.jackson.solver;

  requires transitive ai.greycos.solver.core;
  requires com.fasterxml.jackson.databind;
  requires org.jspecify;
}
