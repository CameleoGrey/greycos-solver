package ai.greycos.solver.core.testutil;

import java.util.List;
import java.util.Objects;

import ai.greycos.solver.core.impl.heuristic.move.SelectorBasedCompositeMove;
import ai.greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import ai.greycos.solver.core.impl.heuristic.selector.list.SubList;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedPillarChangeMove;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedSwapMove;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedListAssignMove;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedListChangeMove;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedListSwapMove;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedListUnassignMove;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedSubListChangeMove;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedSubListSwapMove;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.SelectorBasedSubListUnassignMove;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PositionInList;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.UnassignedElement;
import ai.greycos.solver.core.preview.api.move.Move;

public interface CodeAssertable {

  String getCode();

  static CodeAssertable convert(Object o) {
    Objects.requireNonNull(o);
    if (o instanceof CodeAssertable assertable) {
      return assertable;
    } else if (o instanceof SelectorBasedNoChangeMove<?>) {
      return () -> "No change";
    } else if (o instanceof SelectorBasedChangeMove<?> changeMove) {
      final String code =
          convert(changeMove.getEntity()).getCode()
              + "->"
              + convert(changeMove.getToPlanningValue()).getCode();
      return () -> code;
    } else if (o instanceof SelectorBasedSwapMove<?> swapMove) {
      final String code =
          convert(swapMove.getLeftEntity()).getCode()
              + "<->"
              + convert(swapMove.getRightEntity()).getCode();
      return () -> code;
    } else if (o instanceof SelectorBasedPillarChangeMove<?> pillarChangeMove) {
      final String code =
          pillarChangeMove.getPillar()
              + "->"
              + convert(pillarChangeMove.getToPlanningValue()).getCode();
      return () -> code;
    } else if (o instanceof SelectorBasedCompositeMove<?> compositeMove) {
      StringBuilder codeBuilder = new StringBuilder(compositeMove.getMoves().length * 80);
      for (Move<?> move : compositeMove.getMoves()) {
        codeBuilder.append("+").append(convert(move).getCode());
      }
      final String code = codeBuilder.substring(1);
      return () -> code;
    } else if (o instanceof SelectorBasedListAssignMove<?> listAssignMove) {
      return () ->
          convert(listAssignMove.getMovedValue())
              + " {null->"
              + convert(listAssignMove.getDestinationEntity())
              + "["
              + listAssignMove.getDestinationIndex()
              + "]}";
    } else if (o instanceof SelectorBasedListUnassignMove<?> listUnassignMove) {
      return () ->
          convert(listUnassignMove.getMovedValue())
              + " {"
              + convert(listUnassignMove.getSourceEntity())
              + "["
              + listUnassignMove.getSourceIndex()
              + "]->null}";
    } else if (o instanceof SelectorBasedListChangeMove<?> listChangeMove) {
      return () ->
          convert(listChangeMove.getMovedValue())
              + " {"
              + convert(listChangeMove.getSourceEntity())
              + "["
              + listChangeMove.getSourceIndex()
              + "]->"
              + convert(listChangeMove.getDestinationEntity())
              + "["
              + listChangeMove.getDestinationIndex()
              + "]}";
    } else if (o instanceof SelectorBasedListSwapMove<?> listSwapMove) {
      return () ->
          convert(listSwapMove.getLeftValue())
              + " {"
              + convert(listSwapMove.getLeftEntity())
              + "["
              + listSwapMove.getLeftIndex()
              + "]} <-> "
              + convert(listSwapMove.getRightValue())
              + " {"
              + convert(listSwapMove.getRightEntity())
              + "["
              + listSwapMove.getRightIndex()
              + "]}";
    } else if (o instanceof SelectorBasedSubListChangeMove<?> subListChangeMove) {
      return () ->
          "|"
              + subListChangeMove.getSubListSize()
              + "| {"
              + convert(subListChangeMove.getSourceEntity())
              + "["
              + subListChangeMove.getFromIndex()
              + ".."
              + subListChangeMove.getToIndex()
              + "]-"
              + (subListChangeMove.isReversing() ? "reversing->" : ">")
              + convert(subListChangeMove.getDestinationEntity())
              + "["
              + subListChangeMove.getDestinationIndex()
              + "]}";
    } else if (o instanceof SelectorBasedSubListUnassignMove<?> subListUnassignMove) {
      return () ->
          "|"
              + subListUnassignMove.getSubListSize()
              + "| {"
              + convert(subListUnassignMove.getSourceEntity())
              + "["
              + subListUnassignMove.getFromIndex()
              + ".."
              + subListUnassignMove.getToIndex()
              + "]->null}";
    } else if (o instanceof SelectorBasedSubListSwapMove<?> subListSwapMove) {
      return () ->
          "{"
              + convert(subListSwapMove.getLeftSubList()).getCode()
              + "} <-"
              + (subListSwapMove.isReversing() ? "reversing-" : "")
              + "> {"
              + convert(subListSwapMove.getRightSubList()).getCode()
              + "}";
    } else if (o instanceof List<?> list) {
      StringBuilder codeBuilder = new StringBuilder("[");
      boolean firstElement = true;
      for (Object element : list) {
        if (firstElement) {
          firstElement = false;
        } else {
          codeBuilder.append(", ");
        }
        codeBuilder.append(convert(element).getCode());
      }
      codeBuilder.append("]");
      final String code = codeBuilder.toString();
      return () -> code;
    } else if (o instanceof SubList subList) {
      return () ->
          convert(subList.entity()) + "[" + subList.fromIndex() + "+" + subList.length() + "]";
    } else if (o instanceof UnassignedElement unassignedLocation) {
      return unassignedLocation::toString;
    } else if (o instanceof PositionInList locationInList) {
      return () -> convert(locationInList.entity()) + "[" + locationInList.index() + "]";
    }
    throw new AssertionError(
        ("o's class (" + o.getClass() + ") cannot be converted to CodeAssertable."));
  }
}
