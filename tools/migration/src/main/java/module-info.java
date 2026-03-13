module ai.greycos.solver.migration {
  exports ai.greycos.solver.migration;
  exports ai.greycos.solver.migration.common;
  exports ai.greycos.solver.migration.v1;
  exports ai.greycos.solver.migration.v2;

  requires rewrite.core;
  requires rewrite.java;
  requires rewrite.maven;
}
