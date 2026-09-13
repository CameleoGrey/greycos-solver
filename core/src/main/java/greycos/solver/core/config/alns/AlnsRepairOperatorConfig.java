package greycos.solver.core.config.alns;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
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
      "customClass",
      "topK",
      "regretK",
      "initialWeight",
      "customProperties"
    })
public final class AlnsRepairOperatorConfig extends AbstractConfig<AlnsRepairOperatorConfig> {
  private String id;
  private AlnsRepairOperatorType type;
  private String entityClass;
  private String variableName;
  private String customClass;
  private Integer topK;
  private Integer regretK;
  private Double initialWeight;

  @XmlJavaTypeAdapter(JaxbCustomPropertiesAdapter.class)
  private Map<String, String> customProperties;

  public @Nullable String getId() {
    return id;
  }

  public void setId(@Nullable String id) {
    this.id = id;
  }

  public @NonNull AlnsRepairOperatorConfig withId(@NonNull String id) {
    setId(id);
    return this;
  }

  public @Nullable AlnsRepairOperatorType getType() {
    return type;
  }

  public void setType(@Nullable AlnsRepairOperatorType type) {
    this.type = type;
  }

  public @NonNull AlnsRepairOperatorConfig withType(@NonNull AlnsRepairOperatorType type) {
    setType(type);
    return this;
  }

  public @Nullable Class<?> getEntityClass() {
    return ConfigUtils.resolveClass(entityClass, "entityClass", this);
  }

  public void setEntityClass(@Nullable Class<?> entityClass) {
    this.entityClass = entityClass == null ? null : entityClass.getName();
  }

  public @NonNull AlnsRepairOperatorConfig withEntityClass(@NonNull Class<?> entityClass) {
    setEntityClass(entityClass);
    return this;
  }

  public @Nullable String getVariableName() {
    return variableName;
  }

  public void setVariableName(@Nullable String variableName) {
    this.variableName = variableName;
  }

  public @NonNull AlnsRepairOperatorConfig withVariableName(@NonNull String variableName) {
    setVariableName(variableName);
    return this;
  }

  public @Nullable Class<? extends AlnsRepairOperator> getCustomClass() {
    return ConfigUtils.resolveClass(customClass, "customClass", this);
  }

  public void setCustomClass(@Nullable Class<? extends AlnsRepairOperator> customClass) {
    this.customClass = customClass == null ? null : customClass.getName();
  }

  public @NonNull AlnsRepairOperatorConfig withCustomClass(
      @NonNull Class<? extends AlnsRepairOperator> customClass) {
    setCustomClass(customClass);
    return this;
  }

  public @Nullable Integer getTopK() {
    return topK;
  }

  public void setTopK(@Nullable Integer topK) {
    this.topK = topK;
  }

  public @NonNull AlnsRepairOperatorConfig withTopK(@NonNull Integer topK) {
    setTopK(topK);
    return this;
  }

  /**
   * Number of alternatives used by {@link AlnsRepairOperatorType#REGRET_K}; at least two. When
   * unspecified, that operator uses four alternatives. Existing regret operators retain their fixed
   * number of alternatives.
   */
  public @Nullable Integer getRegretK() {
    return regretK;
  }

  public void setRegretK(@Nullable Integer regretK) {
    this.regretK = regretK;
  }

  public @NonNull AlnsRepairOperatorConfig withRegretK(@NonNull Integer regretK) {
    setRegretK(regretK);
    return this;
  }

  public @Nullable Double getInitialWeight() {
    return initialWeight;
  }

  public void setInitialWeight(@Nullable Double initialWeight) {
    this.initialWeight = initialWeight;
  }

  public @NonNull AlnsRepairOperatorConfig withInitialWeight(@NonNull Double initialWeight) {
    setInitialWeight(initialWeight);
    return this;
  }

  public @Nullable Map<String, String> getCustomProperties() {
    return customProperties;
  }

  public void setCustomProperties(@Nullable Map<String, String> customProperties) {
    this.customProperties = customProperties;
  }

  public @NonNull AlnsRepairOperatorConfig withCustomProperties(
      @NonNull Map<String, String> customProperties) {
    setCustomProperties(customProperties);
    return this;
  }

  @Override
  public @NonNull AlnsRepairOperatorConfig inherit(
      @NonNull AlnsRepairOperatorConfig inheritedConfig) {
    id = ConfigUtils.inheritOverwritableProperty(id, inheritedConfig.id);
    type = ConfigUtils.inheritOverwritableProperty(type, inheritedConfig.type);
    entityClass = ConfigUtils.inheritOverwritableProperty(entityClass, inheritedConfig.entityClass);
    variableName =
        ConfigUtils.inheritOverwritableProperty(variableName, inheritedConfig.variableName);
    customClass = ConfigUtils.inheritOverwritableProperty(customClass, inheritedConfig.customClass);
    topK = ConfigUtils.inheritOverwritableProperty(topK, inheritedConfig.topK);
    regretK = ConfigUtils.inheritOverwritableProperty(regretK, inheritedConfig.regretK);
    initialWeight =
        ConfigUtils.inheritOverwritableProperty(initialWeight, inheritedConfig.initialWeight);
    if (customProperties == null && inheritedConfig.customProperties != null) {
      customProperties = new LinkedHashMap<>(inheritedConfig.customProperties);
    }
    return this;
  }

  @Override
  public @NonNull AlnsRepairOperatorConfig copyConfig() {
    return new AlnsRepairOperatorConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    if (entityClass != null) {
      classVisitor.accept(getEntityClass());
    }
    if (customClass != null) {
      classVisitor.accept(getCustomClass());
    }
  }
}
