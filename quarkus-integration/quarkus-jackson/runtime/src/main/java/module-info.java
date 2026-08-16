module greycos.solver.quarkus.jackson {
  exports greycos.solver.quarkus.jackson;
  exports greycos.solver.quarkus.jackson.cotwin.solution;
  exports greycos.solver.quarkus.jackson.score.analysis;
  exports greycos.solver.quarkus.jackson.solution;
  exports greycos.solver.quarkus.jackson.solver;

  requires transitive greycos.solver.core;
  requires com.fasterxml.jackson.databind;
  requires org.jspecify;
}
