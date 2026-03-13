package ai.greycos.solver.core.impl.cotwin.policy;

import static ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessorType.FIELD_OR_GETTER_METHOD_WITH_SETTER;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.BendableBigDecimalScore;
import ai.greycos.solver.core.api.score.BendableScore;
import ai.greycos.solver.core.api.score.HardMediumSoftBigDecimalScore;
import ai.greycos.solver.core.api.score.HardMediumSoftScore;
import ai.greycos.solver.core.api.score.HardSoftBigDecimalScore;
import ai.greycos.solver.core.api.score.HardSoftScore;
import ai.greycos.solver.core.api.score.IBendableScore;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.SimpleBigDecimalScore;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.config.solver.PreviewFeature;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.impl.cotwin.common.CotwinAccessType;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessor;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessorFactory;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.score.descriptor.ScoreDescriptor;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.cotwin.valuerange.descriptor.CompositeValueRangeDescriptor;
import ai.greycos.solver.core.impl.cotwin.valuerange.descriptor.FromEntityPropertyValueRangeDescriptor;
import ai.greycos.solver.core.impl.cotwin.valuerange.descriptor.FromSolutionPropertyValueRangeDescriptor;
import ai.greycos.solver.core.impl.cotwin.valuerange.descriptor.ValueRangeDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.impl.score.definition.BendableBigDecimalScoreDefinition;
import ai.greycos.solver.core.impl.score.definition.BendableScoreDefinition;
import ai.greycos.solver.core.impl.score.definition.HardMediumSoftBigDecimalScoreDefinition;
import ai.greycos.solver.core.impl.score.definition.HardMediumSoftScoreDefinition;
import ai.greycos.solver.core.impl.score.definition.HardSoftBigDecimalScoreDefinition;
import ai.greycos.solver.core.impl.score.definition.HardSoftScoreDefinition;
import ai.greycos.solver.core.impl.score.definition.ScoreDefinition;
import ai.greycos.solver.core.impl.score.definition.SimpleBigDecimalScoreDefinition;
import ai.greycos.solver.core.impl.score.definition.SimpleScoreDefinition;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class DescriptorPolicy {
  private Map<String, SolutionCloner> generatedSolutionClonerMap = new LinkedHashMap<>();
  private final Map<String, MemberAccessor> fromSolutionValueRangeProviderMap =
      new LinkedHashMap<>();
  private final Set<MemberAccessor> anonymousFromSolutionValueRangeProviderSet =
      new LinkedHashSet<>();
  private final Map<String, MemberAccessor> fromEntityValueRangeProviderMap = new LinkedHashMap<>();
  private final Set<MemberAccessor> anonymousFromEntityValueRangeProviderSet =
      new LinkedHashSet<>();
  private CotwinAccessType cotwinAccessType = CotwinAccessType.FORCE_REFLECTION;
  private Set<PreviewFeature> enabledPreviewFeatureSet = EnumSet.noneOf(PreviewFeature.class);
  @Nullable private MemberAccessorFactory memberAccessorFactory;
  private int entityDescriptorCount = 0;
  private int valueRangeDescriptorCount = 0;

  public <Solution_> EntityDescriptor<Solution_> buildEntityDescriptor(
      SolutionDescriptor<Solution_> solutionDescriptor, Class<?> entityClass) {
    var entityDescriptor =
        new EntityDescriptor<>(entityDescriptorCount++, solutionDescriptor, entityClass);
    solutionDescriptor.addEntityDescriptor(entityDescriptor);
    return entityDescriptor;
  }

  public <Score_ extends Score<Score_>> ScoreDescriptor<Score_> buildScoreDescriptor(
      Member member, Class<?> solutionClass) {
    MemberAccessor scoreMemberAccessor = buildScoreMemberAccessor(member);
    Class<Score_> scoreType = extractScoreType(scoreMemberAccessor, solutionClass);
    PlanningScore annotation = extractPlanningScoreAnnotation(scoreMemberAccessor);
    ScoreDefinition<Score_> scoreDefinition =
        buildScoreDefinition(solutionClass, scoreMemberAccessor, scoreType, annotation);
    return new ScoreDescriptor<>(scoreMemberAccessor, scoreDefinition);
  }

  public <Solution_> CompositeValueRangeDescriptor<Solution_> buildCompositeValueRangeDescriptor(
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      List<ValueRangeDescriptor<Solution_>> childValueRangeDescriptorList) {
    return new CompositeValueRangeDescriptor<>(
        valueRangeDescriptorCount++, variableDescriptor, childValueRangeDescriptorList);
  }

  public <Solution_>
      FromSolutionPropertyValueRangeDescriptor<Solution_>
          buildFromSolutionPropertyValueRangeDescriptor(
              GenuineVariableDescriptor<Solution_> variableDescriptor,
              MemberAccessor valueRangeProviderMemberAccessor) {
    return new FromSolutionPropertyValueRangeDescriptor<>(
        valueRangeDescriptorCount++, variableDescriptor, valueRangeProviderMemberAccessor);
  }

  public <Solution_>
      FromEntityPropertyValueRangeDescriptor<Solution_> buildFromEntityPropertyValueRangeDescriptor(
          GenuineVariableDescriptor<Solution_> variableDescriptor,
          MemberAccessor valueRangeProviderMemberAccessor) {
    return new FromEntityPropertyValueRangeDescriptor<>(
        valueRangeDescriptorCount++, variableDescriptor, valueRangeProviderMemberAccessor);
  }

  @SuppressWarnings("unchecked")
  private static <Score_ extends Score<Score_>> Class<Score_> extractScoreType(
      MemberAccessor scoreMemberAccessor, Class<?> solutionClass) {
    Class<?> memberType = scoreMemberAccessor.getType();
    if (!Score.class.isAssignableFrom(memberType)) {
      throw new IllegalStateException(
          "The solutionClass (%s) has a @%s annotated member (%s) that does not return a subtype of Score."
              .formatted(solutionClass, PlanningScore.class.getSimpleName(), scoreMemberAccessor));
    }
    if (memberType == Score.class) {
      throw new IllegalStateException(
          """
                            The solutionClass (%s) has a @%s annotated member (%s) that doesn't return a non-abstract %s class.
                            Maybe make it return %s or another specific %s implementation."""
              .formatted(
                  solutionClass,
                  PlanningScore.class.getSimpleName(),
                  scoreMemberAccessor,
                  Score.class.getSimpleName(),
                  HardSoftScore.class.getSimpleName(),
                  Score.class.getSimpleName()));
    }
    return (Class<Score_>) memberType;
  }

  private static PlanningScore extractPlanningScoreAnnotation(MemberAccessor scoreMemberAccessor) {
    PlanningScore annotation = scoreMemberAccessor.getAnnotation(PlanningScore.class);
    if (annotation != null) {
      return annotation;
    }
    // The member was auto-discovered.
    try {
      return ScoreDescriptor.class
          .getDeclaredField("PLANNING_SCORE")
          .getAnnotation(PlanningScore.class);
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException(
          "Impossible situation: the field (PLANNING_SCORE) must exist.", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static <Score_ extends Score<Score_>, ScoreDefinition_ extends ScoreDefinition<Score_>>
      ScoreDefinition_ buildScoreDefinition(
          Class<?> solutionClass,
          MemberAccessor scoreMemberAccessor,
          Class<Score_> scoreType,
          PlanningScore annotation) {
    Class<ScoreDefinition_> scoreDefinitionClass =
        (Class<ScoreDefinition_>) annotation.scoreDefinitionClass();
    int bendableHardLevelsSize = annotation.bendableHardLevelsSize();
    int bendableSoftLevelsSize = annotation.bendableSoftLevelsSize();
    if (!Objects.equals(scoreDefinitionClass, PlanningScore.NullScoreDefinition.class)) {
      if (bendableHardLevelsSize != PlanningScore.NO_LEVEL_SIZE
          || bendableSoftLevelsSize != PlanningScore.NO_LEVEL_SIZE) {
        throw new IllegalArgumentException(
            "The solutionClass (%s) has a @%s annotated member (%s) that has a scoreDefinition (%s) that must not have a bendableHardLevelsSize (%d) or a bendableSoftLevelsSize (%d)."
                .formatted(
                    solutionClass,
                    PlanningScore.class.getSimpleName(),
                    scoreMemberAccessor,
                    scoreDefinitionClass,
                    bendableHardLevelsSize,
                    bendableSoftLevelsSize));
      }
      return ConfigUtils.newInstance(
          () -> scoreMemberAccessor + " with @" + PlanningScore.class.getSimpleName(),
          "scoreDefinitionClass",
          scoreDefinitionClass);
    }
    if (!IBendableScore.class.isAssignableFrom(scoreType)) {
      if (bendableHardLevelsSize != PlanningScore.NO_LEVEL_SIZE
          || bendableSoftLevelsSize != PlanningScore.NO_LEVEL_SIZE) {
        throw new IllegalArgumentException(
            "The solutionClass (%s) has a @%s annotated member (%s) that returns a scoreType (%s) that must not have a bendableHardLevelsSize (%d) or a bendableSoftLevelsSize (%d)."
                .formatted(
                    solutionClass,
                    PlanningScore.class.getSimpleName(),
                    scoreMemberAccessor,
                    scoreType,
                    bendableHardLevelsSize,
                    bendableSoftLevelsSize));
      }
      if (scoreType.equals(SimpleScore.class)) {
        return (ScoreDefinition_) new SimpleScoreDefinition();
      } else if (scoreType.equals(SimpleBigDecimalScore.class)) {
        return (ScoreDefinition_) new SimpleBigDecimalScoreDefinition();
      } else if (scoreType.equals(HardSoftScore.class)) {
        return (ScoreDefinition_) new HardSoftScoreDefinition();
      } else if (scoreType.equals(HardSoftBigDecimalScore.class)) {
        return (ScoreDefinition_) new HardSoftBigDecimalScoreDefinition();
      } else if (scoreType.equals(HardMediumSoftScore.class)) {
        return (ScoreDefinition_) new HardMediumSoftScoreDefinition();
      } else if (scoreType.equals(HardMediumSoftBigDecimalScore.class)) {
        return (ScoreDefinition_) new HardMediumSoftBigDecimalScoreDefinition();
      } else {
        throw new IllegalArgumentException(
            """
                                The solutionClass (%s) has a @%s annotated member (%s) that returns a scoreType (%s) that is not recognized as a default %s implementation.
                                  If you intend to use a custom implementation, maybe set a scoreDefinition in the @%s annotation."""
                .formatted(
                    solutionClass,
                    PlanningScore.class.getSimpleName(),
                    scoreMemberAccessor,
                    scoreType,
                    Score.class.getSimpleName(),
                    PlanningScore.class.getSimpleName()));
      }
    } else {
      if (bendableHardLevelsSize == PlanningScore.NO_LEVEL_SIZE
          || bendableSoftLevelsSize == PlanningScore.NO_LEVEL_SIZE) {
        throw new IllegalArgumentException(
            "The solutionClass (%s) has a @%s annotated member (%s) that returns a scoreType (%s) that must have a bendableHardLevelsSize (%d) and a bendableSoftLevelsSize (%d)."
                .formatted(
                    solutionClass,
                    PlanningScore.class.getSimpleName(),
                    scoreMemberAccessor,
                    scoreType,
                    bendableHardLevelsSize,
                    bendableSoftLevelsSize));
      }
      if (scoreType.equals(BendableScore.class)) {
        return (ScoreDefinition_)
            new BendableScoreDefinition(bendableHardLevelsSize, bendableSoftLevelsSize);
      } else if (scoreType.equals(BendableBigDecimalScore.class)) {
        return (ScoreDefinition_)
            new BendableBigDecimalScoreDefinition(bendableHardLevelsSize, bendableSoftLevelsSize);
      } else {
        throw new IllegalArgumentException(
            """
                                The solutionClass (%s) has a @%s annotated member (%s) that returns a bendable scoreType (%s) that is not recognized as a default %s implementation.
                                  If you intend to use a custom implementation, maybe set a scoreDefinition in the annotation."""
                .formatted(
                    solutionClass,
                    PlanningScore.class.getSimpleName(),
                    scoreMemberAccessor,
                    scoreType,
                    Score.class.getSimpleName()));
      }
    }
  }

  public MemberAccessor buildScoreMemberAccessor(Member member) {
    return getMemberAccessorFactory()
        .buildAndCacheMemberAccessor(
            member, FIELD_OR_GETTER_METHOD_WITH_SETTER, PlanningScore.class, getCotwinAccessType());
  }

  public void addFromSolutionValueRangeProvider(MemberAccessor memberAccessor) {
    String id = extractValueRangeProviderId(memberAccessor);
    if (id == null) {
      anonymousFromSolutionValueRangeProviderSet.add(memberAccessor);
    } else {
      fromSolutionValueRangeProviderMap.put(id, memberAccessor);
    }
  }

  public boolean isFromSolutionValueRangeProvider(MemberAccessor memberAccessor) {
    return fromSolutionValueRangeProviderMap.containsValue(memberAccessor)
        || anonymousFromSolutionValueRangeProviderSet.contains(memberAccessor);
  }

  public boolean hasFromSolutionValueRangeProvider(String id) {
    return fromSolutionValueRangeProviderMap.containsKey(id);
  }

  public MemberAccessor getFromSolutionValueRangeProvider(String id) {
    return fromSolutionValueRangeProviderMap.get(id);
  }

  public Set<MemberAccessor> getAnonymousFromSolutionValueRangeProviderSet() {
    return anonymousFromSolutionValueRangeProviderSet;
  }

  public void addFromEntityValueRangeProvider(MemberAccessor memberAccessor) {
    String id = extractValueRangeProviderId(memberAccessor);
    if (id == null) {
      anonymousFromEntityValueRangeProviderSet.add(memberAccessor);
    } else {
      fromEntityValueRangeProviderMap.put(id, memberAccessor);
    }
  }

  public boolean isFromEntityValueRangeProvider(MemberAccessor memberAccessor) {
    return fromEntityValueRangeProviderMap.containsValue(memberAccessor)
        || anonymousFromEntityValueRangeProviderSet.contains(memberAccessor);
  }

  public boolean hasFromEntityValueRangeProvider(String id) {
    return fromEntityValueRangeProviderMap.containsKey(id);
  }

  public Set<MemberAccessor> getAnonymousFromEntityValueRangeProviderSet() {
    return anonymousFromEntityValueRangeProviderSet;
  }

  public CotwinAccessType getCotwinAccessType() {
    return cotwinAccessType;
  }

  public void setCotwinAccessType(CotwinAccessType cotwinAccessType) {
    this.cotwinAccessType = cotwinAccessType;
  }

  public Set<PreviewFeature> getEnabledPreviewFeatureSet() {
    return enabledPreviewFeatureSet;
  }

  public void setEnabledPreviewFeatureSet(Set<PreviewFeature> enabledPreviewFeatureSet) {
    this.enabledPreviewFeatureSet = enabledPreviewFeatureSet;
  }

  /**
   * @return never null
   */
  public Map<String, SolutionCloner> getGeneratedSolutionClonerMap() {
    return generatedSolutionClonerMap;
  }

  public void setGeneratedSolutionClonerMap(
      Map<String, SolutionCloner> generatedSolutionClonerMap) {
    this.generatedSolutionClonerMap = generatedSolutionClonerMap;
  }

  public MemberAccessorFactory getMemberAccessorFactory() {
    return memberAccessorFactory;
  }

  public void setMemberAccessorFactory(MemberAccessorFactory memberAccessorFactory) {
    this.memberAccessorFactory = memberAccessorFactory;
  }

  public MemberAccessor getFromEntityValueRangeProvider(String id) {
    return fromEntityValueRangeProviderMap.get(id);
  }

  public boolean isPreviewFeatureEnabled(PreviewFeature previewFeature) {
    return enabledPreviewFeatureSet.contains(previewFeature);
  }

  private @Nullable String extractValueRangeProviderId(MemberAccessor memberAccessor) {
    ValueRangeProvider annotation = memberAccessor.getAnnotation(ValueRangeProvider.class);
    String id = annotation.id();
    if (id == null || id.isEmpty()) {
      return null;
    }
    validateUniqueValueRangeProviderId(id, memberAccessor);
    return id;
  }

  private void validateUniqueValueRangeProviderId(String id, MemberAccessor memberAccessor) {
    MemberAccessor duplicate = fromSolutionValueRangeProviderMap.get(id);
    if (duplicate != null) {
      throw new IllegalStateException(
          "2 members (%s, %s) with a @%s annotation must not have the same id (%s)."
              .formatted(duplicate, memberAccessor, ValueRangeProvider.class.getSimpleName(), id));
    }
    duplicate = fromEntityValueRangeProviderMap.get(id);
    if (duplicate != null) {
      throw new IllegalStateException(
          "2 members (%s, %s) with a @%s annotation must not have the same id (%s)."
              .formatted(duplicate, memberAccessor, ValueRangeProvider.class.getSimpleName(), id));
    }
  }

  public Collection<String> getValueRangeProviderIds() {
    List<String> valueRangeProviderIds =
        new ArrayList<>(
            fromSolutionValueRangeProviderMap.size() + fromEntityValueRangeProviderMap.size());
    valueRangeProviderIds.addAll(fromSolutionValueRangeProviderMap.keySet());
    valueRangeProviderIds.addAll(fromEntityValueRangeProviderMap.keySet());
    return valueRangeProviderIds;
  }
}
