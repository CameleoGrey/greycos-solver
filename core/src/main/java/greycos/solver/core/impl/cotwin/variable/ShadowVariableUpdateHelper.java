package greycos.solver.core.impl.cotwin.variable;

import static greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy.DISABLED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.ChangedVariableNotifier;
import greycos.solver.core.impl.cotwin.variable.declarative.DefaultShadowVariableSessionFactory;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.inverserelation.InverseRelationShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.nextprev.NextElementShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.nextprev.PreviousElementShadowVariableDescriptor;
import greycos.solver.core.impl.score.constraint.ConstraintMatchTotal;
import greycos.solver.core.impl.score.director.AbstractScoreDirector;
import greycos.solver.core.impl.score.director.AbstractScoreDirectorFactory;
import greycos.solver.core.impl.score.director.InnerScore;

import org.jspecify.annotations.NullMarked;

/** Utility class for updating shadow variables at entity level. */
@NullMarked
public final class ShadowVariableUpdateHelper<Solution_> {

  public static <Solution_> ShadowVariableUpdateHelper<Solution_> create() {
    return new ShadowVariableUpdateHelper<>();
  }

  @SuppressWarnings("unchecked")
  public void updateShadowVariables(Solution_ solution) {
    var solutionClass = (Class<Solution_>) Objects.requireNonNull(solution).getClass();
    var initialSolutionDescriptor = SolutionDescriptor.buildSolutionDescriptor(solutionClass);
    var entityClassSet = new LinkedHashSet<Class<?>>();
    initialSolutionDescriptor.visitAllEntities(
        solution, entity -> entityClassSet.add(entity.getClass()));
    var solutionDescriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            solutionClass, entityClassSet.toArray(new Class<?>[0]));
    try (var scoreDirector = new InternalScoreDirector.Builder<>(solutionDescriptor).build()) {
      // When we have a solution, we can reuse the logic from VariableSupport to update all variable
      // types
      scoreDirector.setWorkingSolution(solution);
    }
  }

  public void updateShadowVariables(Class<Solution_> solutionClass, Object... entities) {
    var entityClassList =
        Arrays.stream(entities)
            .map(Object::getClass)
            .filter(clazz -> clazz.isAnnotationPresent(PlanningEntity.class))
            .distinct()
            .toList();
    var solutionDescriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            Objects.requireNonNull(solutionClass), entityClassList.toArray(new Class<?>[0]));
    var session = InternalShadowVariableSession.build(solutionDescriptor, entities);
    // Validate dependencies before mutating supplied objects, without capturing stale graph edges.
    session.graphDescriptor().assertingNoReferencedMissingEntities();
    session.processBasicVariables();
    session.processListVariables();
    session.processDeclarativeVariables();
    session.processCascadingVariables();
  }

  private record InternalShadowVariableSession<Solution_>(
      SolutionDescriptor<Solution_> solutionDescriptor,
      List<EntityWithDescriptor<Solution_>> entities,
      Map<Object, EntityDescriptor<Solution_>> entityDescriptors,
      DefaultShadowVariableSessionFactory.GraphDescriptor<Solution_> graphDescriptor) {

    public static <Solution_> InternalShadowVariableSession<Solution_> build(
        SolutionDescriptor<Solution_> solutionDescriptor, Object... suppliedEntities) {
      var entities = new ArrayList<EntityWithDescriptor<Solution_>>();
      var entityDescriptors = new IdentityHashMap<Object, EntityDescriptor<Solution_>>();
      for (var entity : suppliedEntities) {
        var descriptor = solutionDescriptor.findEntityDescriptor(entity.getClass());
        if (descriptor != null && entityDescriptors.putIfAbsent(entity, descriptor) == null) {
          entities.add(new EntityWithDescriptor<>(entity, descriptor));
        }
      }
      return new InternalShadowVariableSession<>(
          solutionDescriptor,
          entities,
          entityDescriptors,
          new DefaultShadowVariableSessionFactory.GraphDescriptor<>(
              solutionDescriptor, ChangedVariableNotifier.empty(), suppliedEntities));
    }

    @SuppressWarnings("unchecked")
    public void processBasicVariables() {
      // Index by source descriptor and target identity; names alone do not identify a variable.
      var inverseCollections =
          new IdentityHashMap<
              BasicVariableDescriptor<Solution_>, Map<Object, List<Collection<Object>>>>();
      for (var target : entities) {
        for (var shadowDescriptor : target.entityDescriptor().getShadowVariableDescriptors()) {
          if (shadowDescriptor instanceof InverseRelationShadowVariableDescriptor<Solution_>
              && shadowDescriptor.getSourceVariableDescriptor()
                  instanceof BasicVariableDescriptor<Solution_> sourceDescriptor) {
            var collection = (Collection<Object>) shadowDescriptor.getValue(target.entity());
            if (collection == null) {
              throw new IllegalStateException(
                  "The entity (%s) has a variable (%s) with value (%s) which has a sourceVariableName variable (%s) which is null."
                      .formatted(
                          sourceDescriptor.getEntityDescriptor().getEntityClass(),
                          sourceDescriptor.getVariableName(),
                          target.entity(),
                          shadowDescriptor.getVariableName()));
            }
            inverseCollections
                .computeIfAbsent(sourceDescriptor, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(target.entity(), ignored -> new ArrayList<>())
                .add(collection);
          }
        }
      }
      // Validate every collection before clearing any of them; retain its identity and type.
      inverseCollections
          .values()
          .forEach(
              targets ->
                  targets.values().forEach(collections -> collections.forEach(Collection::clear)));
      for (var source : entities) {
        for (var sourceDescriptor : source.entityDescriptor().getBasicVariableDescriptorList()) {
          var targets = inverseCollections.get(sourceDescriptor);
          var target = sourceDescriptor.getValue(source.entity());
          if (targets != null && target != null) {
            var collections = targets.get(target);
            if (collections != null) {
              collections.forEach(collection -> collection.add(source.entity()));
            }
          }
        }
      }
    }

    public void processListVariables() {
      // Supplied elements may have been removed from a list since the previous update.
      for (var target : entities) {
        for (var shadowDescriptor : target.entityDescriptor().getShadowVariableDescriptors()) {
          if (isListRelationship(shadowDescriptor)) {
            shadowDescriptor.setValue(target.entity(), null);
          }
        }
      }
      for (var owner : entities) {
        for (var variableDescriptor : owner.entityDescriptor().getGenuineVariableDescriptorList()) {
          if (!(variableDescriptor instanceof ListVariableDescriptor<Solution_> listDescriptor)) {
            continue;
          }
          var values = listDescriptor.getValue(owner.entity());
          for (var index = 0; index < values.size(); index++) {
            var target = values.get(index);
            var targetDescriptor = entityDescriptors.get(target);
            if (targetDescriptor == null) {
              continue;
            }
            for (var shadowDescriptor : targetDescriptor.getShadowVariableDescriptors()) {
              if (shadowDescriptor.getSourceVariableDescriptor() != listDescriptor) {
                continue;
              }
              if (shadowDescriptor instanceof InverseRelationShadowVariableDescriptor<Solution_>) {
                shadowDescriptor.setValue(target, owner.entity());
              } else if (shadowDescriptor instanceof IndexShadowVariableDescriptor<Solution_>) {
                shadowDescriptor.setValue(target, index);
              } else if (shadowDescriptor
                  instanceof PreviousElementShadowVariableDescriptor<Solution_>) {
                shadowDescriptor.setValue(target, index > 0 ? values.get(index - 1) : null);
              } else if (shadowDescriptor
                  instanceof NextElementShadowVariableDescriptor<Solution_>) {
                shadowDescriptor.setValue(
                    target, index + 1 < values.size() ? values.get(index + 1) : null);
              }
            }
          }
        }
      }
    }

    private boolean isListRelationship(ShadowVariableDescriptor<Solution_> descriptor) {
      return descriptor.getSourceVariableDescriptor() instanceof ListVariableDescriptor<Solution_>
          && (descriptor instanceof InverseRelationShadowVariableDescriptor<Solution_>
              || descriptor instanceof IndexShadowVariableDescriptor<Solution_>
              || descriptor instanceof PreviousElementShadowVariableDescriptor<Solution_>
              || descriptor instanceof NextElementShadowVariableDescriptor<Solution_>);
    }

    public void processDeclarativeVariables() {
      // Recheck dependencies introduced by repaired relationships and build all edges afresh.
      DefaultShadowVariableSessionFactory.buildGraph(
              graphDescriptor.assertingNoReferencedMissingEntities())
          .updateChanged();
    }

    public void processCascadingVariables() {
      if (solutionDescriptor.getListVariableDescriptor() == null) {
        return;
      }
      var descriptors =
          solutionDescriptor.getEntityDescriptors().stream()
              .flatMap(
                  descriptor ->
                      descriptor.getDeclaredCascadingUpdateShadowVariableDescriptors().stream())
              .distinct()
              .toList();
      if (descriptors.isEmpty()) {
        return;
      }
      var orderedEntities = new ArrayList<Object>();
      var visited = new IdentityHashMap<Object, Boolean>();
      // List order, not argument order, determines when a predecessor's cascade is available.
      for (var owner : entities) {
        for (var variableDescriptor : owner.entityDescriptor().getGenuineVariableDescriptorList()) {
          if (variableDescriptor instanceof ListVariableDescriptor<Solution_> listDescriptor) {
            for (var element : listDescriptor.getValue(owner.entity())) {
              if (entityDescriptors.containsKey(element)
                  && visited.put(element, Boolean.TRUE) == null) {
                orderedEntities.add(element);
              }
            }
          }
        }
      }
      // Unassigned supplied elements also need their cascades refreshed.
      for (var entity : entities) {
        if (visited.put(entity.entity(), Boolean.TRUE) == null) {
          orderedEntities.add(entity.entity());
        }
      }
      try (var scoreDirector = new InternalScoreDirector.Builder<>(solutionDescriptor).build()) {
        for (var entity : orderedEntities) {
          for (var descriptor : descriptors) {
            if (descriptor.getEntityDescriptor().matchesEntity(entity)) {
              descriptor.update(scoreDirector, entity);
            }
          }
        }
      }
    }
  }

  private static class InternalScoreDirectorFactory<Solution_, Score_ extends Score<Score_>>
      extends AbstractScoreDirectorFactory<
          Solution_, Score_, InternalScoreDirectorFactory<Solution_, Score_>> {

    public InternalScoreDirectorFactory(
        SolutionDescriptor<Solution_> solutionDescriptor, EnvironmentMode globalEnvironmentMode) {
      super(solutionDescriptor, globalEnvironmentMode);
    }

    /**
     * Score directors are built directly through {@link InternalScoreDirector.Builder}, never
     * through this factory; the inherited no-arg variant funnels into this one.
     */
    @Override
    public AbstractScoreDirector.AbstractScoreDirectorBuilder<Solution_, Score_, ?, ?>
        createScoreDirectorBuilder(EnvironmentMode environmentMode) {
      throw new UnsupportedOperationException();
    }
  }

  @NullMarked
  private static class InternalScoreDirector<Solution_, Score_ extends Score<Score_>>
      extends AbstractScoreDirector<
          Solution_, Score_, InternalScoreDirectorFactory<Solution_, Score_>> {

    private InternalScoreDirector(Builder<Solution_, Score_> builder) {
      super(builder);
    }

    @Override
    public void setWorkingSolutionWithoutUpdatingShadows(Solution_ workingSolution) {
      super.setWorkingSolutionWithoutUpdatingShadows(workingSolution, ignore -> {});
    }

    @Override
    public InnerScore<Score_> innerCalculateScore() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Map<ConstraintRef, ConstraintMatchTotal<Score_>> getConstraintMatchTotalMap() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean requiresFlushing() {
      throw new UnsupportedOperationException();
    }

    @NullMarked
    public static final class Builder<Solution_, Score_ extends Score<Score_>>
        extends AbstractScoreDirectorBuilder<
            Solution_,
            Score_,
            InternalScoreDirectorFactory<Solution_, Score_>,
            InternalScoreDirector.Builder<Solution_, Score_>> {

      public Builder(SolutionDescriptor<Solution_> solutionDescriptor) {
        // We use PHASE_ASSERT by default
        super(
            new InternalScoreDirectorFactory<>(solutionDescriptor, EnvironmentMode.PHASE_ASSERT),
            EnvironmentMode.PHASE_ASSERT);
        withConstraintMatchPolicy(DISABLED);
        withLookUpEnabled(false);
        withExpectShadowVariablesInCorrectState(false);
      }

      @Override
      public InternalScoreDirector<Solution_, Score_> build() {
        return new InternalScoreDirector<>(this);
      }
    }
  }

  private record EntityWithDescriptor<Solution_>(
      Object entity, EntityDescriptor<Solution_> entityDescriptor) {}
}
