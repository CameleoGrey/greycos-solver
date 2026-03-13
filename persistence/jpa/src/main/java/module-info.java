module ai.greycos.solver.jpa {
  exports ai.greycos.solver.jpa.api.score;

  requires transitive ai.greycos.solver.core;
  requires jakarta.persistence;
}
