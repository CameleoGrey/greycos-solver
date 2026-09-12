package greycos.solver.core.config.alns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsRanking;
import greycos.solver.core.api.solver.alns.AlnsRelatedness;
import greycos.solver.core.config.AbstractConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.io.jaxb.JaxbCustomPropertiesAdapter;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Configuration for an independently instantiated ALNS operator. */
@XmlType(
    propOrder = {
      "id",
      "type",
      "entityClass",
      "variableName",
      "minimumDestroyedCount",
      "maximumDestroyedCount",
      "minimumDestroyedPercentage",
      "maximumDestroyedPercentage",
      "customClass",
      "relatednessClass",
      "rankingClass",
      "rankExponent",
      "initialWeight",
      "customProperties"
    })
public final class AlnsDestroyOperatorConfig extends AbstractConfig<AlnsDestroyOperatorConfig> {
  private String id;
  private AlnsDestroyOperatorType type;
  private String entityClass;
  private String variableName;
  private Integer minimumDestroyedCount;
  private Integer maximumDestroyedCount;
  private Double minimumDestroyedPercentage;
  private Double maximumDestroyedPercentage;
  private String customClass;
  private String relatednessClass;
  private String rankingClass;
  private Double rankExponent;
  private Double initialWeight;

  @XmlJavaTypeAdapter(JaxbCustomPropertiesAdapter.class)
  private Map<String, String> customProperties;

  public @Nullable String getId() {
    return id;
  }

  public void setId(@Nullable String id) {
    this.id = id;
  }

  public @NonNull AlnsDestroyOperatorConfig withId(@NonNull String id) {
    setId(id);
    return this;
  }

  public @Nullable AlnsDestroyOperatorType getType() {
    return type;
  }

  public void setType(@Nullable AlnsDestroyOperatorType type) {
    this.type = type;
  }

  public @NonNull AlnsDestroyOperatorConfig withType(@NonNull AlnsDestroyOperatorType type) {
    setType(type);
    return this;
  }

  public @Nullable Class<?> getEntityClass() {
    return ConfigUtils.resolveClass(entityClass, "entityClass", this);
  }

  public void setEntityClass(@Nullable Class<?> entityClass) {
    this.entityClass = entityClass == null ? null : entityClass.getName();
  }

  public @NonNull AlnsDestroyOperatorConfig withEntityClass(@NonNull Class<?> entityClass) {
    setEntityClass(entityClass);
    return this;
  }

  public @Nullable String getVariableName() {
    return variableName;
  }

  public void setVariableName(@Nullable String variableName) {
    this.variableName = variableName;
  }

  public @NonNull AlnsDestroyOperatorConfig withVariableName(@NonNull String variableName) {
    setVariableName(variableName);
    return this;
  }

  public @Nullable Integer getMinimumDestroyedCount() {
    return minimumDestroyedCount;
  }

  public void setMinimumDestroyedCount(@Nullable Integer minimumDestroyedCount) {
    this.minimumDestroyedCount = minimumDestroyedCount;
  }

  public @NonNull AlnsDestroyOperatorConfig withMinimumDestroyedCount(
      @NonNull Integer minimumDestroyedCount) {
    setMinimumDestroyedCount(minimumDestroyedCount);
    return this;
  }

  public @Nullable Integer getMaximumDestroyedCount() {
    return maximumDestroyedCount;
  }

  public void setMaximumDestroyedCount(@Nullable Integer maximumDestroyedCount) {
    this.maximumDestroyedCount = maximumDestroyedCount;
  }

  public @NonNull AlnsDestroyOperatorConfig withMaximumDestroyedCount(
      @NonNull Integer maximumDestroyedCount) {
    setMaximumDestroyedCount(maximumDestroyedCount);
    return this;
  }

  public @Nullable Double getMinimumDestroyedPercentage() {
    return minimumDestroyedPercentage;
  }

  public void setMinimumDestroyedPercentage(@Nullable Double minimumDestroyedPercentage) {
    this.minimumDestroyedPercentage = minimumDestroyedPercentage;
  }

  public @NonNull AlnsDestroyOperatorConfig withMinimumDestroyedPercentage(
      @NonNull Double minimumDestroyedPercentage) {
    setMinimumDestroyedPercentage(minimumDestroyedPercentage);
    return this;
  }

  public @Nullable Double getMaximumDestroyedPercentage() {
    return maximumDestroyedPercentage;
  }

  public void setMaximumDestroyedPercentage(@Nullable Double maximumDestroyedPercentage) {
    this.maximumDestroyedPercentage = maximumDestroyedPercentage;
  }

  public @NonNull AlnsDestroyOperatorConfig withMaximumDestroyedPercentage(
      @NonNull Double maximumDestroyedPercentage) {
    setMaximumDestroyedPercentage(maximumDestroyedPercentage);
    return this;
  }

  public @Nullable Class<? extends AlnsDestroyOperator> getCustomClass() {
    return ConfigUtils.resolveClass(customClass, "customClass", this);
  }

  public void setCustomClass(@Nullable Class<? extends AlnsDestroyOperator> customClass) {
    this.customClass = customClass == null ? null : customClass.getName();
  }

