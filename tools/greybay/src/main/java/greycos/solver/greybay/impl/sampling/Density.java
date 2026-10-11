package greycos.solver.greybay.impl.sampling;

interface Density {
  Object sample(SplitMix64 random);

  double logProbability(Object value);
}
