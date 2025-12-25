package ai.greycos.solver.core.impl.nodesharing;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Result of analyzing a ConstraintProvider class for lambda expressions.
 *
 * <p>Contains all lambdas found in the class, grouped by functional equivalence. Lambdas that
 * appear only once are filtered out as they provide no sharing benefit.
 */
public final class LambdaAnalysis {

  private final Map<LambdaKey, List<LambdaInfo>> shareableLambdas;

  public LambdaAnalysis(Map<LambdaKey, List<LambdaInfo>> shareableLambdas) {
    this.shareableLambdas = Map.copyOf(shareableLambdas);
  }

  /**
   * Returns all lambdas that can be shared (appear at least twice).
   *
   * @return unmodifiable map of LambdaKey to list of LambdaInfo
   */
  public Map<LambdaKey, List<LambdaInfo>> getShareableLambdas() {
    return shareableLambdas;
  }

  /**
   * Returns all lambda information (flattened).
   *
   * @return unmodifiable list of all shareable LambdaInfo
   */
  public List<LambdaInfo> getAllLambdas() {
    return shareableLambdas.values().stream().flatMap(List::stream).toList();
  }

  /**
   * Checks if there are any shareable lambdas.
   *
   * @return true if there is at least one lambda that appears twice or more
   */
  public boolean hasShareableLambdas() {
    return !shareableLambdas.isEmpty();
  }

  /**
   * Gets the number of lambda groups that can be shared.
   *
   * @return number of unique lambda types that appear at least twice
   */
  public int getShareableLambdaGroupCount() {
    return shareableLambdas.size();
  }

  /**
   * Gets the total number of lambda occurrences that can be shared.
   *
   * @return total count of all lambda instances that can be shared
   */
  public int getShareableLambdaCount() {
    return shareableLambdas.values().stream().mapToInt(List::size).sum();
  }

  /**
   * Gets all lambdas for a specific key.
   *
   * @param key the LambdaKey to look up
   * @return unmodifiable list of LambdaInfo for this key, or empty list if not found
   */
  public List<LambdaInfo> getLambdasForKey(LambdaKey key) {
    return shareableLambdas.getOrDefault(key, Collections.emptyList());
  }
}
