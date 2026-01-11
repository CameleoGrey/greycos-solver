package ai.greycos.solver.core.impl.score.director.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.greycos.solver.core.api.score.constraint.Indictment;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.declarative.ConsistencyTracker;
import ai.greycos.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.VariableDescriptor;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirector;
import ai.greycos.solver.core.impl.score.director.InnerScore;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintSession;

import org.jspecify.annotations.NullMarked;

/**
 * FP streams implementation of {@link ScoreDirector}, which only recalculates the {@link Score} of
 * the part of the {@link PlanningSolution working solution} that changed, instead of the going
 * through the entire {@link PlanningSolution}. This is incremental calculation, which is fast.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @see ScoreDirector
 */
public final class BavetConstraintStreamScoreDirector<Solution_, Score_ extends Score<Score_>>
    extends AbstractScoreDirector<
        Solution_, Score_, BavetConstraintStreamScoreDirectorFactory<Solution_, Score_>> {

  private final boolean derived;
  private BavetConstraintSession<Score_> session;

  /**
   * Tracks entities that were inserted into the current session during
   * setWorkingSolutionWithoutUpdatingShadows or later when their shadow variables trigger updates.
   * Uses identity comparison to detect when an entity reference from an old solution is passed to
   * update methods. When an entity is not in this set and is not a valid entity from the current
   * solution, it means it's from an old solution (e.g., cached in a move from before solution
   * adoption) and should NOT be updated in the session to prevent score corruption.
   *
   * <p>IMPORTANT: During setWorkingSolutionWithoutUpdatingShadows, we only track entities that have
   * genuine planning variables. Value entities that exist only in value ranges (without genuine
   * variables) are NOT tracked initially to prevent them from being inserted into Bavet before
   * their shadow variables are initialized. They are added to this set later when they are assigned
   * to planning variables and their shadow variables trigger updates.
   */
  private Set<Object> insertedEntities = Collections.emptySet();

  private BavetConstraintStreamScoreDirector(Builder<Solution_, Score_> builder, boolean derived) {
    super(builder);
    this.derived = derived;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  /**
   * The function is exclusively available for the Bavet score director, and its use must be
   * approached with caution. The primary purpose of this method is to enable the {@code
   * ConstraintVerifier} to ignore events related to shadow variables when testing constraints that
   * do not rely on them.
   *
   * @see AbstractScoreDirector#clearVariableListenerEvents()
   */
  public void clearShadowVariablesListenerQueue() {
    clearVariableListenerEvents();
  }

  /**
   * The function is exclusively available for the Bavet score director, and its use must be
   * approached with caution. The primary purpose of this method is to enable the {@code
   * ConstraintVerifier} to update the consistency status of entities in the solution without
   * updating shadows.
   *
   * <p>This must be done before setWorkingSolutionWithoutUpdatingShadows, which inserts the
   * entities.
   */
  public void updateConsistencyFromSolution(Solution_ solution) {
    var solutionDescriptor = getSolutionDescriptor();
    var entityList = new ArrayList<>();
    solutionDescriptor.visitAllEntities(solution, entityList::add);
    variableListenerSupport.setConsistencyTracker(
        ConsistencyTracker.frozen(getSolutionDescriptor(), entityList.toArray()));
  }

  @Override
  public void setWorkingSolutionWithoutUpdatingShadows(Solution_ workingSolution) {
    // Reset the consistency tracker to clear any references to old entities.
    // When a new working solution is set (e.g., during island model global best adoption),
    // the new solution contains cloned entities with the same planning IDs but different
    // Java object references. The old consistency tracker may still contain references to
    // old entities, causing the Bavet session to track both old and new entities,
    // resulting in score corruption (doubled scores).
    variableListenerSupport.setConsistencyTracker(new ConsistencyTracker<>());
    session =
        scoreDirectorFactory.newSession(
            workingSolution,
            variableListenerSupport.getConsistencyTracker(),
            constraintMatchPolicy,
            derived);
    // Track which entities are inserted into the session to detect stale entity references.
    // When solution is replaced (e.g., island model adoption), cached moves may hold references
    // to old entities. We track inserted entities using identity to detect and skip updates
    // for entities not in the current session, preventing score corruption.
    //
    // IMPORTANT: We only track entities that have genuine planning variables. Value entities
    // that exist only in value ranges (without genuine variables) should NOT be tracked here.
    // They will be added to the tracker when they are assigned to planning variables and their
    // shadow variables trigger updates. This prevents value entities from being inserted into
    // Bavet before their shadow variables are initialized.
    //
    // NOTE: Create and assign the entityTracker BEFORE calling
    // super.setWorkingSolutionWithoutUpdatingShadows
    // because shadow variable initialization during that call may trigger afterVariableChanged,
    // which will try to add entities to this set.
    var entityTracker = Collections.newSetFromMap(new IdentityHashMap<>());
    insertedEntities = entityTracker;
    super.setWorkingSolutionWithoutUpdatingShadows(
        workingSolution,
        entity -> {
          session.insert(entity);
          // Only track entities with genuine planning variables.
          // Value entities without genuine variables (only in value ranges) will be tracked
          // later when they're assigned and their shadow variables trigger updates.
          var entityDescriptor = getSolutionDescriptor().findEntityDescriptor(entity.getClass());
          if (entityDescriptor != null
              && !entityDescriptor.getGenuineVariableDescriptorList().isEmpty()) {
            entityTracker.add(entity);
          }
        });
  }

  @Override
  protected void afterSetWorkingSolution() {
    // Settle the node network to calculate precomputes
    // This is required so precomputes are not considered by terminations
    session.settle();
  }

  @Override
  public InnerScore<Score_> calculateScore() {
    variableListenerSupport.assertNotificationQueuesAreEmpty();
    var score = session.calculateScore();
    setCalculatedScore(score);
    return new InnerScore<>(score, -getWorkingInitScore());
  }

  @Override
  public Map<String, ConstraintMatchTotal<Score_>> getConstraintMatchTotalMap() {
    if (!constraintMatchPolicy.isEnabled()) {
      throw new IllegalStateException(
          "When constraint matching is disabled, this method should not be called.");
    } else if (workingSolution == null) {
      throw new IllegalStateException(
          "The method setWorkingSolution() must be called before the method getConstraintMatchTotalMap().");
    }
    return session.getConstraintMatchTotalMap();
  }

  @Override
  public Map<Object, Indictment<Score_>> getIndictmentMap() {
    if (!constraintMatchPolicy.isJustificationEnabled()) {
      throw new IllegalStateException(
          "When constraint matching with justifications is disabled, this method should not be called.");
    } else if (workingSolution == null) {
      throw new IllegalStateException(
          "The method setWorkingSolution() must be called before the method getIndictmentMap().");
    }
    return session.getIndictmentMap();
  }

  @Override
  public boolean requiresFlushing() {
    return true; // Tuple refresh happens during score calculation.
  }

  @Override
  public void close() {
    super.close();
    if (session != null) {
      session = null;
    }
  }

  // ************************************************************************
  // Entity/variable add/change/remove methods
  // ************************************************************************

  // public void beforeEntityAdded(EntityDescriptor entityDescriptor, Object entity) // Do nothing

  @Override
  public void afterEntityAdded(EntityDescriptor<Solution_> entityDescriptor, Object entity) {
    if (entity == null) {
      throw new IllegalArgumentException(
          "The entity (%s) cannot be added to the ScoreDirector.".formatted(entity));
    }
    if (!getSolutionDescriptor().hasEntityDescriptor(entity.getClass())) {
      throw new IllegalArgumentException(
          "The entity (%s) of class (%s) is not a configured @%s."
              .formatted(entity, entity.getClass(), PlanningEntity.class.getSimpleName()));
    }
    session.insert(entity);
    super.afterEntityAdded(entityDescriptor, entity);
  }

  // public void beforeVariableChanged(VariableDescriptor variableDescriptor, Object entity) // Do
  // nothing

  @Override
  public void afterVariableChanged(
      VariableDescriptor<Solution_> variableDescriptor, Object entity) {
    // Only update if entity is from current solution, not from cached moves with old references.
    // Old entity references would be inserted as new tuples, corrupting the score.
    //
    // Value entities that are not yet in insertedEntities (because they were in value ranges
    // during initialization) will be added now when their shadow variables trigger updates.
    if (insertedEntities.contains(entity)) {
      session.update(entity);
    } else {
      // Entity not yet tracked - check if it's a valid entity from the current solution
      var entityDescriptor = getSolutionDescriptor().findEntityDescriptor(entity.getClass());
      if (entityDescriptor != null) {
        // This is a valid entity from the current solution (not a stale reference).
        // Add it to the tracker and update it in Bavet.
        insertedEntities.add(entity);
        session.update(entity);
      }
      // If entityDescriptor is null, this is not an entity (e.g., a problem fact),
      // or it's a stale reference from an old solution, so we skip the update.
    }
    super.afterVariableChanged(variableDescriptor, entity);
  }

  @Override
  public void afterListVariableChanged(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object entity,
      int fromIndex,
      int toIndex) {
    // Only update if entity is from current solution, not from cached moves with old references.
    // Old entity references would be inserted as new tuples, corrupting the score.
    //
    // Value entities that are not yet in insertedEntities (because they were in value ranges
    // during initialization) will be added now when their shadow variables trigger updates.
    if (insertedEntities.contains(entity)) {
      session.update(entity);
    } else {
      // Entity not yet tracked - check if it's a valid entity from the current solution
      var entityDescriptor = getSolutionDescriptor().findEntityDescriptor(entity.getClass());
      if (entityDescriptor != null) {
        // This is a valid entity from the current solution (not a stale reference).
        // Add it to the tracker and update it in Bavet.
        insertedEntities.add(entity);
        session.update(entity);
      }
      // If entityDescriptor is null, this is not an entity (e.g., a problem fact),
      // or it's a stale reference from an old solution, so we skip the update.
    }
    super.afterListVariableChanged(variableDescriptor, entity, fromIndex, toIndex);
  }

  // public void beforeEntityRemoved(EntityDescriptor entityDescriptor, Object entity) // Do nothing

  @Override
  public void afterEntityRemoved(EntityDescriptor<Solution_> entityDescriptor, Object entity) {
    // Only retract if entity is from current solution, not from cached moves with old references.
    // Old entity references are not in the session, so retracting them would be a no-op at best
    // or cause issues at worst.
    if (insertedEntities.contains(entity)) {
      session.retract(entity);
    }
    super.afterEntityRemoved(entityDescriptor, entity);
  }

  // ************************************************************************
  // Problem fact add/change/remove methods
  // ************************************************************************

  // public void beforeProblemFactAdded(Object problemFact) // Do nothing

  @Override
  public void afterProblemFactAdded(Object problemFact) {
    if (problemFact == null) {
      throw new IllegalArgumentException(
          "The problemFact (%s) cannot be added to the ScoreDirector.".formatted(problemFact));
    }
    session.insert(problemFact);
    super.afterProblemFactAdded(problemFact);
  }

  @Override
  public void beforeProblemPropertyChanged(Object problemFactOrEntity) {
    // Since this is called when a fact (not a variable) changes,
    // we need to retract and reinsert to update cached static data
    super.beforeProblemPropertyChanged(problemFactOrEntity);
    session.retract(problemFactOrEntity);
  }

  @Override
  public void afterProblemPropertyChanged(Object problemFactOrEntity) {
    session.insert(problemFactOrEntity);
    super.afterProblemPropertyChanged(problemFactOrEntity);
  }

  // public void beforeProblemFactRemoved(Object problemFact) // Do nothing

  @Override
  public void afterProblemFactRemoved(Object problemFact) {
    session.retract(problemFact);
    super.afterProblemFactRemoved(problemFact);
  }

  /**
   * Exposed for debugging purposes, so that we can hook into it from tests and while reproducing
   * issues.
   *
   * @return null before first {@link #setWorkingSolutionWithoutUpdatingShadows(Object)} or after
   *     {@link #close()}.
   */
  @SuppressWarnings("unused")
  public BavetConstraintSession<Score_> getSession() {
    return session;
  }

  @Override
  public boolean isDerived() {
    return derived;
  }

  @NullMarked
  public static final class Builder<Solution_, Score_ extends Score<Score_>>
      extends AbstractScoreDirectorBuilder<
          Solution_,
          Score_,
          BavetConstraintStreamScoreDirectorFactory<Solution_, Score_>,
          Builder<Solution_, Score_>> {

    public Builder(
        BavetConstraintStreamScoreDirectorFactory<Solution_, Score_> scoreDirectorFactory) {
      super(scoreDirectorFactory);
    }

    @Override
    public BavetConstraintStreamScoreDirector<Solution_, Score_> build() {
      return new BavetConstraintStreamScoreDirector<>(this, false);
    }

    @Override
    public AbstractScoreDirector<
            Solution_, Score_, BavetConstraintStreamScoreDirectorFactory<Solution_, Score_>>
        buildDerived() {
      return new BavetConstraintStreamScoreDirector<>(this, true);
    }
  }
}
