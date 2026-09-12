package greycos.solver.core.config.alns;

import jakarta.xml.bind.annotation.XmlEnum;

@XmlEnum
public enum AlnsDestroyOperatorType {
  RANDOM,
  RELATEDNESS,
  WORST_REMOVAL,
  LIST_BLOCK
}
