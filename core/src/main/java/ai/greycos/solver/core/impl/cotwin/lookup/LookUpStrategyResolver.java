package ai.greycos.solver.core.impl.cotwin.lookup;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentMap;

import ai.greycos.solver.core.api.cotwin.lookup.PlanningId;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.impl.cotwin.common.CotwinAccessType;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessorFactory;
import ai.greycos.solver.core.impl.cotwin.policy.DescriptorPolicy;
import ai.greycos.solver.core.impl.cotwin.solution.cloner.DeepCloningUtils;
import ai.greycos.solver.core.impl.util.ConcurrentMemoization;

/** This class is thread-safe. */
public final class LookUpStrategyResolver {

  private final LookUpStrategyType lookUpStrategyType;
  private final CotwinAccessType cotwinAccessType;
  private final MemberAccessorFactory memberAccessorFactory;
  private final ConcurrentMap<Class<?>, LookUpStrategy> decisionCache =
      new ConcurrentMemoization<>();

  public LookUpStrategyResolver(DescriptorPolicy descriptorPolicy) {
    this(descriptorPolicy, LookUpStrategyType.PLANNING_ID_OR_NONE);
  }

  public LookUpStrategyResolver(
      DescriptorPolicy descriptorPolicy, LookUpStrategyType lookUpStrategyType) {
    this.lookUpStrategyType = lookUpStrategyType;
    this.cotwinAccessType = descriptorPolicy.getCotwinAccessType();
    this.memberAccessorFactory = descriptorPolicy.getMemberAccessorFactory();
  }

  /**
   * This method is thread-safe.
   *
   * @param object never null
   * @return never null
   */
  public LookUpStrategy determineLookUpStrategy(Object object) {
    return decisionCache.computeIfAbsent(
        object.getClass(),
        objectClass -> {
          if (DeepCloningUtils.isImmutable(objectClass)) {
            return new ImmutableLookUpStrategy();
          }
          return switch (lookUpStrategyType) {
            case PLANNING_ID_OR_NONE -> {
              var memberAccessor =
                  ConfigUtils.findPlanningIdMemberAccessor(
                      objectClass, memberAccessorFactory, cotwinAccessType);
              if (memberAccessor == null) {
                yield new NoneLookUpStrategy();
              }
              yield new PlanningIdLookUpStrategy(memberAccessor);
            }
            case PLANNING_ID_OR_FAIL_FAST -> {
              var memberAccessor =
                  ConfigUtils.findPlanningIdMemberAccessor(
                      objectClass, memberAccessorFactory, cotwinAccessType);
              if (memberAccessor == null) {
                throw new IllegalArgumentException(
                    "The class ("
                        + objectClass
                        + ") does not have a @"
                        + PlanningId.class.getSimpleName()
                        + " annotation,"
                        + " but the lookUpStrategyType ("
                        + lookUpStrategyType
                        + ") requires it.\n"
                        + "Maybe add the @"
                        + PlanningId.class.getSimpleName()
                        + " annotation"
                        + " or change the @"
                        + PlanningSolution.class.getSimpleName()
                        + " annotation's "
                        + LookUpStrategyType.class.getSimpleName()
                        + ".");
              }
              yield new PlanningIdLookUpStrategy(memberAccessor);
            }
            case EQUALITY -> {
              Method equalsMethod;
              Method hashCodeMethod;
              try {
                equalsMethod = objectClass.getMethod("equals", Object.class);
                hashCodeMethod = objectClass.getMethod("hashCode");
              } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                    "Impossible state because equals() and hashCode() always exist.", e);
              }
              if (equalsMethod.getDeclaringClass().equals(Object.class)) {
                throw new IllegalArgumentException(
                    "The class ("
                        + objectClass.getSimpleName()
                        + ") doesn't override the equals() method, neither does any superclass.");
              }
              if (hashCodeMethod.getDeclaringClass().equals(Object.class)) {
                throw new IllegalArgumentException(
                    "The class ("
                        + objectClass.getSimpleName()
                        + ") overrides equals() but neither it nor any superclass"
                        + " overrides the hashCode() method.");
              }
              yield new EqualsLookUpStrategy();
            }
            case NONE -> new NoneLookUpStrategy();
          };
        });
  }
}
