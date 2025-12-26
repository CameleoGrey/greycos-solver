package ai.greycos.solver.core.config.localsearch.decider.forager;

import java.util.Map;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;

import ai.greycos.solver.core.config.AbstractConfig;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for a local search forager.
 *
 * <p>A forager is responsible for collecting evaluated moves during a step, deciding when to stop
 * evaluating moves (early termination), and selecting the best move to apply.
 *
 * <p>This configuration supports:
 *
 * <ul>
 *   <li>Built-in foragers configured via {@link #pickEarlyType}
 *   <li>Custom foragers configured via {@link #foragerClass} (enterprise feature)
 * </ul>
 *
 * <p>When using custom foragers, you can inject properties via {@link #customProperties}. Custom
 * properties are set using setter methods on the forager class (e.g., {@code setTopK("5")}).
 *
 * <p><b>Enterprise Feature:</b> Custom forager functionality requires a valid Greycos Enterprise
 * license. Attempting to use a custom forager without a license will result in an {@link
 * UnsupportedOperationException}.
 */
@XmlType(
    propOrder = {
      "pickEarlyType",
      "acceptedCountLimit",
      "finalistPodiumType",
      "breakTieRandomly",
      "foragerClass",
      "customProperties"
    })
public class LocalSearchForagerConfig extends AbstractConfig<LocalSearchForagerConfig> {

  protected LocalSearchPickEarlyType pickEarlyType = null;
  protected Integer acceptedCountLimit = null;
  protected FinalistPodiumType finalistPodiumType = null;
  protected Boolean breakTieRandomly = null;
  protected Class<? extends LocalSearchForager> foragerClass = null;
  protected Map<String, String> customProperties = null;

  public @Nullable LocalSearchPickEarlyType getPickEarlyType() {
    return pickEarlyType;
  }

  public void setPickEarlyType(@Nullable LocalSearchPickEarlyType pickEarlyType) {
    this.pickEarlyType = pickEarlyType;
  }

  public @Nullable Integer getAcceptedCountLimit() {
    return acceptedCountLimit;
  }

  public void setAcceptedCountLimit(@Nullable Integer acceptedCountLimit) {
    this.acceptedCountLimit = acceptedCountLimit;
  }

  public @Nullable FinalistPodiumType getFinalistPodiumType() {
    return finalistPodiumType;
  }

  public void setFinalistPodiumType(@Nullable FinalistPodiumType finalistPodiumType) {
    this.finalistPodiumType = finalistPodiumType;
  }

  public @Nullable Boolean getBreakTieRandomly() {
    return breakTieRandomly;
  }

  public void setBreakTieRandomly(@Nullable Boolean breakTieRandomly) {
    this.breakTieRandomly = breakTieRandomly;
  }

  public @Nullable Class<? extends LocalSearchForager> getForagerClass() {
    return foragerClass;
  }

  public void setForagerClass(@Nullable Class<? extends LocalSearchForager> foragerClass) {
    this.foragerClass = foragerClass;
  }

  public @Nullable Map<String, String> getCustomProperties() {
    return customProperties;
  }

  public void setCustomProperties(@Nullable Map<String, String> customProperties) {
    this.customProperties = customProperties;
  }

  // ************************************************************************
  // With methods
  // ************************************************************************

  public @NonNull LocalSearchForagerConfig withPickEarlyType(
      @NonNull LocalSearchPickEarlyType pickEarlyType) {
    this.pickEarlyType = pickEarlyType;
    return this;
  }

  public @NonNull LocalSearchForagerConfig withAcceptedCountLimit(int acceptedCountLimit) {
    this.acceptedCountLimit = acceptedCountLimit;
    return this;
  }

  public @NonNull LocalSearchForagerConfig withFinalistPodiumType(
      @NonNull FinalistPodiumType finalistPodiumType) {
    this.finalistPodiumType = finalistPodiumType;
    return this;
  }

  public @NonNull LocalSearchForagerConfig withBreakTieRandomly(boolean breakTieRandomly) {
    this.breakTieRandomly = breakTieRandomly;
    return this;
  }

  public @NonNull LocalSearchForagerConfig withForagerClass(
      @NonNull Class<? extends LocalSearchForager> foragerClass) {
    this.foragerClass = foragerClass;
    return this;
  }

  public @NonNull LocalSearchForagerConfig withCustomProperties(
      @NonNull Map<String, String> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  @Override
  public @NonNull LocalSearchForagerConfig inherit(
      @NonNull LocalSearchForagerConfig inheritedConfig) {
    pickEarlyType =
        ConfigUtils.inheritOverwritableProperty(pickEarlyType, inheritedConfig.getPickEarlyType());
    acceptedCountLimit =
        ConfigUtils.inheritOverwritableProperty(
            acceptedCountLimit, inheritedConfig.getAcceptedCountLimit());
    finalistPodiumType =
        ConfigUtils.inheritOverwritableProperty(
            finalistPodiumType, inheritedConfig.getFinalistPodiumType());
    breakTieRandomly =
        ConfigUtils.inheritOverwritableProperty(
            breakTieRandomly, inheritedConfig.getBreakTieRandomly());
    foragerClass =
        ConfigUtils.inheritOverwritableProperty(foragerClass, inheritedConfig.getForagerClass());
    customProperties =
        ConfigUtils.inheritOverwritableProperty(
            customProperties, inheritedConfig.getCustomProperties());
    return this;
  }

  @Override
  public @NonNull LocalSearchForagerConfig copyConfig() {
    return new LocalSearchForagerConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    if (foragerClass != null) {
      classVisitor.accept(foragerClass);
    }
  }
}
