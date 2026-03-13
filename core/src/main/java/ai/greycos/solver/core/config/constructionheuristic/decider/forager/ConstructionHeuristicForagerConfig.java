package ai.greycos.solver.core.config.constructionheuristic.decider.forager;

import java.util.Map;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import ai.greycos.solver.core.config.AbstractConfig;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.impl.constructionheuristic.decider.forager.ConstructionHeuristicForager;
import ai.greycos.solver.core.impl.io.jaxb.JaxbCustomPropertiesAdapter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for construction heuristic forager. Supports built-in foragers via pickEarlyType
 * and custom foragers via foragerClass. Custom properties can be injected via setter methods on
 * forager class.
 */
@XmlType(propOrder = {"pickEarlyType", "foragerClass", "customProperties"})
public class ConstructionHeuristicForagerConfig
    extends AbstractConfig<ConstructionHeuristicForagerConfig> {

  private ConstructionHeuristicPickEarlyType pickEarlyType = null;
  private Class<? extends ConstructionHeuristicForager> foragerClass = null;

  @XmlJavaTypeAdapter(JaxbCustomPropertiesAdapter.class)
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
        ConfigUtils.inheritOverwritableProperty(
            customProperties, inheritedConfig.getCustomProperties());
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
