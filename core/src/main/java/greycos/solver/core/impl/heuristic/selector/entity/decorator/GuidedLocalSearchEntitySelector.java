package greycos.solver.core.impl.heuristic.selector.entity.decorator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchSelectionContext;

/** Applies GLS priorities to legal origin entities before mimic recording. */
public final class GuidedLocalSearchEntitySelector<Solution_>
    extends AbstractDemandEnabledSelector<Solution_> implements EntitySelector<Solution_> {

  private final EntitySelector<Solution_> child;
  private final GuidedLocalSearchSelectionContext<Solution_> context;
  private final List<String> variableNames;
  private final boolean discardLosingProbes;

  public GuidedLocalSearchEntitySelector(
      EntitySelector<Solution_> child,
      GuidedLocalSearchSelectionContext<Solution_> context,
      List<String> variableNames,
      boolean discardLosingProbes) {
    this.child = child;
    this.context = context;
    this.variableNames = List.copyOf(variableNames);
    this.discardLosingProbes = discardLosingProbes;
    phaseLifecycleSupport.addEventListener(child);
  }

  @Override
  public EntityDescriptor<Solution_> getEntityDescriptor() {
    return child.getEntityDescriptor();
  }

  @Override
  public SelectionCacheType getCacheType() {
    return child.getCacheType();
  }

  @Override
  public boolean isNeverEnding() {
    return child.isNeverEnding();
  }

  @Override
  public long getSize() {
    return child.getSize();
  }

  @Override
  public Iterator<Object> iterator() {
    return context.direct(
        child.iterator(),
        discardLosingProbes,
        entity -> context.entityPriority(entity, variableNames));
  }

  @Override
  public Iterator<Object> endingIterator() {
    return child.endingIterator();
  }

  @Override
  public ListIterator<Object> listIterator() {
    return listIterator(0);
  }

  @Override
  public ListIterator<Object> listIterator(int index) {
    if (index < 0) {
      throw new IndexOutOfBoundsException(index);
    }
    var directed = iterator();
    var result =
        new ListIterator<Object>() {
          private final List<Object> history = new ArrayList<>();
          private int cursor;

          @Override
          public boolean hasNext() {
            return cursor < history.size() || directed.hasNext();
          }

          @Override
          public Object next() {
            if (cursor < history.size()) {
              return history.get(cursor++);
            }
            var next = directed.next();
            history.add(next);
            cursor++;
            return next;
          }

          @Override
          public boolean hasPrevious() {
            return cursor > 0;
          }

          @Override
          public Object previous() {
            if (!hasPrevious()) {
              throw new NoSuchElementException();
            }
            return history.get(--cursor);
          }

          @Override
          public int nextIndex() {
            return cursor;
          }

          @Override
          public int previousIndex() {
            return cursor - 1;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }

          @Override
          public void set(Object value) {
            throw new UnsupportedOperationException();
          }

          @Override
          public void add(Object value) {
            throw new UnsupportedOperationException();
          }
        };
    for (int i = 0; i < index; i++) {
      if (!result.hasNext()) {
        throw new IndexOutOfBoundsException(index);
      }
      result.next();
    }
    return result;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof GuidedLocalSearchEntitySelector<?> that
        && Objects.equals(child, that.child)
        && context == that.context
        && variableNames.equals(that.variableNames)
        && discardLosingProbes == that.discardLosingProbes;
  }

  @Override
  public int hashCode() {
    return Objects.hash(child, context, variableNames, discardLosingProbes);
  }
}
