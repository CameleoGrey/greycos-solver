package greycos.solver.core.config.alns;

import jakarta.xml.bind.annotation.XmlEnum;

@XmlEnum
public enum AlnsSelectionPolicyType {
  SEGMENTED_ROULETTE,
  UCB
}
