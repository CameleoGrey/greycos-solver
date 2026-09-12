package greycos.solver.core.config.alns;

import jakarta.xml.bind.annotation.XmlEnum;

@XmlEnum
public enum AlnsAcceptanceType {
  LATE_ACCEPTANCE,
  HILL_CLIMBING,
  SIMULATED_ANNEALING
}
