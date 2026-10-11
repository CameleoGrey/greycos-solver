package greycos.solver.core.impl.heuristic.selector.list;

import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;

/** Resolves one list declaration from the owner and nested selector targets. */
public final class ListVariableSelectorSupport {

  private ListVariableSelectorSupport() {}

  public static <Solution_> ListVariableDescriptor<Solution_> resolve(
      HeuristicConfigPolicy<Solution_> configPolicy,
      Class<?> entityClass,
      Object... selectorConfigs) {
    String variableName = null;
    var downcastClasses = new java.util.ArrayList<Class<?>>();
    GenuineVariableDescriptor<Solution_> recordedDescriptor = null;
    for (var selectorConfig : selectorConfigs) {
      if (selectorConfig == null) {
        continue;
      }
      ValueSelectorConfig valueConfig;
      GenuineVariableDescriptor<Solution_> candidate = null;
      if (selectorConfig instanceof SubListSelectorConfig subListConfig) {
        valueConfig = subListConfig.getValueSelectorConfig();
        if (subListConfig.getMimicSelectorRef() != null) {
          var recorder = configPolicy.getSubListMimicRecorder(subListConfig.getMimicSelectorRef());
          if (recorder == null) {
            throw new IllegalArgumentException(
                "The subListSelector (%s) has a mimicSelectorRef (%s) for which no recording selector exists."
                    .formatted(subListConfig, subListConfig.getMimicSelectorRef()));
          }
          candidate = recorder.getVariableDescriptor();
        }
      } else {
        valueConfig = (ValueSelectorConfig) selectorConfig;
      }
      if (valueConfig != null) {
        if (valueConfig.getDowncastEntityClass() != null) {
          downcastClasses.add(valueConfig.getDowncastEntityClass());
        }
        var selectedName = valueConfig.getVariableName();
        if (selectedName != null) {
          if (variableName != null && !variableName.equals(selectedName)) {
            throw new IllegalArgumentException(
                "The list selectors must target the same planning list variable, but use variableName (%s) and (%s)."
                    .formatted(variableName, selectedName));
          }
          variableName = selectedName;
        }
        if (valueConfig.getMimicSelectorRef() != null) {
          var recorder = configPolicy.getValueMimicRecorder(valueConfig.getMimicSelectorRef());
          if (recorder == null) {
            throw new IllegalArgumentException(
                "The valueSelector (%s) has a mimicSelectorRef (%s) for which no recording selector exists."
                    .formatted(valueConfig, valueConfig.getMimicSelectorRef()));
          }
          candidate = recorder.getVariableDescriptor();
        }
      }
      if (candidate != null) {
        if (!candidate.isListVariable()) {
          throw new IllegalArgumentException(
              "The list selector (%s) targets (%s), which is not a planning list variable."
                  .formatted(selectorConfig, candidate));
        }
        if (recordedDescriptor != null && recordedDescriptor != candidate) {
          throw new IllegalArgumentException(
              "The list selectors must target the same planning list variable, but record (%s) and (%s)."
                  .formatted(recordedDescriptor, candidate));
        }
        recordedDescriptor = candidate;
      }
    }
    if (recordedDescriptor != null) {
      if (variableName != null && !variableName.equals(recordedDescriptor.getVariableName())) {
        throw new IllegalArgumentException(
            "The list selector variableName (%s) does not match the recorded planning list variable (%s)."
                .formatted(variableName, recordedDescriptor));
      }
      variableName = recordedDescriptor.getVariableName();
      if (entityClass == null) {
        entityClass = recordedDescriptor.getEntityDescriptor().getEntityClass();
      }
    }
    var solutionDescriptor = configPolicy.getSolutionDescriptor();
    ListVariableDescriptor<Solution_> downcastDescriptor = null;
    for (var downcastClass : downcastClasses) {
      if (entityClass != null && !entityClass.isAssignableFrom(downcastClass)) {
        throw new IllegalArgumentException(
            "The list selector entityClass (%s) cannot downcast to (%s)."
                .formatted(entityClass, downcastClass));
      }
      var candidate = solutionDescriptor.resolveListVariableDescriptor(downcastClass, variableName);
      if (downcastDescriptor != null && downcastDescriptor != candidate) {
        throw new IllegalArgumentException(
            "The downcast list selectors must target the same planning list variable, but target (%s) and (%s)."
                .formatted(downcastDescriptor, candidate));
      }
      downcastDescriptor = candidate;
    }
    if (downcastDescriptor != null) {
      entityClass = downcastDescriptor.getEntityDescriptor().getEntityClass();
      variableName = downcastDescriptor.getVariableName();
    }
    if (entityClass != null && variableName != null) {
      var entityDescriptor = solutionDescriptor.findEntityDescriptor(entityClass);
      var variableDescriptor =
          entityDescriptor == null
              ? null
              : entityDescriptor.getGenuineVariableDescriptor(variableName);
      if (variableDescriptor != null && !variableDescriptor.isListVariable()) {
        throw new IllegalArgumentException(
            "The selector target (%s) is not a planning list variable."
                .formatted(variableDescriptor));
      }
    }
    if (!solutionDescriptor.hasListVariable()) {
      throw new IllegalArgumentException(
          "The list selector cannot unfold because there are no planning list variables; it cannot be deduced automatically.");
    }
    var descriptor = solutionDescriptor.resolveListVariableDescriptor(entityClass, variableName);
    if (recordedDescriptor != null && recordedDescriptor != descriptor) {
      throw new IllegalArgumentException(
          "The list selector target (%s) does not match the recorded planning list variable (%s)."
              .formatted(descriptor, recordedDescriptor));
    }
    return descriptor;
  }
}
