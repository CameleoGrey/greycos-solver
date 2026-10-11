package greycos.solver.greybay.impl.sampling;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import greycos.solver.greybay.config.CategoricalDimension;
import greycos.solver.greybay.config.Dimension;
import greycos.solver.greybay.config.FixedDimension;
import greycos.solver.greybay.config.IntegerDimension;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.RealDimension;
import greycos.solver.greybay.config.SearchSpace;

/** Stable traversal and exhaustion proofs restricted to the declared finite assignment limit. */
final class FiniteSpace {
  private static final int LIMIT = 100_000;

  private FiniteSpace() {}

  static List<ParameterAssignment> enumerate(SearchSpace space, String root) {
    var result = new ArrayList<ParameterAssignment>();
    return visit(
            space.dimensions(),
            0,
            new LinkedHashMap<>(),
            root,
            values -> {
              if (result.size() == LIMIT) return false;
              result.add(ParameterAssignment.of(values));
              return true;
            })
        ? List.copyOf(result)
        : null;
  }

  /**
   * Tests assignments in enumeration order, stopping at the first mismatch. The predicate is never
   * called unless the complete selected space is discrete and within the enumeration limit.
   */
  static boolean allMatch(
      SearchSpace space, String root, Predicate<ParameterAssignment> predicate) {
    if (count(space.dimensions(), 0, new LinkedHashMap<>(), root, LIMIT) < 0) return false;
    return visit(
        space.dimensions(),
        0,
        new LinkedHashMap<>(),
        root,
        values -> predicate.test(ParameterAssignment.of(values)));
  }

  /** Counts without constructing assignments; -1 means the finite bound was not established. */
  private static int count(
      List<Dimension> dimensions,
      int position,
      LinkedHashMap<String, Object> categories,
      String root,
      int limit) {
    if (limit == 0) return -1;
    if (position == dimensions.size()) return 1;
    Dimension dimension = dimensions.get(position);
    if (!dimension.activation().isActive(categories) || dimension instanceof FixedDimension)
      return count(dimensions, position + 1, categories, root, limit);
    if (dimension instanceof RealDimension) return -1;
    if (dimension instanceof IntegerDimension integer) {
      long size = integer.gridCount();
      if (size > limit) return -1;
      // Activation depends only on categorical coordinates, so every integer value has the same
      // number of suffix assignments. Divide the remaining bound before multiplying the count.
      int suffix = count(dimensions, position + 1, categories, root, limit / (int) size);
      return suffix < 0 ? -1 : (int) size * suffix;
    }
    var categorical = (CategoricalDimension) dimension;
    List<String> choices =
        root != null && dimension.name().equals("template") ? List.of(root) : categorical.choices();
    int total = 0;
    for (String value : choices) {
      categories.put(dimension.name(), value);
      int branch = count(dimensions, position + 1, categories, root, limit - total);
      if (branch < 0) return -1;
      total += branch;
    }
    categories.remove(dimension.name());
    return total;
  }

  private static boolean visit(
      List<Dimension> dimensions,
      int position,
      LinkedHashMap<String, Object> values,
      String root,
      Predicate<Map<String, Object>> visitor) {
    if (position == dimensions.size()) return visitor.test(values);
    Dimension dimension = dimensions.get(position);
    if (!dimension.activation().isActive(values))
      return visit(dimensions, position + 1, values, root, visitor);
    if (dimension instanceof RealDimension) return false;
    if (root != null && dimension.name().equals("template")) {
      values.put(dimension.name(), root);
      boolean completed = visit(dimensions, position + 1, values, root, visitor);
      values.remove(dimension.name());
      return completed;
    }
    if (dimension instanceof IntegerDimension integer) {
      if (integer.gridCount() > LIMIT) return false;
      for (long index = 0; index < integer.gridCount(); index++) {
        values.put(integer.name(), integer.lower() + index * integer.step());
        if (!visit(dimensions, position + 1, values, root, visitor)) return false;
      }
    } else {
      List<?> possibilities =
          dimension instanceof CategoricalDimension categorical
              ? categorical.choices()
              : List.of(((FixedDimension) dimension).value());
      for (Object value : possibilities) {
        values.put(dimension.name(), value);
        if (!visit(dimensions, position + 1, values, root, visitor)) return false;
      }
    }
    values.remove(dimension.name());
    return true;
  }
}
