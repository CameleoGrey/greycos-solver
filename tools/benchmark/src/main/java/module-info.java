module greycos.solver.benchmark {
  requires transitive greycos.solver.core;
  requires greycos.solver.jaxb;
  requires commons.math3;
  requires freemarker;
  requires jakarta.xml.bind;
  requires java.desktop;
  requires java.xml;
  requires micrometer.core;
  requires org.jspecify;
  requires org.slf4j;

  exports greycos.solver.benchmark.api;
  exports greycos.solver.benchmark.config;
  exports greycos.solver.benchmark.config.blueprint;
  exports greycos.solver.benchmark.config.ranking;
  exports greycos.solver.benchmark.config.report;
  exports greycos.solver.benchmark.config.statistic;
  exports greycos.solver.benchmark.impl.result;
  exports greycos.solver.benchmark.impl.report to
      greycos.solver.benchmark.aggregator;
  exports greycos.solver.benchmark.impl.statistic.common to
      greycos.solver.benchmark.aggregator;
  exports greycos.solver.benchmark.impl to
      greycos.solver.quarkus.benchmark.integration.test;

  opens greycos.solver.benchmark.config to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.benchmark.config.blueprint to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.benchmark.config.ranking to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.benchmark.config.report to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.benchmark.config.statistic to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.benchmark.impl.result to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
}
