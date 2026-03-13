module ai.greycos.solver.benchmark {
  requires transitive ai.greycos.solver.core;
  requires ai.greycos.solver.jaxb;
  requires commons.math3;
  requires freemarker;
  requires jakarta.xml.bind;
  requires java.desktop;
  requires java.xml;
  requires micrometer.core;
  requires org.jspecify;
  requires org.slf4j;

  exports ai.greycos.solver.benchmark.api;
  exports ai.greycos.solver.benchmark.config;
  exports ai.greycos.solver.benchmark.config.blueprint;
  exports ai.greycos.solver.benchmark.config.ranking;
  exports ai.greycos.solver.benchmark.config.report;
  exports ai.greycos.solver.benchmark.config.statistic;
  exports ai.greycos.solver.benchmark.impl.result;
  exports ai.greycos.solver.benchmark.impl.report to
      ai.greycos.solver.benchmark.aggregator;
  exports ai.greycos.solver.benchmark.impl.statistic.common to
      ai.greycos.solver.benchmark.aggregator;
  exports ai.greycos.solver.benchmark.impl to
      ai.greycos.solver.quarkus.benchmark.integration.test;

  opens ai.greycos.solver.benchmark.config to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.benchmark.config.blueprint to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.benchmark.config.ranking to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.benchmark.config.report to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.benchmark.config.statistic to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.benchmark.impl.result to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
}
