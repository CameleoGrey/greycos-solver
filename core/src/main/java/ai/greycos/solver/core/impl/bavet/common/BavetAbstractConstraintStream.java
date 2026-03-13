package ai.greycos.solver.core.impl.bavet.common;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.constraint.ConstraintRef;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintStream;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraint;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import ai.greycos.solver.core.impl.score.stream.bavet.common.BavetScoringConstraintStream;
import ai.greycos.solver.core.impl.score.stream.bavet.common.ConstraintNodeBuildHelper;
import ai.greycos.solver.core.impl.score.stream.common.AbstractConstraintStream;
import ai.greycos.solver.core.impl.score.stream.common.RetrievalSemantics;
import ai.greycos.solver.core.impl.score.stream.common.ScoreImpactType;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class BavetAbstractConstraintStream<Solution_>
    extends AbstractConstraintStream<Solution_> implements BavetStream {

  private static final Set<String> CONSTRAINT_STREAM_API_METHOD_SET =
      Stream.of(
              ai.greycos.solver.core.api.score.stream.uni.UniConstraintStream.class,
              ai.greycos.solver.core.api.score.stream.bi.BiConstraintStream.class,
              ai.greycos.solver.core.api.score.stream.tri.TriConstraintStream.class,
              ai.greycos.solver.core.api.score.stream.quad.QuadConstraintStream.class)
          .flatMap(clazz -> Arrays.stream(clazz.getMethods()))
          .map(Method::getName)
          .collect(Collectors.toUnmodifiableSet());

  protected final BavetConstraintFactory<Solution_> constraintFactory;
  protected final BavetAbstractConstraintStream<Solution_> parent;
  protected final List<BavetAbstractConstraintStream<Solution_>> childStreamList =
      new ArrayList<>(2);
  protected final SortedSet<ConstraintNodeLocation> streamLocationSet;

  protected BavetAbstractConstraintStream(
      BavetConstraintFactory<Solution_> constraintFactory,
      BavetAbstractConstraintStream<Solution_> parent) {
    super(parent.getRetrievalSemantics());
    this.constraintFactory = constraintFactory;
    this.parent = parent;
    this.streamLocationSet = new TreeSet<>();
    streamLocationSet.add(determineStreamLocation());
  }

  protected BavetAbstractConstraintStream(
      BavetConstraintFactory<Solution_> constraintFactory, RetrievalSemantics retrievalSemantics) {
    super(retrievalSemantics);
    this.constraintFactory = constraintFactory;
    this.parent = null;
    this.streamLocationSet = new TreeSet<>();
    streamLocationSet.add(determineStreamLocation());
  }

  private static ConstraintNodeLocation determineStreamLocation() {
    return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        .walk(
            stack ->
                stack
                    .dropWhile(
                        frame ->
                            !ConstraintStream.class.isAssignableFrom(frame.getDeclaringClass())
                                && !CONSTRAINT_STREAM_API_METHOD_SET.contains(
                                    frame.getMethodName()))
                    .dropWhile(
                        frame ->
                            ConstraintStream.class.isAssignableFrom(frame.getDeclaringClass())
                                || ConstraintFactory.class.isAssignableFrom(
                                    frame.getDeclaringClass()))
                    .map(
                        frame ->
                            new ConstraintNodeLocation(
                                frame.getClassName(), frame.getMethodName(), frame.getLineNumber()))
                    .findFirst()
                    .orElseGet(ConstraintNodeLocation::unknown));
  }

  @Override
  public SortedSet<ConstraintNodeLocation> getLocationSet() {
    return streamLocationSet;
  }

  public void addLocationSet(Set<ConstraintNodeLocation> locationSet) {
    streamLocationSet.addAll(locationSet);
  }

  /**
   * Whether the stream guarantees that no two tuples it produces will ever have the same set of
   * facts. Streams which can prove that they either do or do not produce unique tuples should
   * override this method.
   *
   * @return delegates to {@link #getParent()} if not null, otherwise false
   */
  public boolean guaranteesDistinct() {
    if (parent != null) {
      // It is generally safe to take this from the parent; if the stream disagrees, it may
      // override.
      return parent.guaranteesDistinct();
    } else { // Streams need to explicitly opt-in by overriding this method.
      return false;
    }
  }

  protected <Score_ extends Score<Score_>> Constraint buildConstraint(
      String constraintPackage,
      String constraintName,
      String description,
      String constraintGroup,
      Score_ constraintWeight,
      ScoreImpactType impactType,
      Object justificationFunction,
      Object indictedObjectsMapping,
      BavetScoringConstraintStream<Solution_> stream) {
    var resolvedJustificationMapping =
        Objects.requireNonNullElseGet(justificationFunction, this::getDefaultJustificationMapping);
    var resolvedIndictedObjectsMapping =
        Objects.requireNonNullElseGet(
            indictedObjectsMapping, this::getDefaultIndictedObjectsMapping);
    var isConstraintWeightConfigurable = constraintWeight == null;
    var constraintRef = ConstraintRef.of(constraintName);
    var constraint =
        new BavetConstraint<>(
            constraintFactory,
            constraintRef,
            description,
            constraintGroup,
            isConstraintWeightConfigurable ? null : constraintWeight,
            impactType,
            resolvedJustificationMapping,
            resolvedIndictedObjectsMapping,
            stream);
    stream.setConstraint(constraint);
    return constraint;
  }

  public final <Stream_ extends BavetAbstractConstraintStream<Solution_>> Stream_ shareAndAddChild(
      Stream_ stream) {
    return constraintFactory.share(stream, childStreamList::add);
  }

  public void collectActiveConstraintStreams(
      Set<BavetAbstractConstraintStream<Solution_>> constraintStreamSet) {
    if (parent == null) { // Maybe a join/ifExists/forEach forgot to override this?
      throw new IllegalStateException(
          "Impossible state: the stream (%s) does not have a parent.".formatted(this));
    }
    parent.collectActiveConstraintStreams(constraintStreamSet);
    constraintStreamSet.add(this);
  }

  /**
   * Returns the stream which first produced the tuple that this stream operates on. If a stream
   * does not have a single parent nor is it a source, it is expected to override this method.
   *
   * @return this if {@link TupleSource}, otherwise parent's tuple source.
   */
  public BavetAbstractConstraintStream<Solution_> getTupleSource() {
    if (this instanceof TupleSource) {
      return this;
    } else if (parent == null) { // Maybe some stream forgot to override this?
      throw new IllegalStateException(
          "Impossible state: the stream (%s) does not have a parent.".formatted(this));
    }
    return parent.getTupleSource();
  }

  public abstract <Score_ extends Score<Score_>> void buildNode(
      ConstraintNodeBuildHelper<Solution_, Score_> buildHelper);

  protected void assertEmptyChildStreamList() {
    if (!childStreamList.isEmpty()) {
      throw new IllegalStateException(
          "Impossible state: the stream (%s) has a non-empty childStreamList (%s)."
              .formatted(this, childStreamList));
    }
  }

  @Override
  public @NonNull BavetConstraintFactory<Solution_> getConstraintFactory() {
    return constraintFactory;
  }

  /**
   * @return null for join/ifExists nodes, which have left and right parents instead; also null for
   *     forEach node, which has no parent.
   */
  @SuppressWarnings("unchecked")
  @Override
  public final BavetAbstractConstraintStream<Solution_> getParent() {
    return parent;
  }

  public final List<BavetAbstractConstraintStream<Solution_>> getChildStreamList() {
    return childStreamList;
  }
}
