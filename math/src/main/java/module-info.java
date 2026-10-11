/** Discrete linear models evaluated incrementally by GreyCOS Constraint Streams. */
module greycos.solver.math {
  exports greycos.solver.math.api;
  exports greycos.solver.math.config;

  // Generated member accessors live in a separate class loader.
  exports greycos.solver.math.impl.cotwin;
  exports greycos.solver.math.impl.score to
      greycos.solver.core;
  exports greycos.solver.math.impl.search to
      greycos.solver.core;

  opens greycos.solver.math.impl.cotwin to
      greycos.solver.core;
  opens greycos.solver.math.impl.score to
      greycos.solver.core;
  opens greycos.solver.math.impl.search to
      greycos.solver.core;

  requires transitive greycos.solver.core;
  requires org.jspecify;
}