  public @NonNull AlnsDestroyOperatorConfig withCustomClass(
      @NonNull Class<? extends AlnsDestroyOperator> customClass) {
    setCustomClass(customClass);
    return this;
  }

  public @Nullable Class<? extends AlnsRelatedness> getRelatednessClass() {
    return ConfigUtils.resolveClass(relatednessClass, "relatednessClass", this);
  }

  public void setRelatednessClass(@Nullable Class<? extends AlnsRelatedness> relatednessClass) {
    this.relatednessClass = relatednessClass == null ? null : relatednessClass.getName();
  }

  public @NonNull AlnsDestroyOperatorConfig withRelatednessClass(
      @NonNull Class<? extends AlnsRelatedness> relatednessClass) {
    setRelatednessClass(relatednessClass);
    return this;
  }

  public @Nullable Class<? extends AlnsRanking> getRankingClass() {
    return ConfigUtils.resolveClass(rankingClass, "rankingClass", this);
  }

  public void setRankingClass(@Nullable Class<? extends AlnsRanking> rankingClass) {
    this.rankingClass = rankingClass == null ? null : rankingClass.getName();
  }

  public @NonNull AlnsDestroyOperatorConfig withRankingClass(
      @NonNull Class<? extends AlnsRanking> rankingClass) {
    setRankingClass(rankingClass);
    return this;
  }

  public @Nullable Double getRankExponent() {
    return rankExponent;
  }

  public void setRankExponent(@Nullable Double rankExponent) {
    this.rankExponent = rankExponent;
  }

  public @NonNull AlnsDestroyOperatorConfig withRankExponent(@NonNull Double rankExponent) {
    setRankExponent(rankExponent);
    return this;
  }

  public @Nullable Double getInitialWeight() {
    return initialWeight;
  }

  public void setInitialWeight(@Nullable Double initialWeight) {
    this.initialWeight = initialWeight;
  }

  public @NonNull AlnsDestroyOperatorConfig withInitialWeight(@NonNull Double initialWeight) {
    setInitialWeight(initialWeight);
    return this;
  }

  public @Nullable Map<String, String> getCustomProperties() {
    return customProperties;
  }

  public void setCustomProperties(@Nullable Map<String, String> customProperties) {
    this.customProperties = customProperties;
  }

  public @NonNull AlnsDestroyOperatorConfig withCustomProperties(
      @NonNull Map<String, String> customProperties) {
    setCustomProperties(customProperties);
    return this;
  }

  @Override
  public @NonNull AlnsDestroyOperatorConfig inherit(
      @NonNull AlnsDestroyOperatorConfig inheritedConfig) {
    id = ConfigUtils.inheritOverwritableProperty(id, inheritedConfig.id);
    type = ConfigUtils.inheritOverwritableProperty(type, inheritedConfig.type);
    entityClass = ConfigUtils.inheritOverwritableProperty(entityClass, inheritedConfig.entityClass);
    variableName =
        ConfigUtils.inheritOverwritableProperty(variableName, inheritedConfig.variableName);
    minimumDestroyedCount =
        ConfigUtils.inheritOverwritableProperty(
            minimumDestroyedCount, inheritedConfig.minimumDestroyedCount);
    maximumDestroyedCount =
        ConfigUtils.inheritOverwritableProperty(
            maximumDestroyedCount, inheritedConfig.maximumDestroyedCount);
    minimumDestroyedPercentage =
        ConfigUtils.inheritOverwritableProperty(
            minimumDestroyedPercentage, inheritedConfig.minimumDestroyedPercentage);
    maximumDestroyedPercentage =
        ConfigUtils.inheritOverwritableProperty(
            maximumDestroyedPercentage, inheritedConfig.maximumDestroyedPercentage);
    customClass = ConfigUtils.inheritOverwritableProperty(customClass, inheritedConfig.customClass);
    relatednessClass =
        ConfigUtils.inheritOverwritableProperty(relatednessClass, inheritedConfig.relatednessClass);
    rankingClass =
        ConfigUtils.inheritOverwritableProperty(rankingClass, inheritedConfig.rankingClass);
    rankExponent =
        ConfigUtils.inheritOverwritableProperty(rankExponent, inheritedConfig.rankExponent);
    initialWeight =
        ConfigUtils.inheritOverwritableProperty(initialWeight, inheritedConfig.initialWeight);
    if (customProperties == null && inheritedConfig.customProperties != null) {
      customProperties = new LinkedHashMap<>(inheritedConfig.customProperties);
    }
    return this;
  }

  @Override
  public @NonNull AlnsDestroyOperatorConfig copyConfig() {
    return new AlnsDestroyOperatorConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    if (entityClass != null) {
      classVisitor.accept(getEntityClass());
    }
    if (customClass != null) {
      classVisitor.accept(getCustomClass());
    }
    if (relatednessClass != null) {
      classVisitor.accept(getRelatednessClass());
    }
    if (rankingClass != null) {
      classVisitor.accept(getRankingClass());
    }
  }
}
