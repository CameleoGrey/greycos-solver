package greycos.solver.core.impl.constructionheuristic.nearby;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.preview.api.move.Move;

/** Preserves original union branch order and opens each branch only when it is reached. */
final class ConcatenatingConstructionIterator<Solution_>
    implements ProgressiveConstructionIterator<Solution_> {

  private Iterator<Supplier<ProgressiveConstructionIterator<Solution_>>> children;
  private ProgressiveConstructionIterator<Solution_> current;
  private boolean started;
  private boolean closed;
  private long completedWideningCount;

  ConcatenatingConstructionIterator(
      Iterator<Supplier<ProgressiveConstructionIterator<Solution_>>> children) {
    this.children = children;
  }

  @Override
  public boolean hasNext() {
    if (closed) {
      return false;
    }
    if (!started) {
      started = true;
      startNextChild();
    }
    return current != null && current.hasNext();
  }

  private boolean startNextChild() {
    if (current != null) {
      completedWideningCount += current.getWideningCount();
      current.close();
      current = null;
    }
    while (children.hasNext()) {
      current = children.next().get();
      if (current.hasNext() || current.advanceBatch()) {
        return true;
      }
      completedWideningCount += current.getWideningCount();
      current.close();
      current = null;
    }
    return false;
  }

  @Override
  public Move<Solution_> next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    return current.next();
  }

  @Override
  public void recordScore(ConstructionHeuristicMoveScope<Solution_> moveScope) {
    if (current != null) {
      current.recordScore(moveScope);
    }
  }

  @Override
  public boolean advanceBatch() {
    if (closed) {
      return false;
    }
    if (hasNext()) {
      throw new IllegalStateException("The current construction batch still has unconsumed moves.");
    }
    return current != null && current.advanceBatch() || startNextChild();
  }

  @Override
  public long getWideningCount() {
    return completedWideningCount + (current == null ? 0 : current.getWideningCount());
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      if (current != null) {
        completedWideningCount += current.getWideningCount();
        current.close();
      }
    } finally {
      current = null;
      children = Collections.emptyIterator();
    }
  }
}
