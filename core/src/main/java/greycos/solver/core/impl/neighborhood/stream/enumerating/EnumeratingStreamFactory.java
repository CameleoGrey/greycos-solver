package greycos.solver.core.impl.neighborhood.stream.enumerating;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.neighborhood.stream.enumerating.common.AbstractDataset;
import greycos.solver.core.impl.neighborhood.stream.enumerating.common.AbstractEnumeratingStream;
import greycos.solver.core.impl.neighborhood.stream.enumerating.common.TerminalEnumeratingStream;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.AbstractUniEnumeratingStream;
import greycos.solver.core.impl.neighborhood.stream.enumerating.uni.ForEachIncludingPinnedEnumeratingStream;
import greycos.solver.core.impl.score.director.SessionContext;
import greycos.solver.core.preview.api.neighborhood.stream.enumerating.UniEnumeratingStream;
import greycos.solver.core.preview.api.neighborhood.stream.function.UniNeighborhoodsPredicate;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class EnumeratingStreamFactory<Solution_> {

  private final SolutionDescriptor<Solution_> solutionDescriptor;
  private final EnvironmentMode environmentMode;
  private final Map<Class<?>, UniNeighborhoodsPredicate<Solution_, Object>> movablePredicateMap =
      new HashMap<>();
  private final Map<AbstractEnumeratingStream<Solution_>, AbstractEnumeratingStream<Solution_>>
      sharingStreamMap = new HashMap<>(256);

  public EnumeratingStreamFactory(
      SolutionDescriptor<Solution_> solutionDescriptor, EnvironmentMode environmentMode) {
    this.solutionDescriptor = Objects.requireNonNull(solutionDescriptor);
    this.environmentMode = Objects.requireNonNull(environmentMode);
  }

  public <A> UniEnumeratingStream<Solution_, A> forEachNonDiscriminating(
      Class<A> sourceClass, boolean includeNull) {
    assertValidForEachType(sourceClass);
    return share(new ForEachIncludingPinnedEnumeratingStream<>(this, sourceClass, includeNull));
  }

  public <A> UniEnumeratingStream<Solution_, A> forEachExcludingPinned(
      Class<A> sourceClass, boolean includeNull) {
    assertValidForEachType(sourceClass);
    var entityDescriptor = solutionDescriptor.findEntityDescriptor(sourceClass);
    var runtimeDispatch =
        solutionDescriptor.getEntityDescriptors().stream()
            .anyMatch(
                descriptor ->
                    descriptor != entityDescriptor
                        && sourceClass.isAssignableFrom(descriptor.getEntityClass()));
    var applicableLists =
        solutionDescriptor.getListVariableDescriptorList().stream()
            .filter(
                descriptor ->
                    descriptor.acceptsValueType(sourceClass)
                        || sourceClass.isAssignableFrom(descriptor.getElementType()))
            .filter(descriptor -> descriptor.supportsPinning())
            .toList();
    if (!runtimeDispatch
        && applicableLists.isEmpty()
        && (entityDescriptor == null || !entityDescriptor.isGenuine())) {
      return forEachNonDiscriminating(sourceClass, includeNull);
    }
    // Cache all composed/runtime predicates; filtering streams share by predicate identity.
    var predicate =
        movablePredicateMap.computeIfAbsent(
            sourceClass,
            ignored -> {
              if (!runtimeDispatch && entityDescriptor != null && entityDescriptor.isGenuine()) {
                return entityDescriptor.getEntityMovablePredicate();
              }
              if (!runtimeDispatch
                  && applicableLists.size() == 1
                  && applicableLists.getFirst().acceptsValueType(sourceClass)
                  && (entityDescriptor == null || !entityDescriptor.isGenuine())) {
                return applicableLists.getFirst().getValueMovablePredicate();
              }
              return (solutionView, value) -> {
                if (value == null) {
                  return true;
                }
                var runtimeEntityDescriptor =
                    solutionDescriptor.findEntityDescriptor(value.getClass());
                if (runtimeEntityDescriptor != null && runtimeEntityDescriptor.isGenuine()) {
                  // A pinned list position does not pin the element's independent basic variables.
                  // List-specific enumeration applies the list descriptor's pinning separately.
                  return runtimeEntityDescriptor
                      .getEntityMovablePredicate()
                      .test(solutionView, value);
                }
                for (var descriptor : applicableLists) {
                  if (descriptor.acceptsValueType(value.getClass())) {
                    return descriptor.getValueMovablePredicate().test(solutionView, value);
                  }
                }
                return true;
              };
            });
    return share(
        (AbstractUniEnumeratingStream<Solution_, A>)
            forEachNonDiscriminating(sourceClass, includeNull)
                .filter((UniNeighborhoodsPredicate) predicate));
  }

  public <A> void assertValidForEachType(Class<A> fromType) {
    var problemFactOrEntityClassSet = solutionDescriptor.getProblemFactOrEntityClassSet();
    /*
     * Need to support the following situations:
     * 1/ FactType == FromType; querying for the declared type.
     * 2/ FromType extends/implements FactType; querying for impl type where declared type is its interface.
     * 3/ FromType super FactType; querying for interface where declared type is its implementation.
     */
    var hasMatchingType =
        problemFactOrEntityClassSet.stream()
            .anyMatch(
                factType ->
                    fromType.isAssignableFrom(factType) || factType.isAssignableFrom(fromType));
    if (!hasMatchingType) {
      var canonicalClassNameList =
          problemFactOrEntityClassSet.stream().map(Class::getCanonicalName).sorted().toList();
      throw new IllegalArgumentException(
          """
          Cannot use class (%s) in an enumerating stream as it is neither the same as, \
          nor a superclass or superinterface of one of planning entities or problem facts.
          Ensure that all forEach(), join(), ifExists() and ifNotExists() building blocks only reference classes \
          assignable from planning entities or problem facts (%s) annotated on the planning solution (%s).\
          """
              .formatted(
                  fromType.getCanonicalName(),
                  canonicalClassNameList,
                  solutionDescriptor.getSolutionClass().getCanonicalName()));
    }
  }

  public <Stream_ extends AbstractEnumeratingStream<Solution_>> Stream_ share(Stream_ stream) {
    return share(stream, t -> {});
  }

  /**
   * Enables node sharing. If a stream already exists in this factory, it replaces it with the old
   * copy. {@link AbstractEnumeratingStream} implement equals/hashcode ignoring child streams.
   *
   * <p>{@link DatasetSessionFactory#buildSession(SessionContext)} needs this to happen for all
   * streams.
   *
   * <p>This must be called before the stream receives child streams.
   *
   * @param stream never null
   * @param consumer never null
   * @param <Stream_> the {@link AbstractEnumeratingStream} subclass
   * @return never null
   */
  @SuppressWarnings("unchecked")
  public <Stream_ extends AbstractEnumeratingStream<Solution_>> Stream_ share(
      Stream_ stream, Consumer<Stream_> consumer) {
    return (Stream_)
        sharingStreamMap.computeIfAbsent(
            stream,
            k -> {
              consumer.accept(stream);
              return stream;
            });
  }

  public SolutionDescriptor<Solution_> getSolutionDescriptor() {
    return solutionDescriptor;
  }

  public EnvironmentMode getEnvironmentMode() {
    return environmentMode;
  }

  @SuppressWarnings("unchecked")
  public List<AbstractDataset<Solution_>> getDatasets() {
    return sharingStreamMap.values().stream()
        .flatMap(
            s -> {
              if (s instanceof TerminalEnumeratingStream<?, ?> terminalStream) {
                return Stream.of((AbstractDataset<Solution_>) terminalStream.getDataset());
              }
              return Stream.empty();
            })
        .collect(Collectors.toList());
  }
}
