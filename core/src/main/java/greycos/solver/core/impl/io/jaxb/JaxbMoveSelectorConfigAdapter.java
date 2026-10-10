package greycos.solver.core.impl.io.jaxb;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.CrossVariableMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.PillarChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.PillarSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.RuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.solver.SolverConfig;

/** Marshals one native move selector inside a named configuration element. */
public final class JaxbMoveSelectorConfigAdapter
    extends XmlAdapter<
        JaxbMoveSelectorConfigAdapter.AdaptedMoveSelectorConfig, MoveSelectorConfig<?>> {

  @Override
  public MoveSelectorConfig<?> unmarshal(AdaptedMoveSelectorConfig adapted) {
    if (adapted == null) return null;
    if (adapted.moveSelectorConfig == null) {
      throw new IllegalArgumentException(
          "The perturbation element must contain one native move selector.");
    }
    return adapted.moveSelectorConfig;
  }

  @Override
  public AdaptedMoveSelectorConfig marshal(MoveSelectorConfig<?> selector) {
    if (selector == null) return null;
    var adapted = new AdaptedMoveSelectorConfig();
    adapted.moveSelectorConfig = selector;
    return adapted;
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  @XmlType(
      name = "iteratedLocalSearchPerturbationConfig",
      namespace = SolverConfig.XML_NAMESPACE,
      propOrder = {"moveSelectorConfig"})
  public static final class AdaptedMoveSelectorConfig {
    @XmlElements({
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = CartesianProductMoveSelectorConfig.XML_ELEMENT_NAME,
          type = CartesianProductMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = ChangeMoveSelectorConfig.XML_ELEMENT_NAME,
          type = ChangeMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = KOptListMoveSelectorConfig.XML_ELEMENT_NAME,
          type = KOptListMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = ListChangeMoveSelectorConfig.XML_ELEMENT_NAME,
          type = ListChangeMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = ListSwapMoveSelectorConfig.XML_ELEMENT_NAME,
          type = ListSwapMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = MoveIteratorFactoryConfig.XML_ELEMENT_NAME,
          type = MoveIteratorFactoryConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = MoveListFactoryConfig.XML_ELEMENT_NAME,
          type = MoveListFactoryConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = PillarChangeMoveSelectorConfig.XML_ELEMENT_NAME,
          type = PillarChangeMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = PillarSwapMoveSelectorConfig.XML_ELEMENT_NAME,
          type = PillarSwapMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = CrossVariableMultistageMoveSelectorConfig.XML_ELEMENT_NAME,
          type = CrossVariableMultistageMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = MultistageMoveSelectorConfig.XML_ELEMENT_NAME,
          type = MultistageMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = ListMultistageMoveSelectorConfig.XML_ELEMENT_NAME,
          type = ListMultistageMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = RuinRecreateMoveSelectorConfig.XML_ELEMENT_NAME,
          type = RuinRecreateMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = ListRuinRecreateMoveSelectorConfig.XML_ELEMENT_NAME,
          type = ListRuinRecreateMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = SubListChangeMoveSelectorConfig.XML_ELEMENT_NAME,
          type = SubListChangeMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = SubListSwapMoveSelectorConfig.XML_ELEMENT_NAME,
          type = SubListSwapMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = SwapMoveSelectorConfig.XML_ELEMENT_NAME,
          type = SwapMoveSelectorConfig.class),
      @XmlElement(
          namespace = SolverConfig.XML_NAMESPACE,
          name = UnionMoveSelectorConfig.XML_ELEMENT_NAME,
          type = UnionMoveSelectorConfig.class)
    })
    private MoveSelectorConfig<?> moveSelectorConfig;
  }
}
