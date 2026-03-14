package ai.greycos.solver.core.impl.constructionheuristic.placer.entity;

import static ai.greycos.solver.core.testutil.PlannerAssert.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Iterator;

import ai.greycos.solver.core.impl.constructionheuristic.placer.Placement;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import ai.greycos.solver.core.preview.api.move.Move;

final class PlacementAssertions {

  static <Solution_> void assertEntityPlacement(
      Placement<Solution_> placement, String entityCode, String... valueCodes) {
    Iterator<Move<Solution_>> iterator = placement.iterator();
    assertThat(iterator).isNotNull();
    for (String valueCode : valueCodes) {
      assertThat(iterator).hasNext();
      SelectorBasedChangeMove<Solution_> move = adapt(iterator.next());
      assertCode(entityCode, move.getEntity());
      assertCode(valueCode, move.getToPlanningValue());
    }
    assertThat(iterator).isExhausted();
  }

  static <Solution_> SelectorBasedChangeMove<Solution_> adapt(Move<Solution_> move) {
    return (SelectorBasedChangeMove<Solution_>) move;
  }

  static <Solution_> void assertValuePlacement(
      Placement<Solution_> placement, String valueCode, String... entityCodes) {
    Iterator<Move<Solution_>> iterator = placement.iterator();
    assertThat(iterator).isNotNull();
    for (String entityCode : entityCodes) {
      assertThat(iterator).hasNext();
      SelectorBasedChangeMove<Solution_> move = adapt(iterator.next());
      assertCode(entityCode, move.getEntity());
      assertCode(valueCode, move.getToPlanningValue());
    }
    assertThat(iterator).isExhausted();
  }

  private PlacementAssertions() {}
}
