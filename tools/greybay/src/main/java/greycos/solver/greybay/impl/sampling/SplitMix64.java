package greycos.solver.greybay.impl.sampling;

/** SplitMix64 version one; no Gaussian cache or JVM-provided generator state. */
public final class SplitMix64 {
  private long state;

  public SplitMix64(long state) {
    this.state = state;
  }

  public long state() {
    return state;
  }

  public long nextLong() {
    long bits = (state += 0x9e3779b97f4a7c15L);
    bits = (bits ^ (bits >>> 30)) * 0xbf58476d1ce4e5b9L;
    bits = (bits ^ (bits >>> 27)) * 0x94d049bb133111ebL;
    return bits ^ (bits >>> 31);
  }

  public double nextDouble() {
    return (nextLong() >>> 11) * 0x1.0p-53;
  }
}
