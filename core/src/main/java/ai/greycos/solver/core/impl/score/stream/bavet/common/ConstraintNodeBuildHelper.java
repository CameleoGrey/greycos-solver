package ai.greycos.solver.core.impl.score.stream.bavet.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.bavet.common.AbstractNodeBuildHelper;
import ai.greycos.solver.core.impl.bavet.common.BavetAbstractConstraintStream;
import ai.greycos.solver.core.impl.bavet.common.InnerConstraintProfiler;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import ai.greycos.solver.core.impl.score.stream.common.ForEachFilteringCriteria;
import ai.greycos.solver.core.impl.score.stream.common.inliner.AbstractScoreInliner;

import org.jspecify.annotations.Nullable;

public final class ConstraintNodeBuildHelper<Solution_, Score_ extends Score<Score_>>
    extends AbstractNodeBuildHelper<BavetAbstractConstraintStream<Solution_>> {

  private final AbstractScoreInliner<Score_> scoreInliner;
  private final ConsistencyTracker<Solution_> consistencyTracker;
  private final Map<EntityDescriptor<?>, Map<ForEachFilteringCriteria, Predicate<Object>>>
      entityDescriptorToForEachCriteriaToPredicateMap;

  public ConstraintNodeBuildHelper(
      ConsistencyTracker<Solution_> consistencyTracker,
      Set<BavetAbstractConstraintStream<Solution_>> activeStreamSet,
      AbstractScoreInliner<Score_> scoreInliner,
      @Nullable InnerConstraintProfiler profiler) {
    super(activeStreamSet, profiler);
    this.consistencyTracker = consistencyTracker;
    this.scoreInliner = scoreInliner;
    this.entityDescriptorToForEachCriteriaToPredicateMap = new HashMap<>();
  }

  public AbstractScoreInliner<Score_> getScoreInliner() {
    return scoreInliner;
  }

  public <A> Predicate<A> getForEachPredicateForEntityDescriptorAndCriteria(
      EntityDescriptor<?> entityDescriptor, ForEachFilteringCriteria criteria) {
    var predicateMap =
        entityDescriptorToForEachCriteriaToPredicateMap.computeIfAbsent(
            entityDescriptor, ignored -> new HashMap<>());
    return (Predicate<A>)
        predicateMap.computeIfAbsent(
            criteria,
            ignored -> criteria.getFilterForEntityDescriptor(consistencyTracker, entityDescriptor));
  }
}
