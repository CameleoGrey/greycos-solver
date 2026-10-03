package greycos.solver.core.impl.heuristic.selector.move.generic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import greycos.solver.core.api.solver.multistage.BasicVariableReference;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableReference;
import greycos.solver.core.api.solver.multistage.MultistageVariableReference;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.move.generic.CrossVariableMultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.MultistageVariableKind;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.multistage.MultistageDefinition;
import greycos.solver.core.impl.multistage.MultistageVariableBinding;

/** Binds every declared variable before a cross-variable provider can run. */
public final class CrossVariableMultistageMoveSelectorFactory<Solution_>
    extends AbstractMultistageMoveSelectorFactory<
        Solution_, CrossVariableMultistageMoveSelectorConfig> {

  public CrossVariableMultistageMoveSelectorFactory(
      CrossVariableMultistageMoveSelectorConfig config) {
    super(
        config,
        null,
        null,
        config.getStageProviderClass(),
        config.determineCandidateCountLimit(),
        config.determineProbeCountLimit(),
        false);
  }

  @Override
  protected Class<?> getProviderInterface() {
    return CrossVariableStageProvider.class;
  }

  @Override
  protected MoveSelector<Solution_> buildBaseMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    var definition =
        new MultistageDefinition<>(
            configPolicy.getSolutionDescriptor(),
            resolveVariableBindings(configPolicy),
            config.getStageProviderClass(),
            config.determineProbeCountLimit());
    return new MultistageMoveSelector<>(
        definition, randomSelection, config.determineCandidateCountLimit());
  }

  List<MultistageVariableBinding<Solution_>> resolveVariableBindings(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    var declarations = config.getVariableList();
    if (declarations == null || declarations.isEmpty()) {
      throw new IllegalArgumentException(
          "The crossVariableMultistageMoveSelector (%s) requires a nonempty variableList. "
                  .formatted(config)
              + "Maybe declare every basic and list variable the stage provider will access.");
    }
    var solutionDescriptor = configPolicy.getSolutionDescriptor();
    var references = new HashSet<MultistageVariableReference<?, ?>>();
    var bindings = new ArrayList<MultistageVariableBinding<Solution_>>(declarations.size());
    for (int index = 0; index < declarations.size(); index++) {
      var declaration = declarations.get(index);
      if (declaration == null) {
        throw new IllegalArgumentException(
            "The crossVariableMultistageMoveSelector (%s) has a null variable declaration at index (%d)."
                .formatted(config, index));
      }
      var kind = declaration.getKind();
      var entityClass = declaration.getEntityClass();
      var variableName = declaration.getVariableName();
      var valueClass = declaration.getValueClass();
      if (kind == null
          || entityClass == null
          || variableName == null
          || variableName.isBlank()
          || valueClass == null) {
        throw new IllegalArgumentException(
            "The crossVariableMultistageMoveSelector (%s) has an incomplete variable declaration at index (%d): kind (%s), entityClass (%s), variableName (%s), valueClass (%s). All four fields are required and variableName must be nonblank."
                .formatted(config, index, kind, entityClass, variableName, valueClass));
      }
      var targetEntityDescriptor = solutionDescriptor.getEntityDescriptorStrict(entityClass);
      if (targetEntityDescriptor == null) {
        throw new IllegalArgumentException(
            "The crossVariableMultistageMoveSelector (%s) declares entityClass (%s) at index (%d), which is not a configured planning entity. Maybe use one of %s."
                .formatted(
                    config, entityClass.getName(), index, solutionDescriptor.getEntityClassSet()));
      }
      var variableDescriptor = targetEntityDescriptor.getVariableDescriptor(variableName);
      if (!(variableDescriptor instanceof GenuineVariableDescriptor<Solution_> genuineVariable)) {
        throw new IllegalArgumentException(
            "The crossVariableMultistageMoveSelector (%s) declares variable (%s.%s) at index (%d), which is not a genuine planning variable."
                .formatted(config, entityClass.getName(), variableName, index));
      }
      if (genuineVariable.isListVariable() != (kind == MultistageVariableKind.LIST)) {
        throw new IllegalArgumentException(
            "The crossVariableMultistageMoveSelector (%s) declares kind (%s) for variable (%s.%s) at index (%d), but the planning variable kind is (%s)."
                .formatted(
                    config,
                    kind,
                    entityClass.getName(),
                    variableName,
                    index,
                    genuineVariable.isListVariable()
                        ? MultistageVariableKind.LIST
                        : MultistageVariableKind.BASIC));
      }
      var declaredValueClass =
          genuineVariable instanceof ListVariableDescriptor<Solution_> listVariable
              ? listVariable.getElementType()
              : genuineVariable.getVariablePropertyType();
      if (!valueClass.equals(declaredValueClass)) {
        throw new IllegalArgumentException(
            "The crossVariableMultistageMoveSelector (%s) declares valueClass (%s) for variable (%s.%s) at index (%d), but its exact declared value type is (%s). Maybe use that exact type in both the configuration and the variable reference."
                .formatted(
                    config,
                    valueClass.getName(),
                    entityClass.getName(),
                    variableName,
                    index,
                    declaredValueClass.getName()));
      }
      MultistageVariableReference<?, ?> reference =
          kind == MultistageVariableKind.LIST
              ? ListVariableReference.of(entityClass, variableName, valueClass)
              : BasicVariableReference.of(entityClass, variableName, valueClass);
      if (!references.add(reference)) {
        throw new IllegalArgumentException(
            "The crossVariableMultistageMoveSelector (%s) has duplicate variable declaration (%s) at index (%d). Maybe declare each exact variable reference only once."
                .formatted(config, reference, index));
      }
      // Different configured entity scopes may intentionally inherit the same descriptor.
      bindings.add(
          new MultistageVariableBinding<>(reference, genuineVariable, targetEntityDescriptor));
    }
    return List.copyOf(bindings);
  }
}
