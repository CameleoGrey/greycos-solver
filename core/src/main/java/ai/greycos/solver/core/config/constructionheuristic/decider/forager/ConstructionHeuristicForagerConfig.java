package ai.greycos.solver.core.config.constructionheuristic.decider.forager;

import java.util.Map;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;

import ai.greycos.solver.core.config.AbstractConfig;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for a construction heuristic forager.
 *
 * <p>A forager is responsible for collecting evaluated moves during a step,
 * deciding when to stop evaluating moves (early termination), and selecting the best move to apply.</p>
 *
 * <p>This configuration supports:</p>
 * <ul>
 *   <li>Built-in foragers configured via {@link #pickEarlyType}</li>
 *   <li>Custom foragers configured via {@link #foragerClass} (enterprise feature)</li>
 * </ul>
 *
 * <p>When using custom foragers, you can inject properties via {@link #customProperties}.
 * Custom properties are set using setter methods on the forager class (e.g., {@code setTopK("5")}).</p>
 *
 * <p><b>Enterprise Feature:</b> Custom forager functionality requires a valid Greycos Enterprise license.
 * Attempting to use a custom forager without a license will result in an {@link UnsupportedOperationException}.</p>
 */

@XmlType(propOrder = {"pickEarlyType", "foragerClass", "customProperties"})
public class ConstructionHeuristicForagerConfig
    extends AbstractConfig<ConstructionHeuristicForagerConfig> {

  private ConstructionHeuristicPickEarlyType pickEarlyType = null;
  private Class<? extends ConstructionHeuristicForager> foragerClass = null;
  private Map<String, String> customProperties = null;

  public @Nullable ConstructionHeuristicPickEarlyType getPickEarlyType() {
    return pickEarlyType;
  }

  public void setPickEarlyType(@Nullable ConstructionHeuristicPickEarlyType pickEarlyType) {
    this.pickEarlyType = pickEarlyType;
  }

  public @Nullable Class<? extends ConstructionHeuristicForager> getForagerClass() {
    return foragerClass;
  }

  public void setForagerClass(
      @Nullable Class<? extends ConstructionHeuristicForager> foragerClass) {
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

  public @NonNull ConstructionHeuristicForagerConfig withPickEarlyType(
      @NonNull ConstructionHeuristicPickEarlyType pickEarlyType) {
    this.setPickEarlyType(pickEarlyType);
    return this;
  }

  public @NonNull ConstructionHeuristicForagerConfig withForagerClass(
      @NonNull Class<? extends ConstructionHeuristicForager> foragerClass) {
    this.setForagerClass(foragerClass);
    return this;
  }

  public @NonNull ConstructionHeuristicForagerConfig withCustomProperties(
      @NonNull Map<String, String> customProperties) {
    this.setCustomProperties(customProperties);
    return this;
  }

  @Override
  public @NonNull ConstructionHeuristicForagerConfig inherit(
      @NonNull ConstructionHeuristicForagerConfig inheritedConfig) {
    pickEarlyType =
        ConfigUtils.inheritOverwritableProperty(pickEarlyType, inheritedConfig.getPickEarlyType());
    foragerClass =
        ConfigUtils.inheritOverwritableProperty(foragerClass, inheritedConfig.getForagerClass());
    customProperties =
        ConfigUtils.inheritOverwritableProperty(customProperties, inheritedConfig.getCustomProperties());
    return this;
  }

  @Override
  public @NonNull ConstructionHeuristicForagerConfig copyConfig() {
    return new ConstructionHeuristicForagerConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    if (foragerClass != null) {
      classVisitor.accept(foragerClass);
    }
  }
}
