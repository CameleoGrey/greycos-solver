module greycos.solver.spring.boot.autoconfigure {
  exports greycos.solver.spring.boot.autoconfigure;
  exports greycos.solver.spring.boot.autoconfigure.config;
  exports greycos.solver.spring.boot.autoconfigure.util;

  opens greycos.solver.spring.boot.autoconfigure;

  requires static greycos.solver.benchmark;
  requires transitive greycos.solver.core;
  requires transitive greycos.solver.jackson;
  requires org.apache.commons.logging;
  requires org.jspecify;
  requires spring.beans;
  requires spring.boot;
  requires spring.boot.autoconfigure;
  requires spring.boot.persistence;
  requires spring.context;
  requires spring.core;
  requires tools.jackson.databind;
}
