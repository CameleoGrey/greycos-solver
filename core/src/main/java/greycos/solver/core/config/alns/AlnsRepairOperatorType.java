package greycos.solver.core.config.alns;

import jakarta.xml.bind.annotation.XmlEnum;

@XmlEnum
public enum AlnsRepairOperatorType {
  GREEDY,
  REGRET_2,
  REGRET_3,
  RANDOMIZED_GREEDY
}
