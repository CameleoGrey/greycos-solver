package greycos.solver.core.config.localsearch;

import java.math.BigDecimal;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlType;

import greycos.solver.core.config.AbstractConfig;
import greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Overrides the automatically calibrated score units of an automatic decision feature. */
@XmlType(propOrder = {"scoreLevelIndex", "scale"})
public class GuidedLocalSearchLevelScaleConfig
    extends AbstractConfig<GuidedLocalSearchLevelScaleConfig> {

  private Integer scoreLevelIndex;
  private BigDecimal scale;

  public @Nullable Integer getScoreLevelIndex() {
    return scoreLevelIndex;
  }

  public void setScoreLevelIndex(@Nullable Integer scoreLevelIndex) {
    this.scoreLevelIndex = scoreLevelIndex;
  }

  public @NonNull GuidedLocalSearchLevelScaleConfig withScoreLevelIndex(int scoreLevelIndex) {
    setScoreLevelIndex(scoreLevelIndex);
    return this;
  }

  /** Positive exact score units per automatic feature. */
  public @Nullable BigDecimal getScale() {
    return scale;
  }

  public void setScale(@Nullable BigDecimal scale) {
    this.scale = scale;
  }

  public @NonNull GuidedLocalSearchLevelScaleConfig withScale(@NonNull BigDecimal scale) {
    setScale(scale);
    return this;
  }

  @Override
  public @NonNull GuidedLocalSearchLevelScaleConfig inherit(
      @NonNull GuidedLocalSearchLevelScaleConfig inheritedConfig) {
    scoreLevelIndex =
        ConfigUtils.inheritOverwritableProperty(scoreLevelIndex, inheritedConfig.scoreLevelIndex);
    scale = ConfigUtils.inheritOverwritableProperty(scale, inheritedConfig.scale);
    return this;
  }

  @Override
  public @NonNull GuidedLocalSearchLevelScaleConfig copyConfig() {
    return new GuidedLocalSearchLevelScaleConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {}
}
