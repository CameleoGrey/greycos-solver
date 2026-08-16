module greycos.solver.migration {
  exports greycos.solver.migration;
  exports greycos.solver.migration.common;
  exports greycos.solver.migration.v1;
  exports greycos.solver.migration.v2;

  requires rewrite.core;
  requires rewrite.java;
  requires rewrite.maven;
  requires rewrite.properties;
}
