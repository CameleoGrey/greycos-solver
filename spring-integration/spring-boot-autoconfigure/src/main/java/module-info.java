module ai.greycos.solver.spring.boot.autoconfigure {
  exports ai.greycos.solver.spring.boot.autoconfigure;
  exports ai.greycos.solver.spring.boot.autoconfigure.config;
  exports ai.greycos.solver.spring.boot.autoconfigure.util;

  opens ai.greycos.solver.spring.boot.autoconfigure;

  requires static ai.greycos.solver.benchmark;
  requires transitive ai.greycos.solver.core;
  requires transitive ai.greycos.solver.jackson;
  requires com.fasterxml.jackson.databind;
  requires org.jspecify;
  requires spring.beans;
  requires spring.boot;
  requires spring.boot.autoconfigure;
  requires spring.context;
  requires spring.core;
  requires spring.jcl;
  requires spring.web;
}
