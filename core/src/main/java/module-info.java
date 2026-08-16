module greycos.solver.core {

  // Public APIs
  exports greycos.solver.core.api.cotwin.common;
  exports greycos.solver.core.api.cotwin.entity;
  exports greycos.solver.core.api.cotwin.lookup;
  exports greycos.solver.core.api.cotwin.solution;
  exports greycos.solver.core.api.cotwin.solution.cloner;
  exports greycos.solver.core.api.cotwin.valuerange;
  exports greycos.solver.core.api.cotwin.variable;
  exports greycos.solver.core.api.function;
  exports greycos.solver.core.api.score;
  exports greycos.solver.core.api.score.analysis;
  exports greycos.solver.core.api.score.calculator;
  exports greycos.solver.core.api.score.stream;
  exports greycos.solver.core.api.score.stream.bi;
  exports greycos.solver.core.api.score.stream.common;
  exports greycos.solver.core.api.score.stream.penta;
  exports greycos.solver.core.api.score.stream.quad;
  exports greycos.solver.core.api.score.stream.test;
  exports greycos.solver.core.api.score.stream.tri;
  exports greycos.solver.core.api.score.stream.uni;
  exports greycos.solver.core.api.solver;
  exports greycos.solver.core.api.solver.change;
  exports greycos.solver.core.api.solver.event;
  exports greycos.solver.core.api.solver.phase;

  // Config APIs
  exports greycos.solver.core.config;
  exports greycos.solver.core.config.constructionheuristic;
  exports greycos.solver.core.config.constructionheuristic.decider.forager;
  exports greycos.solver.core.config.constructionheuristic.placer;
  exports greycos.solver.core.config.exhaustivesearch;
  exports greycos.solver.core.config.heuristic.selector.common;
  exports greycos.solver.core.config.heuristic.selector;
  exports greycos.solver.core.config.heuristic.selector.common.decorator;
  exports greycos.solver.core.config.heuristic.selector.common.nearby;
  exports greycos.solver.core.config.heuristic.selector.entity;
  exports greycos.solver.core.config.heuristic.selector.entity.pillar;
  exports greycos.solver.core.config.heuristic.selector.list;
  exports greycos.solver.core.config.heuristic.selector.move;
  exports greycos.solver.core.config.heuristic.selector.move.composite;
  exports greycos.solver.core.config.heuristic.selector.move.factory;
  exports greycos.solver.core.config.heuristic.selector.move.generic;
  exports greycos.solver.core.config.heuristic.selector.move.generic.list;
  exports greycos.solver.core.config.heuristic.selector.move.generic.list.kopt;
  exports greycos.solver.core.config.heuristic.selector.value;
  exports greycos.solver.core.config.islandmodel;
  exports greycos.solver.core.config.localsearch;
  exports greycos.solver.core.config.localsearch.decider.acceptor;
  exports greycos.solver.core.config.localsearch.decider.acceptor.stepcountinghillclimbing;
  exports greycos.solver.core.config.localsearch.decider.forager;
  exports greycos.solver.core.config.partitionedsearch;
  exports greycos.solver.core.config.phase;
  exports greycos.solver.core.config.phase.custom;
  exports greycos.solver.core.config.score.director;
  exports greycos.solver.core.config.score.trend;
  exports greycos.solver.core.config.solver;
  exports greycos.solver.core.config.solver.monitoring;
  exports greycos.solver.core.config.solver.termination;
  exports greycos.solver.core.config.util;

  // Preview APIs
  exports greycos.solver.core.preview.api.cotwin.metamodel;
  exports greycos.solver.core.preview.api.cotwin.solution.diff;
  exports greycos.solver.core.preview.api.move;
  exports greycos.solver.core.preview.api.move.builtin;
  exports greycos.solver.core.preview.api.move.test;
  exports greycos.solver.core.preview.api.neighborhood;
  exports greycos.solver.core.preview.api.neighborhood.stream;
  exports greycos.solver.core.preview.api.neighborhood.stream.enumerating;
  exports greycos.solver.core.preview.api.neighborhood.stream.enumerating.collector;
  exports greycos.solver.core.preview.api.neighborhood.stream.enumerating.function;
  exports greycos.solver.core.preview.api.neighborhood.stream.function;
  exports greycos.solver.core.preview.api.neighborhood.stream.joiner;
  exports greycos.solver.core.preview.api.neighborhood.stream.sampling;
  exports greycos.solver.core.preview.api.neighborhood.test;

  // Shared implementation packages used by other public modules.
  exports greycos.solver.core.impl.cotwin.common;
  exports greycos.solver.core.impl.cotwin.common.accessor;
  exports greycos.solver.core.impl.cotwin.common.accessor.gizmo;
  exports greycos.solver.core.impl.cotwin.entity.descriptor;
  exports greycos.solver.core.impl.cotwin.solution;
  exports greycos.solver.core.impl.cotwin.solution.cloner.gizmo;
  exports greycos.solver.core.impl.cotwin.solution.descriptor;
  exports greycos.solver.core.impl.cotwin.variable.declarative;
  exports greycos.solver.core.impl.cotwin.variable.descriptor;
  exports greycos.solver.core.impl.heuristic.move;
  exports greycos.solver.core.impl.heuristic.selector.common.nearby;
  exports greycos.solver.core.impl.io.jaxb;
  exports greycos.solver.core.impl.localsearch.scope;
  exports greycos.solver.core.impl.phase.event;
  exports greycos.solver.core.impl.phase.scope;
  exports greycos.solver.core.impl.score.constraint;
  exports greycos.solver.core.impl.score.analysis to
      greycos.solver.jackson,
      greycos.solver.quarkus.jackson;
  exports greycos.solver.core.impl.score.definition;
  exports greycos.solver.core.impl.score.director;
  exports greycos.solver.core.impl.score.stream.common;
  exports greycos.solver.core.impl.score.stream.test;
  exports greycos.solver.core.impl.solver;
  exports greycos.solver.core.impl.solver.monitoring;
  exports greycos.solver.core.impl.solver.scope;
  exports greycos.solver.core.impl.solver.termination;
  exports greycos.solver.core.impl.solver.thread;
  exports greycos.solver.core.impl.util;

  // Open JAXB-serialized types to JAXB.
  opens greycos.solver.core.impl.io.jaxb;
  opens greycos.solver.core.config to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.constructionheuristic to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.constructionheuristic.decider.forager to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.constructionheuristic.placer to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.exhaustivesearch to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.common to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.common.decorator to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.common.nearby to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.entity to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.entity.pillar to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.list to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.move to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.move.composite to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.move.factory to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.move.generic to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.move.generic.list to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.move.generic.list.kopt to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.heuristic.selector.value to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.islandmodel to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.localsearch to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.localsearch.decider.acceptor to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.localsearch.decider.acceptor.stepcountinghillclimbing to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.localsearch.decider.forager to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.partitionedsearch to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.phase to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.phase.custom to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.score.director to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.score.trend to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.solver to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.solver.monitoring to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.solver.termination to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens greycos.solver.core.config.util to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;

  requires commons.math3;
  requires io.quarkus.gizmo2;
  requires jakarta.xml.bind;
  requires java.management;
  requires java.xml;
  requires micrometer.core;
  requires org.jspecify;
  requires org.objectweb.asm;
  requires org.slf4j;
}
