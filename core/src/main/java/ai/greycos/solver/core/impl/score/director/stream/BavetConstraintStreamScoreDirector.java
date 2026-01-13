package ai.greycos.solver.core.impl.score.director.stream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.greycos.solver.core.api.score.constraint.Indictment;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
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
   * Tracks entities inserted into the current session. Uses identity comparison to detect stale
   * entity references from old solutions (e.g., cached in moves before solution adoption) to
   * prevent score corruption.
   *
   * <p>During setWorkingSolutionWithoutUpdatingShadows, only entities with genuine planning
   * variables are tracked. Value entities in value ranges are added later when assigned and their
   * shadow variables trigger updates, preventing premature insertion before initialization.
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
    // Reset consistency tracker to clear old entity references (e.g., from island model adoption)
    // and prevent tracking both old and new entities, which would corrupt scores.
    // However, if the tracker is already frozen (set by ConstraintVerifier), preserve it.
    var currentTracker = variableListenerSupport.getConsistencyTracker();
    if (!currentTracker.isFrozen()) {
      variableListenerSupport.setConsistencyTracker(new ConsistencyTracker<>());
    }
    session =
        scoreDirectorFactory.newSession(
            workingSolution,
            variableListenerSupport.getConsistencyTracker(),
            constraintMatchPolicy,
            derived);
    // Track inserted entities to detect stale references from old solutions.
    // When the consistency tracker is frozen (ConstraintVerifier mode), track all entities
    // to support constraint verification on solutions with manually set shadow variables.
    // Otherwise, only track entities with genuine planning variables initially; value entities
    // in ranges are added later when assigned, preventing premature insertion before
    // shadow variable initialization.
    //
    // NOTE: entityTracker must be assigned BEFORE super.setWorkingSolutionWithoutUpdatingShadows
    // because shadow variable initialization during that call triggers afterVariableChanged.
    var entityTracker = Collections.newSetFromMap(new IdentityHashMap<>());
    insertedEntities = entityTracker;
    var trackerIsFrozen = variableListenerSupport.getConsistencyTracker().isFrozen();
    super.setWorkingSolutionWithoutUpdatingShadows(
        workingSolution,
        entity -> {
          session.insert(entity);
          // Track all entities when frozen (ConstraintVerifier mode),
          // otherwise only track entities with genuine planning variables.
          // Value entities without genuine variables (only in value ranges) will be tracked
          // later when assigned, except in frozen mode where we need them immediately.
          var entityDescriptor = getSolutionDescriptor().findEntityDescriptor(entity.getClass());
          if (entityDescriptor != null) {
            if (trackerIsFrozen || !entityDescriptor.getGenuineVariableDescriptorList().isEmpty()) {
              entityTracker.add(entity);
            }
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
    // Only update entities from current solution, not stale references from cached moves.
    // Value entities not yet tracked (from value ranges during initialization) are added now
    // when their shadow variables trigger updates.
    if (insertedEntities.contains(entity)) {
      session.update(entity);
    } else {
      // Entity not yet tracked - check if valid from current solution
      var entityDescriptor = getSolutionDescriptor().findEntityDescriptor(entity.getClass());
      if (entityDescriptor != null) {
        // Valid entity from current solution - add to tracker and update in Bavet
        insertedEntities.add(entity);
        session.update(entity);
      }
      // If entityDescriptor is null, not an entity or stale reference - skip update
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
