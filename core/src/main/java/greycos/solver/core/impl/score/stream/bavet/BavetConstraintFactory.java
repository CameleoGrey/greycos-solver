package greycos.solver.core.impl.score.stream.bavet;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import greycos.solver.core.api.score.stream.ConstraintStream;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.api.score.stream.PrecomputeFactory;
import greycos.solver.core.api.score.stream.uni.UniConstraintStream;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.bavet.common.BavetAbstractConstraintStream;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.bavet.bi.BavetAbstractBiConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.bi.BavetPrecomputeBiConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.common.ConstraintNodeBuildHelper;
import greycos.solver.core.impl.score.stream.bavet.common.bridge.BavetAftBridgeBiConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.common.bridge.BavetAftBridgeQuadConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.common.bridge.BavetAftBridgeTriConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.common.bridge.BavetAftBridgeUniConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.quad.BavetAbstractQuadConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.quad.BavetPrecomputeQuadConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.tri.BavetAbstractTriConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.tri.BavetPrecomputeTriConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.uni.BavetAbstractUniConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.uni.BavetForEachUniConstraintStream;
import greycos.solver.core.impl.score.stream.bavet.uni.BavetPrecomputeUniConstraintStream;
import greycos.solver.core.impl.score.stream.common.ForEachFilteringCriteria;
import greycos.solver.core.impl.score.stream.common.InnerConstraintFactory;
import greycos.solver.core.impl.score.stream.common.RetrievalSemantics;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class BavetConstraintFactory<Solution_>
    extends InnerConstraintFactory<Solution_, BavetConstraint<Solution_>> {

  private final SolutionDescriptor<Solution_> solutionDescriptor;
  private final EnvironmentMode environmentMode;
  private final Map<ForEachKey, UniConstraintStream<?>> forEachStreamMap = new HashMap<>();

  private record ForEachKey(
      Class<?> sourceClass,
      ForEachFilteringCriteria criteria,
      RetrievalSemantics retrievalSemantics) {}

  private final Map<
          BavetAbstractConstraintStream<Solution_>, BavetAbstractConstraintStream<Solution_>>
      sharingStreamMap = new HashMap<>(256);

  public BavetConstraintFactory(
      SolutionDescriptor<Solution_> solutionDescriptor, EnvironmentMode environmentMode) {
    this.solutionDescriptor = solutionDescriptor;
    this.environmentMode = Objects.requireNonNull(environmentMode);
  }

  public <Stream_ extends BavetAbstractConstraintStream<Solution_>> Stream_ share(Stream_ stream) {
    return share(stream, t -> {});
  }

  /**
   * Enables node sharing. If a constraint already exists in this factory, it replaces it with the
   * old copy. {@link BavetAbstractConstraintStream} implement equals/hashcode ignoring child
   * streams.
   *
   * <p>{@link BavetConstraintSessionFactory#buildSession(Object, ConsistencyTracker,
   * ConstraintMatchPolicy, boolean)} needs this to happen for all streams.
   *
   * <p>This must be called before the stream receives child streams.
   *
   * @param stream never null
   * @param consumer never null
   * @param <Stream_> the {@link BavetAbstractConstraintStream} subclass
   * @return never null
   */
  public <Stream_ extends BavetAbstractConstraintStream<Solution_>> Stream_ share(
      Stream_ stream, Consumer<Stream_> consumer) {
    var out =
        (Stream_)
            sharingStreamMap.computeIfAbsent(
                stream,
                k -> {
                  consumer.accept(stream);
                  return stream;
                });
    out.addLocationSet(stream.getLocationSet());
    return out;
  }

  // ************************************************************************
  // from
  // ************************************************************************
  // Required for node sharing, since using a lambda will create different instances
  private record ForEachFilteringCriteriaPredicateFunction<Solution_, A>(
      EntityDescriptor<Solution_> entityDescriptor, ForEachFilteringCriteria criteria)
      implements Function<ConstraintNodeBuildHelper<Solution_, ?>, Predicate<A>> {
    public @Nullable Predicate<A> apply(ConstraintNodeBuildHelper<Solution_, ?> helper) {
      return helper.getForEachPredicateForEntityDescriptorAndCriteria(entityDescriptor, criteria);
    }
  }

  private record RuntimeForEachFilteringCriteriaPredicateFunction<Solution_, A>(
      SolutionDescriptor<Solution_> solutionDescriptor, ForEachFilteringCriteria criteria)
      implements Function<ConstraintNodeBuildHelper<Solution_, ?>, Predicate<A>> {
    @Override
    public Predicate<A> apply(ConstraintNodeBuildHelper<Solution_, ?> helper) {
      // Resolve once per runtime class, while keeping each session's consistency predicates
      // private.
      Map<Class<?>, Predicate<A>> predicateMap = new HashMap<>();
      return value -> {
        var runtimeClass = value.getClass();
        // Avoid allocating a capturing computeIfAbsent resolver on every cache hit.
        var predicate = predicateMap.get(runtimeClass);
        if (predicate == null) {
          var entityDescriptor = solutionDescriptor.findEntityDescriptor(runtimeClass);
          predicate =
              entityDescriptor == null
                  ? null
                  : helper.getForEachPredicateForEntityDescriptorAndCriteria(
                      entityDescriptor, criteria);
          if (predicate == null) {
            predicate = ignored -> true;
          }
          predicateMap.put(runtimeClass, predicate);
        }
        return predicate.test(value);
      };
    }
  }

  private <A> UniConstraintStream<A> forEachForCriteria(
      Class<A> sourceClass, ForEachFilteringCriteria criteria) {
    return forEachForCriteria(sourceClass, criteria, RetrievalSemantics.STANDARD);
  }

  private <A> UniConstraintStream<A> forEachForCriteria(
      Class<A> sourceClass,
      ForEachFilteringCriteria criteria,
      RetrievalSemantics retrievalSemantics) {
    var key = new ForEachKey(sourceClass, criteria, retrievalSemantics);
    var cachedStream = (UniConstraintStream<A>) forEachStreamMap.get(key);
    if (cachedStream != null) {
      return cachedStream;
    }
    // Recursive calls create owner streams; avoid computeIfAbsent on the same map.
    var stream = buildForEachForCriteria(sourceClass, criteria, retrievalSemantics);
    forEachStreamMap.put(key, stream);
    return stream;
  }

  private <A> UniConstraintStream<A> buildForEachForCriteria(
      Class<A> sourceClass,
      ForEachFilteringCriteria criteria,
      RetrievalSemantics retrievalSemantics) {
    assertValidFromType(sourceClass);
    var entityDescriptor = solutionDescriptor.findEntityDescriptor(sourceClass);
    if (criteria == ForEachFilteringCriteria.ALL) {
      return share(
          new BavetForEachUniConstraintStream<>(this, sourceClass, null, retrievalSemantics));
    }
    var requiresRuntimeDispatch =
        solutionDescriptor.getEntityDescriptors().stream()
            .anyMatch(
                descriptor ->
                    descriptor != entityDescriptor
                        && sourceClass.isAssignableFrom(descriptor.getEntityClass()));
    var membershipDescriptors =
        solutionDescriptor.getListVariableDescriptorList().stream()
            .filter(
                descriptor ->
                    descriptor.acceptsValueType(sourceClass)
                        || sourceClass.isAssignableFrom(descriptor.getElementType()))
            .filter(descriptor -> descriptor.getInverseRelationShadowVariableDescriptor() == null)
            .toList();
    // Keep the shared consistent source used by the traditional no-inverse, shadow-only model.
    // A value with genuine basic variables needs the stricter predicate as well as membership.
    var sourceCriteria =
        criteria == ForEachFilteringCriteria.ASSIGNED_AND_CONSISTENT
                && !requiresRuntimeDispatch
                && entityDescriptor != null
                && !entityDescriptor.hasAnyBasicVariables()
                && membershipDescriptors.size() == 1
                && membershipDescriptors.getFirst().acceptsValueType(sourceClass)
            ? ForEachFilteringCriteria.CONSISTENT
            : criteria;
    Function<ConstraintNodeBuildHelper<Solution_, ?>, Predicate<A>> predicateFunction = null;
    if (requiresRuntimeDispatch) {
      predicateFunction =
          new RuntimeForEachFilteringCriteriaPredicateFunction<>(
              solutionDescriptor, sourceCriteria);
    } else if (entityDescriptor != null) {
      predicateFunction =
          new ForEachFilteringCriteriaPredicateFunction<>(entityDescriptor, sourceCriteria);
    }
    BavetAbstractUniConstraintStream<Solution_, A> source =
        share(
            new BavetForEachUniConstraintStream<>(
                this, sourceClass, predicateFunction, retrievalSemantics));
    if (criteria != ForEachFilteringCriteria.ASSIGNED_AND_CONSISTENT) {
      return source;
    }
    if (membershipDescriptors.isEmpty()) {
      return source;
    }
    if (membershipDescriptors.size() == 1
        && membershipDescriptors.getFirst().acceptsValueType(sourceClass)) {
      var descriptor = membershipDescriptors.getFirst();
      return source.ifExistsForEachAssignment(
          forEachUnfiltered((Class<Object>) descriptor.getEntityDescriptor().getEntityClass()),
          Joiners.filtering(descriptor.<A>getInListPredicate()));
    }
    // The branches are disjoint by runtime value type. Owner updates must flow through ifExists;
    // consulting only a list-state predicate would leave values without inverse shadows stale.
    var result =
        source.filter(
            value ->
                membershipDescriptors.stream()
                    .noneMatch(descriptor -> descriptor.acceptsValueType(value.getClass())));
    for (var descriptor : membershipDescriptors) {
      var branchSource =
          (BavetAbstractUniConstraintStream<Solution_, A>)
              source.filter(value -> descriptor.acceptsValueType(value.getClass()));
      var branch =
          branchSource.ifExistsForEachAssignment(
              forEachUnfiltered((Class<Object>) descriptor.getEntityDescriptor().getEntityClass()),
              Joiners.filtering(descriptor.<A>getInListPredicate()));
      result = result.concat(branch);
    }
    return result;
  }

  @Override
  public <A> UniConstraintStream<A> forEach(Class<A> sourceClass) {
    return forEachForCriteria(sourceClass, ForEachFilteringCriteria.ASSIGNED_AND_CONSISTENT);
  }

  @Override
  public <A> UniConstraintStream<A> forEachIncludingUnassigned(Class<A> sourceClass) {
    return forEachForCriteria(sourceClass, ForEachFilteringCriteria.CONSISTENT);
  }

  @Override
  public <A> UniConstraintStream<A> forEachUnfiltered(Class<A> sourceClass) {
    return forEachForCriteria(sourceClass, ForEachFilteringCriteria.ALL);
  }

  <A> UniConstraintStream<A> forEachUnfilteredPrecomputed(Class<A> sourceClass) {
    return forEachForCriteria(
        sourceClass, ForEachFilteringCriteria.ALL, RetrievalSemantics.PRECOMPUTE);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Stream_ extends ConstraintStream> Stream_ precompute(
      Function<PrecomputeFactory, Stream_> precomputeSupplier) {
    var bavetStream =
        Objects.requireNonNull(precomputeSupplier.apply(new BavetStaticDataFactory<>(this)));
    switch (bavetStream) {
      case BavetAbstractUniConstraintStream<?, ?> uniStream -> {
        var out =
            new BavetPrecomputeUniConstraintStream<>(
                this, (BavetAbstractUniConstraintStream<Solution_, ?>) uniStream);
        return (Stream_)
            share(new BavetAftBridgeUniConstraintStream<>(this, out), out::setAftBridge);
      }
      case BavetAbstractBiConstraintStream<?, ?, ?> biStream -> {
        var out =
            new BavetPrecomputeBiConstraintStream<>(
                this, (BavetAbstractBiConstraintStream<Solution_, ?, ?>) biStream);
        return (Stream_)
            share(new BavetAftBridgeBiConstraintStream<>(this, out), out::setAftBridge);
      }
      case BavetAbstractTriConstraintStream<?, ?, ?, ?> triStream -> {
        var out =
            new BavetPrecomputeTriConstraintStream<>(
                this, (BavetAbstractTriConstraintStream<Solution_, ?, ?, ?>) triStream);
        return (Stream_)
            share(new BavetAftBridgeTriConstraintStream<>(this, out), out::setAftBridge);
      }
      case BavetAbstractQuadConstraintStream<?, ?, ?, ?, ?> quadStream -> {
        var out =
            new BavetPrecomputeQuadConstraintStream<>(
                this, (BavetAbstractQuadConstraintStream<Solution_, ?, ?, ?, ?>) quadStream);
        return (Stream_)
            share(new BavetAftBridgeQuadConstraintStream<>(this, out), out::setAftBridge);
      }
      default ->
          throw new IllegalStateException(
              "impossible state: the supplier (%s) returned a stream (%s) that not an instance of any Bavet ConstraintStream"
                  .formatted(precomputeSupplier, bavetStream));
    }
  }

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  @Override
  public SolutionDescriptor<Solution_> getSolutionDescriptor() {
    return solutionDescriptor;
  }

  public EnvironmentMode getEnvironmentMode() {
    return environmentMode;
  }
}
