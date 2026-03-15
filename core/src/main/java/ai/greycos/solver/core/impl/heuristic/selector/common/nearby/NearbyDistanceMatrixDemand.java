package ai.greycos.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

import ai.greycos.solver.core.impl.cotwin.variable.supply.Demand;
import ai.greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Supply demand for sharing nearby distance matrices across equal nearby selectors.
 *
 * <p>The {@link SupplyManager} reuses the same {@link NearbyDistanceMatrix} instance for equal
 * demand instances, avoiding repeated matrix construction.
 */
public final class NearbyDistanceMatrixDemand<Origin_, Destination_>
    implements Demand<NearbyDistanceMatrix<Origin_, Destination_>> {

  private final @NonNull NearbyDistanceMeter<Origin_, Destination_> nearbyDistanceMeter;
  private final @Nullable NearbyRandom nearbyRandom;
  private final int maxNearbySortSize;
  private final boolean strictDestinationSize;
  private final @NonNull Object destinationSelectorKey;
  private final @NonNull Object originSelectorKey;
  private final @NonNull String demandType;
  private final @NonNull IntSupplier originSizeSupplier;
  private final @NonNull Function<Origin_, Iterator<Destination_>> destinationIteratorProvider;
  private final @NonNull ToIntFunction<Origin_> destinationSizeFunction;

  public NearbyDistanceMatrixDemand(
      @NonNull NearbyDistanceMeter<Origin_, Destination_> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      int maxNearbySortSize,
      boolean strictDestinationSize,
      @NonNull Object destinationSelectorKey,
      @NonNull Object originSelectorKey,
      @NonNull String demandType,
      @NonNull IntSupplier originSizeSupplier,
      @NonNull Function<Origin_, Iterator<Destination_>> destinationIteratorProvider,
      @NonNull ToIntFunction<Origin_> destinationSizeFunction) {
    this.nearbyDistanceMeter = nearbyDistanceMeter;
    this.nearbyRandom = nearbyRandom;
    this.maxNearbySortSize = maxNearbySortSize;
    this.strictDestinationSize = strictDestinationSize;
    this.destinationSelectorKey = destinationSelectorKey;
    this.originSelectorKey = originSelectorKey;
    this.demandType = demandType;
    this.originSizeSupplier = originSizeSupplier;
    this.destinationIteratorProvider = destinationIteratorProvider;
    this.destinationSizeFunction = destinationSizeFunction;
  }

  @Override
  public NearbyDistanceMatrix<Origin_, Destination_> createExternalizedSupply(
      SupplyManager supplyManager) {
    int originSize = originSizeSupplier.getAsInt();
    if (originSize < 0) {
      throw new IllegalStateException("The originSize (" + originSize + ") must be non-negative.");
    }
    return new NearbyDistanceMatrix<>(
        nearbyDistanceMeter,
        originSize,
        destinationIteratorProvider,
        destinationSizeFunction,
        maxNearbySortSize,
        strictDestinationSize);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NearbyDistanceMatrixDemand<?, ?> that)) {
      return false;
    }
    return maxNearbySortSize == that.maxNearbySortSize
        && strictDestinationSize == that.strictDestinationSize
        && Objects.equals(nearbyDistanceMeter, that.nearbyDistanceMeter)
        && Objects.equals(nearbyRandom, that.nearbyRandom)
        && Objects.equals(destinationSelectorKey, that.destinationSelectorKey)
        && Objects.equals(originSelectorKey, that.originSelectorKey)
        && Objects.equals(demandType, that.demandType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        nearbyDistanceMeter,
        nearbyRandom,
        maxNearbySortSize,
        strictDestinationSize,
        destinationSelectorKey,
        originSelectorKey,
        demandType);
  }
}
