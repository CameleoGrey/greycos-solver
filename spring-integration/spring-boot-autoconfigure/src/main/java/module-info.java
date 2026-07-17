module ai.greycos.solver.spring.boot.autoconfigure {
  exports ai.greycos.solver.spring.boot.autoconfigure;
  exports ai.greycos.solver.spring.boot.autoconfigure.config;
  exports ai.greycos.solver.spring.boot.autoconfigure.util;

  opens ai.greycos.solver.spring.boot.autoconfigure;

  requires static ai.greycos.solver.benchmark;
  requires transitive ai.greycos.solver.core;
  requires transitive ai.greycos.solver.jackson;
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
