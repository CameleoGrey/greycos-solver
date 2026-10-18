package greycos.solver.core.impl.cotwin.variable.cascade;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import greycos.solver.core.impl.cotwin.common.accessor.MemberAccessorType;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.policy.DescriptorPolicy;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.score.director.ScoreDirector;

public final class CascadingUpdateShadowVariableDescriptor<Solution_>
    extends ShadowVariableDescriptor<Solution_> {

  private final List<ShadowVariableTarget<Solution_>> shadowVariableTargetList;
  private final List<VariableDescriptor<Solution_>> targetVariableDescriptorList =
      new ArrayList<>();
  private VariableDescriptor<Solution_> firstTargetVariableDescriptor;
  private MemberAccessor targetMethod;
  private ListVariableDescriptor<Solution_> sourceVariableDescriptor;

  public CascadingUpdateShadowVariableDescriptor(
      int ordinal,
      EntityDescriptor<Solution_> entityDescriptor,
      MemberAccessor variableMemberAccessor) {
    super(ordinal, entityDescriptor, variableMemberAccessor);
    shadowVariableTargetList = new ArrayList<>();
    addTargetVariable(entityDescriptor, variableMemberAccessor);
  }

  public void addTargetVariable(
      EntityDescriptor<Solution_> entityDescriptor, MemberAccessor variableMemberAccessor) {
    shadowVariableTargetList.add(
        new ShadowVariableTarget<>(entityDescriptor, variableMemberAccessor));
  }

  public String getTargetMethodName() {
    var variableMemberAccessor = shadowVariableTargetList.get(0).variableMemberAccessor();
    return Arrays.stream(
            variableMemberAccessor.getDeclaredAnnotationsByType(
                CascadingUpdateShadowVariable.class))
        .findFirst()
        .map(CascadingUpdateShadowVariable::targetMethodName)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "The entity %s is not annotated with @%s."
                        .formatted(
                            entityDescriptor.getEntityClass(),
                            CascadingUpdateShadowVariable.class.getSimpleName())));
  }

  public boolean update(ScoreDirector<Solution_> scoreDirector, Object entity) {
    if (targetVariableDescriptorList.size() == 1) {
      return updateSingle(scoreDirector, entity);
    } else {
      return updateMultiple(scoreDirector, entity);
    }
  }

  private boolean updateSingle(ScoreDirector<Solution_> scoreDirector, Object entity) {
    var oldValue = firstTargetVariableDescriptor.getValue(entity);
    scoreDirector.beforeVariableChanged(entity, firstTargetVariableDescriptor.getVariableName());
    targetMethod.executeGetter(entity);
    var newValue = firstTargetVariableDescriptor.getValue(entity);
    scoreDirector.afterVariableChanged(entity, firstTargetVariableDescriptor.getVariableName());
    return !Objects.equals(oldValue, newValue);
  }

  private boolean updateMultiple(ScoreDirector<Solution_> scoreDirector, Object entity) {
    var oldValueList = new ArrayList<>(targetVariableDescriptorList.size());
    for (var targetVariableDescriptor : targetVariableDescriptorList) {
      scoreDirector.beforeVariableChanged(entity, targetVariableDescriptor.getVariableName());
      oldValueList.add(targetVariableDescriptor.getValue(entity));
    }
    targetMethod.executeGetter(entity);
    var hasChange = false;
    for (var i = 0; i < targetVariableDescriptorList.size(); i++) {
      var targetVariableDescriptor = targetVariableDescriptorList.get(i);
      var newValue = targetVariableDescriptor.getValue(entity);
      scoreDirector.afterVariableChanged(entity, targetVariableDescriptor.getVariableName());
      if (!hasChange && !Objects.equals(oldValueList.get(i), newValue)) {
        hasChange = true;
      }
    }
    return hasChange;
  }

  @Override
  public void processAnnotations(DescriptorPolicy descriptorPolicy) {
    // Do nothing
  }

  @Override
  public void linkVariableDescriptors(DescriptorPolicy descriptorPolicy) {
    for (var shadowVariableTarget : shadowVariableTargetList) {
      targetVariableDescriptorList.add(
          shadowVariableTarget
              .entityDescriptor()
              .getShadowVariableDescriptor(
                  shadowVariableTarget.variableMemberAccessor().getName()));
    }

    var candidates =
        entityDescriptor.getSolutionDescriptor().getListVariableDescriptorList().stream()
            .filter(
                descriptor ->
                    descriptor.acceptsValueType(entityDescriptor.getEntityClass())
                        || entityDescriptor
                            .getEntityClass()
                            .isAssignableFrom(descriptor.getElementType()))
            .toList();
    if (candidates.size() > 1) {
      throw new IllegalArgumentException(
          "The entityClass (%s) has an @%s-annotated property (%s), but matches multiple planning list variables (%s). Declare the cascading shadow on the specific element class."
              .formatted(
                  entityDescriptor.getEntityClass(),
                  CascadingUpdateShadowVariable.class.getSimpleName(),
                  variableMemberAccessor.getName(),
                  candidates));
    }
    sourceVariableDescriptor = candidates.isEmpty() ? null : candidates.getFirst();
    if (sourceVariableDescriptor == null
        && entityDescriptor.getSolutionDescriptor().getListVariableDescriptorList().size() > 1) {
      throw new IllegalArgumentException(
          "The entityClass (%s) has an @%s-annotated property (%s), but is not an element type of a planning list variable."
              .formatted(
                  entityDescriptor.getEntityClass(),
                  CascadingUpdateShadowVariable.class.getSimpleName(),
                  variableMemberAccessor.getName()));
    }

    var targetMethodName = getTargetMethodName();
    var allSourceMethodMembers =
        ConfigUtils.getDeclaredMembers(entityDescriptor.getEntityClass()).stream()
            .filter(
                member ->
                    member.getName().equals(targetMethodName)
                        && member instanceof Method method
                        && method.getParameterCount() == 0)
            .toList();
    if (allSourceMethodMembers.isEmpty()) {
      throw new IllegalArgumentException(
          "The entity class (%s) has an @%s annotated property (%s), but the method \"%s\" cannot be found."
              .formatted(
                  entityDescriptor.getEntityClass(),
                  CascadingUpdateShadowVariable.class.getSimpleName(),
                  variableMemberAccessor.getName(),
                  targetMethodName));
    }
    targetMethod =
        descriptorPolicy
            .getMemberAccessorFactory()
            .buildAndCacheMemberAccessor(
                allSourceMethodMembers.get(0),
                MemberAccessorType.VOID_METHOD,
                null,
                descriptorPolicy.getCotwinAccessType());
    firstTargetVariableDescriptor = targetVariableDescriptorList.get(0);
  }

  @Override
  public ListVariableDescriptor<Solution_> getSourceVariableDescriptor() {
    return sourceVariableDescriptor;
  }

  @Override
  public Collection<Class<?>> getUpdaterClasses() {
    return Collections.emptyList();
  }

  private record ShadowVariableTarget<Solution_>(
      EntityDescriptor<Solution_> entityDescriptor, MemberAccessor variableMemberAccessor) {}
}
