module greycos.solver.jpa {
  exports greycos.solver.jpa.api.score;

  requires transitive greycos.solver.core;
  requires jakarta.persistence;
}
