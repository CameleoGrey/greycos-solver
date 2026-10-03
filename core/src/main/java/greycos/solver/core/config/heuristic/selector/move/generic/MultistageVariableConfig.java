package greycos.solver.core.config.heuristic.selector.move.generic;

import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;

import greycos.solver.core.config.AbstractConfig;
import greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Declares one typed variable and its permitted entity scope for a cross-variable selector. */
@XmlType(propOrder = {"kind", "entityClass", "variableName", "valueClass"})
public final class MultistageVariableConfig extends AbstractConfig<MultistageVariableConfig> {

  private MultistageVariableKind kind;
  private String entityClass;
  private String variableName;
  private String valueClass;

  public @Nullable MultistageVariableKind getKind() {
    return kind;
  }

  public void setKind(@Nullable MultistageVariableKind kind) {
    this.kind = kind;
  }

  public @Nullable Class<?> getEntityClass() {
    return ConfigUtils.resolveClass(entityClass, "entityClass", this);
  }

  public void setEntityClass(@Nullable Class<?> entityClass) {
    this.entityClass = entityClass == null ? null : entityClass.getName();
  }

  public @Nullable String getVariableName() {
    return variableName;
  }

  public void setVariableName(@Nullable String variableName) {
    this.variableName = variableName;
  }

  public @Nullable Class<?> getValueClass() {
    return ConfigUtils.resolveClass(valueClass, "valueClass", this);
  }

  public void setValueClass(@Nullable Class<?> valueClass) {
    this.valueClass = valueClass == null ? null : valueClass.getName();
  }

  public @NonNull MultistageVariableConfig withKind(@NonNull MultistageVariableKind kind) {
    setKind(kind);
    return this;
  }

  public @NonNull MultistageVariableConfig withEntityClass(@NonNull Class<?> entityClass) {
    setEntityClass(entityClass);
    return this;
  }

  public @NonNull MultistageVariableConfig withVariableName(@NonNull String variableName) {
    setVariableName(variableName);
    return this;
  }

  public @NonNull MultistageVariableConfig withValueClass(@NonNull Class<?> valueClass) {
    setValueClass(valueClass);
    return this;
  }

  @Override
  public @NonNull MultistageVariableConfig inherit(
      @NonNull MultistageVariableConfig inheritedConfig) {
    kind = ConfigUtils.inheritOverwritableProperty(kind, inheritedConfig.kind);
    entityClass = ConfigUtils.inheritOverwritableProperty(entityClass, inheritedConfig.entityClass);
    variableName =
        ConfigUtils.inheritOverwritableProperty(variableName, inheritedConfig.variableName);
    valueClass = ConfigUtils.inheritOverwritableProperty(valueClass, inheritedConfig.valueClass);
    return this;
  }

  @Override
  public @NonNull MultistageVariableConfig copyConfig() {
    return new MultistageVariableConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    if (entityClass != null) classVisitor.accept(getEntityClass());
    if (valueClass != null) classVisitor.accept(getValueClass());
  }

  @Override
  public String toString() {
    return "MultistageVariableConfig(kind=%s, entityClass=%s, variableName=%s, valueClass=%s)"
        .formatted(kind, entityClass, variableName, valueClass);
  }
}
