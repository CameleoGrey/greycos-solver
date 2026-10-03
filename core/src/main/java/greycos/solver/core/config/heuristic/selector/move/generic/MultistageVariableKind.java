package greycos.solver.core.config.heuristic.selector.move.generic;

import jakarta.xml.bind.annotation.XmlEnum;

/** The kind of genuine planning variable declared for a cross-variable selector. */
@XmlEnum
public enum MultistageVariableKind {
  BASIC,
  LIST
}
