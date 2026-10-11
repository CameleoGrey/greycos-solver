package greycos.solver.greybay.config.template;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.ProblemCapabilities;

/**
 * An application-approved destroy/repair portfolio. The eligibility feature must certify actual
 * variable scopes and eligible bindings for this portfolio on the loaded problem, including pins.
 * Every callback class has an explicit identity; capabilities must include its {@link
 * #callbackFeature}.
 */
public record AlnsPortfolio(
    ComponentIdentity identity,
    List<AlnsDestroyOperatorConfig> destroys,
    List<AlnsRepairOperatorConfig> repairs,
    TemplateRequirements requirements,
    String eligibilityFeature,
    Map<Class<?>, ComponentIdentity> callbacks) {
  public AlnsPortfolio {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(requirements, "requirements");
    Objects.requireNonNull(eligibilityFeature, "eligibilityFeature");
    destroys = destroys.stream().map(AlnsDestroyOperatorConfig::copyConfig).toList();
    repairs = repairs.stream().map(AlnsRepairOperatorConfig::copyConfig).toList();
    callbacks = Map.copyOf(callbacks);
    if (destroys.isEmpty() || repairs.isEmpty() || eligibilityFeature.isBlank()) {
      throw new IllegalArgumentException(
          "ALNS portfolios require destroy/repair operators and an eligibility capability ID.");
    }
    var destroyIds = new HashSet<String>();
    var repairIds = new HashSet<String>();
    for (var destroy : destroys) {
      requireId(destroy.getId(), destroyIds);
      if (destroy.getType() == null && destroy.getCustomClass() == null) {
        throw new IllegalArgumentException(
            "Destroy operator (" + destroy.getId() + ") requires an explicit type.");
      }
      if (destroy.getCustomClass() != null
              && (destroy.getType() != null
                  || destroy.getRelatednessClass() != null
                  || destroy.getRankingClass() != null
                  || destroy.getGroupingClass() != null)
          || destroy.getRelatednessClass() != null
              && destroy.getType() != AlnsDestroyOperatorType.RELATEDNESS
          || destroy.getRankingClass() != null
              && destroy.getType() != AlnsDestroyOperatorType.WORST_REMOVAL
          || destroy.getGroupingClass() != null
              && destroy.getType() != AlnsDestroyOperatorType.GROUP_REMOVAL) {
        throw new IllegalArgumentException(
            "Destroy operator ("
                + destroy.getId()
                + ") has incompatible built-in/custom callback fields.");
      }
      requireCallback(destroy.getCustomClass(), callbacks);
      requireCallback(destroy.getRelatednessClass(), callbacks);
      requireCallback(destroy.getRankingClass(), callbacks);
      requireCallback(destroy.getGroupingClass(), callbacks);
      if (destroy.getType() == AlnsDestroyOperatorType.RELATEDNESS
          && destroy.getRelatednessClass() == null) {
        throw new IllegalArgumentException(
            "Destroy operator (" + destroy.getId() + ") requires its callback.");
      }
      if (repairs.stream().noneMatch(repair -> covers(repair, destroy))) {
        throw new IllegalArgumentException(
            "Destroy operator ("
                + destroy.getId()
                + ") has no repair covering its declared scope.");
      }
    }
    for (var repair : repairs) {
      requireId(repair.getId(), repairIds);
      if (repair.getType() == null && repair.getCustomClass() == null) {
        throw new IllegalArgumentException(
            "Repair operator (" + repair.getId() + ") requires an explicit type.");
      }
      if (repair.getType() != null && repair.getCustomClass() != null) {
        throw new IllegalArgumentException(
            "Repair operator (" + repair.getId() + ") combines a built-in type and custom class.");
      }
      requireCallback(repair.getCustomClass(), callbacks);
      if (repair.getRegretK() != null && repair.getType() != AlnsRepairOperatorType.REGRET_K
          || repair.getTopK() != null
              && repair.getType() != AlnsRepairOperatorType.RANDOMIZED_GREEDY) {
        throw new IllegalArgumentException(
            "Repair operator (" + repair.getId() + ") has inactive regretK/topK fields.");
      }
    }
  }

  private static void requireId(String id, HashSet<String> ids) {
    if (id == null || id.isBlank() || !ids.add(id)) {
      throw new IllegalArgumentException(
          "ALNS operator ID (" + id + ") must be nonblank and unique within its portfolio.");
    }
  }

  private static void requireCallback(Class<?> type, Map<Class<?>, ComponentIdentity> callbacks) {
    if (type != null && !callbacks.containsKey(type)) {
      throw new IllegalArgumentException(
          "ALNS callback (" + type.getName() + ") requires a registered component identity.");
    }
  }

  private static boolean covers(
      AlnsRepairOperatorConfig repair, AlnsDestroyOperatorConfig destroy) {
    return (repair.getEntityClass() == null
            || destroy.getEntityClass() != null
                && repair.getEntityClass().isAssignableFrom(destroy.getEntityClass()))
        && (repair.getVariableName() == null
            || Objects.equals(repair.getVariableName(), destroy.getVariableName()));
  }

  public static String callbackFeature(ComponentIdentity identity) {
    return "callback:"
        + identity.id()
        + ":"
        + identity.version()
        + ":"
        + identity.artifactIdentity();
  }

  public void validate(ProblemCapabilities capabilities) {
    requirements.validate(capabilities);
    if (!capabilities.features().contains(eligibilityFeature)) {
      throw new IllegalArgumentException(
          "ALNS portfolio ("
              + identity.id()
              + ") needs loaded-model eligibility capability ("
              + eligibilityFeature
              + ").");
    }
    if (!capabilities.listVariables()
        && destroys.stream()
            .anyMatch(operator -> operator.getType() == AlnsDestroyOperatorType.LIST_BLOCK)) {
      throw new IllegalArgumentException("ALNS LIST_BLOCK requires a list-variable model.");
    }
    for (var callback : callbacks.values()) {
      if (!capabilities.features().contains(callbackFeature(callback))) {
        throw new IllegalArgumentException(
            "Problem does not approve ALNS callback (" + callback + ").");
      }
    }
  }

  @Override
  public List<AlnsDestroyOperatorConfig> destroys() {
    return destroys.stream().map(AlnsDestroyOperatorConfig::copyConfig).toList();
  }

  @Override
  public List<AlnsRepairOperatorConfig> repairs() {
    return repairs.stream().map(AlnsRepairOperatorConfig::copyConfig).toList();
  }
}
