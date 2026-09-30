package greycos.solver.core.impl.score.stream.collector;

import java.math.BigInteger;

import greycos.solver.core.impl.score.FloatingPointMath;

import org.jspecify.annotations.Nullable;

/**
 * A reusable contribution handle. The group stores an exact sum of binary units; each handle
 * remembers the represented contribution to retract, independently of later tuple mutations.
 */
public abstract class AbstractFloatingPointSlot {

  public static final class State {
    private BigInteger sum = BigInteger.ZERO;
    private long count;

    public float sumFloat() {
      return FloatingPointMath.fromFloatUnits(sum);
    }

    public double sumDouble() {
      return FloatingPointMath.fromDoubleUnits(sum);
    }

    public @Nullable Float averageFloat() {
      return count == 0L ? null : FloatingPointMath.averageFloatUnits(sum, count);
    }

    public @Nullable Double averageDouble() {
      return count == 0L ? null : FloatingPointMath.averageDoubleUnits(sum, count);
    }
  }

  private final State state;
  private BigInteger cachedInput;

  protected AbstractFloatingPointSlot(State state) {
    this.state = state;
  }

  protected final void addMapped(float input) {
    addUnits(FloatingPointMath.units(input));
  }

  protected final void addMapped(double input) {
    addUnits(FloatingPointMath.units(input));
  }

  private void addUnits(BigInteger input) {
    if (cachedInput != null) {
      throw new IllegalStateException("The floating collector handle already has an active value.");
    }
    long nextCount = Math.incrementExact(state.count);
    var nextSum = state.sum.add(input);
    state.sum = nextSum;
    state.count = nextCount;
    cachedInput = input;
  }

  protected final void replaceWithMapped(float input) {
    replaceWithUnits(FloatingPointMath.units(input));
  }

  protected final void replaceWithMapped(double input) {
    replaceWithUnits(FloatingPointMath.units(input));
  }

  private void replaceWithUnits(BigInteger input) {
    requireActive();
    if (cachedInput.equals(input)) {
      return;
    }
    state.sum = state.sum.subtract(cachedInput).add(input);
    cachedInput = input;
  }

  protected final void removeMapped() {
    requireActive();
    state.sum = state.sum.subtract(cachedInput);
    state.count--;
    cachedInput = null;
  }

  private void requireActive() {
    if (cachedInput == null) {
      throw new IllegalStateException("The floating collector handle has no active value.");
    }
  }
}
