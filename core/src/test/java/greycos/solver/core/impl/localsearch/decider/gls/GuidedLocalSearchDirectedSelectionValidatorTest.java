package greycos.solver.core.impl.localsearch.decider.gls;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;

import org.junit.jupiter.api.Test;

class GuidedLocalSearchDirectedSelectionValidatorTest {

  @Test
  void supportedTreeIncludesAllBuiltInOrigins() {
    var config =
        new UnionMoveSelectorConfig(
            List.of(
                new ChangeMoveSelectorConfig(),
                new SwapMoveSelectorConfig(),
                new ListChangeMoveSelectorConfig(),
                new ListSwapMoveSelectorConfig(),
                new SubListChangeMoveSelectorConfig(),
                new SubListSwapMoveSelectorConfig(),
                new KOptListMoveSelectorConfig()));
    assertThatCode(() -> GuidedLocalSearchDirectedSelectionValidator.validate(config))
        .doesNotThrowAnyException();
  }

  @Test
  void unsupportedDescendantIncludesFullPathAndRemedy() {
    var config =
        new UnionMoveSelectorConfig(
            List.of(
                new ChangeMoveSelectorConfig(),
                new UnionMoveSelectorConfig(List.of(new CartesianProductMoveSelectorConfig()))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GuidedLocalSearchDirectedSelectionValidator.validate(config))
        .withMessageContaining("moveSelector.union[1].union[0]")
        .withMessageContaining("CartesianProductMoveSelectorConfig")
        .withMessageContaining("disable directedOriginSelection");
  }

  @Test
  void cachedMovesFailBeforeSelectorConstruction() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                GuidedLocalSearchDirectedSelectionValidator.validate(
                    new ChangeMoveSelectorConfig().withCacheType(SelectionCacheType.STEP)))
        .withMessageContaining("moveSelector.cacheType");
  }

  @Test
  void probabilityValueSourceRemainsUnsupportedForDirectedSelection() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                GuidedLocalSearchDirectedSelectionValidator.validate(
                    new ListChangeMoveSelectorConfig()
                        .withValueSelectorConfig(
                            new ValueSelectorConfig()
                                .withSelectionOrder(SelectionOrder.PROBABILISTIC))))
        .withMessageContaining("valueSelector.selectionOrder")
        .withMessageContaining("PROBABILISTIC");
  }

  @Test
  void ownOriginNearbyReplayIsSupported() {
    var config =
        new ListChangeMoveSelectorConfig()
            .withValueSelectorConfig(new ValueSelectorConfig().withId("origin"))
            .withDestinationSelectorConfig(
                new DestinationSelectorConfig()
                    .withNearbySelectionConfig(
                        new NearbySelectionConfig()
                            .withOriginValueSelectorConfig(
                                new ValueSelectorConfig().withMimicSelectorRef("origin"))));
    assertThatCode(() -> GuidedLocalSearchDirectedSelectionValidator.validate(config))
        .doesNotThrowAnyException();
  }

  @Test
  void originalSwapRejectsSecondaryReplayWithInheritedSelectionOrder() {
    var config =
        new UnionMoveSelectorConfig(
                List.of(
                    new SwapMoveSelectorConfig()
                        .withEntitySelectorConfig(new EntitySelectorConfig().withId("origin"))
                        .withSecondaryEntitySelectorConfig(
                            new EntitySelectorConfig().withMimicSelectorRef("origin"))))
            .withSelectionOrder(SelectionOrder.ORIGINAL);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GuidedLocalSearchDirectedSelectionValidator.validate(config))
        .withMessageContaining("moveSelector.union[0].secondaryEntitySelector.mimicSelectorRef")
        .withMessageContaining("use RANDOM swap selection");
  }

  @Test
  void externalReplayCannotBypassOriginSelection() {
    var config =
        new UnionMoveSelectorConfig(
            List.of(
                new ChangeMoveSelectorConfig()
                    .withEntitySelectorConfig(new EntitySelectorConfig().withId("outside")),
                new SwapMoveSelectorConfig()
                    .withEntitySelectorConfig(
                        new EntitySelectorConfig().withMimicSelectorRef("outside"))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GuidedLocalSearchDirectedSelectionValidator.validate(config))
        .withMessageContaining("moveSelector.union[1].entitySelector.mimicSelectorRef")
        .withMessageContaining("outside");
  }
}
