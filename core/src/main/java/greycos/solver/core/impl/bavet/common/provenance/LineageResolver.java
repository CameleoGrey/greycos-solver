package greycos.solver.core.impl.bavet.common.provenance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NullMarked;

/**
 * Materializes a settled lineage graph for one analysis snapshot. Never retain a resolver across
 * updates: handles are live, while the memoized results belong to this extraction only.
 *
 * <p>Only explicitly requested roots are cached. Each uncached root visits its reachable handles
 * and source references once; flattening every intermediate union would retain quadratic source
 * lists even when the requested result and its shared graph are linear in size. Distinct roots
 * traverse their shared ancestry independently.
 */
@NullMarked
public final class LineageResolver {

  private final Map<LineageHandle, List<Object>> resolvedRoots = new IdentityHashMap<>();

  public List<Object> resolve(LineageHandle handle) {
    var cached = resolvedRoots.get(handle);
    if (cached != null) {
      return cached;
    }
    var stack = new ArrayDeque<Frame>();
    var states = new IdentityHashMap<LineageHandle, VisitState>();
    Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    var sources = new ArrayList<Object>();
    appendSources(handle, seen, sources);
    stack.addLast(new Frame(handle));
    states.put(handle, VisitState.VISITING);
    while (!stack.isEmpty()) {
      var frame = stack.peekLast();
      if (frame.parents.hasNext()) {
        var parent = frame.parents.next();
        var parentState = states.get(parent);
        if (parentState == VisitState.VISITING) {
          throw new IllegalStateException("The original-source lineage graph contains a cycle.");
        }
        if (parentState == null) {
          // Do not consume cached ancestor lists: overlapping roots could repeat large scans.
          appendSources(parent, seen, sources);
          stack.addLast(new Frame(parent));
          states.put(parent, VisitState.VISITING);
        }
      } else {
        states.put(frame.handle, VisitState.COMPLETE);
        stack.removeLast();
      }
    }
    var result = List.copyOf(sources);
    resolvedRoots.put(handle, result);
    return result;
  }

  private static void appendSources(LineageHandle handle, Set<Object> seen, List<Object> sources) {
    for (var source : handle.sources()) {
      if (seen.add(source)) {
        sources.add(source);
      }
    }
  }

  private enum VisitState {
    VISITING,
    COMPLETE
  }

  private static final class Frame {
    private final LineageHandle handle;
    private final Iterator<LineageHandle> parents;

    private Frame(LineageHandle handle) {
      this.handle = handle;
      this.parents = handle.parents().iterator();
    }
  }
}
