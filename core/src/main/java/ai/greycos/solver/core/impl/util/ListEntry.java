package ai.greycos.solver.core.impl.util;

import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface ListEntry<T>
    permits ElementAwareLinkedList.Entry, ElementAwareArrayList.Entry {

  boolean isRemoved();

  T getElement();
}
