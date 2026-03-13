module ai.greycos.solver.core {

  // Public APIs
  exports ai.greycos.solver.core.api.cotwin.common;
  exports ai.greycos.solver.core.api.cotwin.entity;
  exports ai.greycos.solver.core.api.cotwin.lookup;
  exports ai.greycos.solver.core.api.cotwin.solution;
  exports ai.greycos.solver.core.api.cotwin.solution.cloner;
  exports ai.greycos.solver.core.api.cotwin.solution.diff;
  exports ai.greycos.solver.core.api.cotwin.valuerange;
  exports ai.greycos.solver.core.api.cotwin.variable;
  exports ai.greycos.solver.core.api.function;
  exports ai.greycos.solver.core.api.score;
  exports ai.greycos.solver.core.api.score.analysis;
  exports ai.greycos.solver.core.api.score.calculator;
  exports ai.greycos.solver.core.api.score.constraint;
  exports ai.greycos.solver.core.api.score.stream;
  exports ai.greycos.solver.core.api.score.stream.bi;
  exports ai.greycos.solver.core.api.score.stream.common;
  exports ai.greycos.solver.core.api.score.stream.penta;
  exports ai.greycos.solver.core.api.score.stream.quad;
  exports ai.greycos.solver.core.api.score.stream.test;
  exports ai.greycos.solver.core.api.score.stream.tri;
  exports ai.greycos.solver.core.api.score.stream.uni;
  exports ai.greycos.solver.core.api.solver;
  exports ai.greycos.solver.core.api.solver.change;
  exports ai.greycos.solver.core.api.solver.event;
  exports ai.greycos.solver.core.api.solver.phase;

  // Config APIs
  exports ai.greycos.solver.core.config;
  exports ai.greycos.solver.core.config.constructionheuristic;
  exports ai.greycos.solver.core.config.constructionheuristic.decider.forager;
  exports ai.greycos.solver.core.config.constructionheuristic.placer;
  exports ai.greycos.solver.core.config.exhaustivesearch;
  exports ai.greycos.solver.core.config.heuristic.selector.common;
  exports ai.greycos.solver.core.config.heuristic.selector;
  exports ai.greycos.solver.core.config.heuristic.selector.common.decorator;
  exports ai.greycos.solver.core.config.heuristic.selector.common.nearby;
  exports ai.greycos.solver.core.config.heuristic.selector.entity;
  exports ai.greycos.solver.core.config.heuristic.selector.entity.pillar;
  exports ai.greycos.solver.core.config.heuristic.selector.list;
  exports ai.greycos.solver.core.config.heuristic.selector.move;
  exports ai.greycos.solver.core.config.heuristic.selector.move.composite;
  exports ai.greycos.solver.core.config.heuristic.selector.move.factory;
  exports ai.greycos.solver.core.config.heuristic.selector.move.generic;
  exports ai.greycos.solver.core.config.heuristic.selector.move.generic.list;
  exports ai.greycos.solver.core.config.heuristic.selector.move.generic.list.kopt;
  exports ai.greycos.solver.core.config.heuristic.selector.value;
  exports ai.greycos.solver.core.config.islandmodel;
  exports ai.greycos.solver.core.config.localsearch;
  exports ai.greycos.solver.core.config.localsearch.decider.acceptor;
  exports ai.greycos.solver.core.config.localsearch.decider.acceptor.stepcountinghillclimbing;
  exports ai.greycos.solver.core.config.localsearch.decider.forager;
  exports ai.greycos.solver.core.config.partitionedsearch;
  exports ai.greycos.solver.core.config.phase;
  exports ai.greycos.solver.core.config.phase.custom;
  exports ai.greycos.solver.core.config.score.director;
  exports ai.greycos.solver.core.config.score.trend;
  exports ai.greycos.solver.core.config.solver;
  exports ai.greycos.solver.core.config.solver.monitoring;
  exports ai.greycos.solver.core.config.solver.random;
  exports ai.greycos.solver.core.config.solver.termination;
  exports ai.greycos.solver.core.config.util;

  // Preview APIs
  exports ai.greycos.solver.core.preview.api.cotwin.metamodel;
  exports ai.greycos.solver.core.preview.api.cotwin.solution.diff;
  exports ai.greycos.solver.core.preview.api.move;
  exports ai.greycos.solver.core.preview.api.move.builtin;
  exports ai.greycos.solver.core.preview.api.move.test;
  exports ai.greycos.solver.core.preview.api.neighborhood;
  exports ai.greycos.solver.core.preview.api.neighborhood.stream;
  exports ai.greycos.solver.core.preview.api.neighborhood.stream.enumerating;
  exports ai.greycos.solver.core.preview.api.neighborhood.stream.enumerating.function;
  exports ai.greycos.solver.core.preview.api.neighborhood.stream.function;
  exports ai.greycos.solver.core.preview.api.neighborhood.stream.joiner;
  exports ai.greycos.solver.core.preview.api.neighborhood.stream.sampling;
  exports ai.greycos.solver.core.preview.api.neighborhood.test;

  // Shared implementation packages used by other public modules.
  exports ai.greycos.solver.core.impl.cotwin.common;
  exports ai.greycos.solver.core.impl.cotwin.common.accessor;
  exports ai.greycos.solver.core.impl.cotwin.common.accessor.gizmo;
  exports ai.greycos.solver.core.impl.cotwin.entity.descriptor;
  exports ai.greycos.solver.core.impl.cotwin.solution;
  exports ai.greycos.solver.core.impl.cotwin.solution.cloner.gizmo;
  exports ai.greycos.solver.core.impl.cotwin.solution.descriptor;
  exports ai.greycos.solver.core.impl.cotwin.variable.declarative;
  exports ai.greycos.solver.core.impl.cotwin.variable.descriptor;
  exports ai.greycos.solver.core.impl.heuristic.move;
  exports ai.greycos.solver.core.impl.heuristic.selector.common.nearby;
  exports ai.greycos.solver.core.impl.io.jaxb;
  exports ai.greycos.solver.core.impl.localsearch.scope;
  exports ai.greycos.solver.core.impl.phase.event;
  exports ai.greycos.solver.core.impl.phase.scope;
  exports ai.greycos.solver.core.impl.score.constraint;
  exports ai.greycos.solver.core.impl.score.definition;
  exports ai.greycos.solver.core.impl.score.director;
  exports ai.greycos.solver.core.impl.score.stream.common;
  exports ai.greycos.solver.core.impl.score.stream.test;
  exports ai.greycos.solver.core.impl.solver;
  exports ai.greycos.solver.core.impl.solver.monitoring;
  exports ai.greycos.solver.core.impl.solver.scope;
  exports ai.greycos.solver.core.impl.solver.termination;
  exports ai.greycos.solver.core.impl.solver.thread;
  exports ai.greycos.solver.core.impl.util;

  // Open JAXB-serialized types to JAXB.
  opens ai.greycos.solver.core.impl.io.jaxb;
  opens ai.greycos.solver.core.config to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.constructionheuristic to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.constructionheuristic.decider.forager to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.constructionheuristic.placer to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.exhaustivesearch to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.common to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.common.decorator to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.common.nearby to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.entity to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.entity.pillar to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.list to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.move to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.move.composite to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.move.factory to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.move.generic to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.move.generic.list to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.move.generic.list.kopt to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.heuristic.selector.value to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.islandmodel to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.localsearch to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.localsearch.decider.acceptor to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.localsearch.decider.acceptor.stepcountinghillclimbing to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.localsearch.decider.forager to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.partitionedsearch to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.phase to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.phase.custom to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.score.director to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.score.trend to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.solver to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.solver.monitoring to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.solver.random to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.solver.termination to
      jakarta.xml.bind,
      org.glassfish.jaxb.runtime;
  opens ai.greycos.solver.core.config.util to
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
