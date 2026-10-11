/** Durable, sequential Bayesian solver-configuration studies. */
module greycos.solver.greybay {
  requires transitive greycos.solver.core;
  requires commons.math3;
  requires tools.jackson.databind;
  requires tools.jackson.core;
  requires java.xml;
  requires java.management;
  requires org.slf4j;

  exports greycos.solver.greybay.api;
  exports greycos.solver.greybay.api.objective;
  exports greycos.solver.greybay.api.sampling;
  exports greycos.solver.greybay.api.template;
  exports greycos.solver.greybay.config;
  exports greycos.solver.greybay.config.template;
}
