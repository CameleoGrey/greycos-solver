package greycos.solver.core.impl.bavet.common.provenance;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * A stable reference to the original sources of one logical stream occurrence. Parent membership
 * may change without changing the tuple or its score. Identity, rather than source equality,
 * distinguishes these references and the occurrences contributing to them.
 */
@NullMarked
public final class LineageHandle {

  private final List<Object> sources;
  private final Map<LineageHandle, Integer> parents;

  private LineageHandle(List<Object> sources, Map<LineageHandle, Integer> parents) {
    this.sources = sources;
    this.parents = parents;
  }

  public static LineageHandle source(Object source) {
    return new LineageHandle(List.of(Objects.requireNonNull(source)), Collections.emptyMap());
  }

  /** Detaches provenance exported from a temporary precompute network. */
  public static LineageHandle frozen(Collection<?> sources) {
    return new LineageHandle(List.copyOf(sources), Collections.emptyMap());
  }

  public static LineageHandle mutable() {
    return new LineageHandle(List.of(), new LinkedHashMap<>());
  }

  public static LineageHandle union(LineageHandle... parents) {
    var handle = mutable();
    for (var parent : parents) {
      handle.add(parent);
    }
    return handle;
  }

  /** Adds one contributor occurrence; repeated references remain until their last removal. */
  public void add(LineageHandle parent) {
    if (parent == this) {
      throw new IllegalArgumentException("A lineage handle cannot contribute to itself.");
    }
    parents.merge(Objects.requireNonNull(parent), 1, Math::addExact);
  }

  public void remove(LineageHandle parent) {
    var count = parents.get(parent);
    if (count == null) {
      throw new IllegalStateException("The lineage contributor was never added.");
    }
    if (count == 1) {
      parents.remove(parent);
    } else {
      parents.put(parent, count - 1);
    }
  }

  /** Releases only this handle's parent edges; it never changes its shared parents. */
  public void clear() {
    parents.clear();
  }

  List<Object> sources() {
    return sources;
  }

  Collection<LineageHandle> parents() {
    return parents.keySet();
  }
}
