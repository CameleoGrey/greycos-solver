package greycos.solver.math.impl.model;

import java.util.List;

import greycos.solver.math.api.MathModel;

import org.jspecify.annotations.NullMarked;

/** Immutable per-model facts; no mutable assignments or scoring state are shared here. */
@NullMarked
public record CompiledModel(MathModel model, List<Row> rows, List<Term> terms) {
  public CompiledModel {
    rows = List.copyOf(rows);
    terms = List.copyOf(terms);
  }
}
