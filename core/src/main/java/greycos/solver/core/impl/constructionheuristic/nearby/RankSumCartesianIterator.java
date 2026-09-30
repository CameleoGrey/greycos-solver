package greycos.solver.core.impl.constructionheuristic.nearby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

/**
 * Lazily enumerates ranked tuples by the sum of their child ranks, breaking ties lexicographically
 * by those ranks. Each child is read and cached only as far as the explored tuple frontier
 * requires.
 */
public final class RankSumCartesianIterator<T> implements Iterator<List<T>>, AutoCloseable {

  private final List<Iterator<? extends T>> children;
  private final List<List<T>> caches;
  private final PriorityQueue<RankTuple> frontier = new PriorityQueue<>();
  private boolean initialized;
  private boolean closed;

  public RankSumCartesianIterator(List<? extends Iterator<? extends T>> children) {
    this.children = new ArrayList<>(children);
    caches = new ArrayList<>(children.size());
    for (int i = 0; i < children.size(); i++) {
      caches.add(new ArrayList<>());
    }
  }

  private boolean ensureRank(int childIndex, int rank) {
    var cache = caches.get(childIndex);
    var child = children.get(childIndex);
    while (cache.size() <= rank) {
      if (!child.hasNext()) {
        return false;
      }
      cache.add(child.next());
    }
    return true;
  }

  @Override
  public boolean hasNext() {
    if (closed) {
      return false;
    }
    if (!initialized) {
      initialized = true;
      for (int i = 0; i < children.size(); i++) {
        if (!ensureRank(i, 0)) {
          return false;
        }
      }
      frontier.add(new RankTuple(new int[children.size()], 0));
    }
    return !frontier.isEmpty();
  }

  @Override
  public List<T> next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    var tuple = frontier.remove();
    var result = new ArrayList<T>(children.size());
    for (int i = 0; i < children.size(); i++) {
      result.add(caches.get(i).get(tuple.ranks[i]));
    }
    for (int i = 0; i < children.size(); i++) {
      if (tuple.ranks[i] < Integer.MAX_VALUE && ensureRank(i, tuple.ranks[i] + 1)) {
        var nextRanks = tuple.ranks.clone();
        nextRanks[i]++;
        frontier.add(new RankTuple(nextRanks, tuple.sum + 1));
      }
      // Every tuple has one parent, obtained by decrementing its first non-zero rank. This
      // generates the frontier without a visited set or duplicate Cartesian tuples.
      if (tuple.ranks[i] != 0) {
        break;
      }
    }
    return Collections.unmodifiableList(result);
  }

  @Override
  public void close() {
    closed = true;
    frontier.clear();
    children.clear();
    caches.clear();
  }

  private record RankTuple(int[] ranks, long sum) implements Comparable<RankTuple> {

    @Override
    public int compareTo(RankTuple other) {
      var comparison = Long.compare(sum, other.sum);
      for (int i = 0; comparison == 0 && i < ranks.length; i++) {
        comparison = Integer.compare(ranks[i], other.ranks[i]);
      }
      return comparison;
    }
  }
}
