package greycos.solver.greybay.config.template;

import java.util.LinkedHashMap;
import java.util.Objects;

import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import greycos.solver.greybay.api.ComponentIdentity;

/**
 * A registered selector tree, copied on input and for every materialization. Null uses core
 * automatic moves.
 */
public record MovePortfolio(
    ComponentIdentity identity, MoveSelectorConfig<?> selector, TemplateRequirements requirements) {
  public MovePortfolio {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(requirements, "requirements");
    selector = copySelector(selector);
  }

  @Override
  public MoveSelectorConfig<?> selector() {
    return copySelector(selector);
  }

  public static MovePortfolio automatic(
      ComponentIdentity identity, TemplateRequirements requirements) {
    return new MovePortfolio(identity, null, requirements);
  }

  private static MoveSelectorConfig<?> copySelector(MoveSelectorConfig<?> selector) {
    if (selector == null) return null;
    var copy = selector.copyConfig();
    detachProperties(copy);
    return copy;
  }

  // Core config inheritance deliberately shares property maps; registered portfolios own their
  // maps.
  private static void detachProperties(MoveSelectorConfig<?> selector) {
    if (selector instanceof MoveListFactoryConfig factory
        && factory.getMoveListFactoryCustomProperties() != null) {
      factory.setMoveListFactoryCustomProperties(
          new LinkedHashMap<>(factory.getMoveListFactoryCustomProperties()));
    } else if (selector instanceof MoveIteratorFactoryConfig factory
        && factory.getMoveIteratorFactoryCustomProperties() != null) {
      factory.setMoveIteratorFactoryCustomProperties(
          new LinkedHashMap<>(factory.getMoveIteratorFactoryCustomProperties()));
    } else if (selector instanceof UnionMoveSelectorConfig union
        && union.getMoveSelectorList() != null) {
      union.getMoveSelectorList().forEach(MovePortfolio::detachProperties);
    } else if (selector instanceof CartesianProductMoveSelectorConfig product
        && product.getMoveSelectorList() != null) {
      product.getMoveSelectorList().forEach(MovePortfolio::detachProperties);
    }
  }
}
