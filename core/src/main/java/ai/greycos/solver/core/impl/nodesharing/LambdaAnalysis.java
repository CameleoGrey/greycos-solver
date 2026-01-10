package ai.greycos.solver.core.impl.nodesharing;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Analysis result containing lambdas suitable for sharing in a ConstraintProvider class.
 *
 * <p>Why: Transformation needs metadata about which lambdas can be deduplicated.
 * How: Groups lambdas by functional equivalence and filters single-occurrence lambdas.
 * What: Provides access to shareable lambda groups for bytecode transformation.
 */
public final class LambdaAnalysis {

  private final Map<LambdaKey, List<LambdaInfo>> shareableLambdas;

  public LambdaAnalysis(Map<LambdaKey, List<LambdaInfo>> shareableLambdas) {
    this.shareableLambdas = Map.copyOf(shareableLambdas);
  }

  public Map<LambdaKey, List<LambdaInfo>> getShareableLambdas() {
    return shareableLambdas;
  }

  public List<LambdaInfo> getAllLambdas() {
    return shareableLambdas.values().stream().flatMap(List::stream).toList();
  }

  public boolean hasShareableLambdas() {
    return !shareableLambdas.isEmpty();
  }

  public int getShareableLambdaGroupCount() {
    return shareableLambdas.size();
  }

  public int getShareableLambdaCount() {
    return shareableLambdas.values().stream().mapToInt(List::size).sum();
  }

  public List<LambdaInfo> getLambdasForKey(LambdaKey key) {
    return shareableLambdas.getOrDefault(key, Collections.emptyList());
  }
}
