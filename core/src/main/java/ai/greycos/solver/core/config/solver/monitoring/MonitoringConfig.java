package ai.greycos.solver.core.config.solver.monitoring;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import ai.greycos.solver.core.config.AbstractConfig;
import ai.greycos.solver.core.config.util.ConfigUtils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@XmlType(
    propOrder = {
      "solverMetricList",
      "constraintMatchMetricSampleInterval",
    })
public class MonitoringConfig extends AbstractConfig<MonitoringConfig> {
  @XmlElement(name = "metric")
  protected List<SolverMetric> solverMetricList = null;

  protected Integer constraintMatchMetricSampleInterval = null;

  // ************************************************************************
  // Constructors and simple getters/setters
  // ************************************************************************
  public @Nullable List<@NonNull SolverMetric> getSolverMetricList() {
    return solverMetricList;
  }

  public void setSolverMetricList(@Nullable List<@NonNull SolverMetric> solverMetricList) {
    this.solverMetricList = solverMetricList;
  }

  public @Nullable Integer getConstraintMatchMetricSampleInterval() {
    return constraintMatchMetricSampleInterval;
  }

  public void setConstraintMatchMetricSampleInterval(
      @Nullable Integer constraintMatchMetricSampleInterval) {
    if (constraintMatchMetricSampleInterval != null && constraintMatchMetricSampleInterval < 1) {
      throw new IllegalArgumentException(
          "The constraintMatchMetricSampleInterval ("
              + constraintMatchMetricSampleInterval
              + ") must be at least 1.");
    }
    this.constraintMatchMetricSampleInterval = constraintMatchMetricSampleInterval;
  }

  // ************************************************************************
  // With methods
  // ************************************************************************

  public @NonNull MonitoringConfig withSolverMetricList(
      @NonNull List<@NonNull SolverMetric> solverMetricList) {
    this.solverMetricList = solverMetricList;
    return this;
  }

  public @NonNull MonitoringConfig withConstraintMatchMetricSampleInterval(
      int constraintMatchMetricSampleInterval) {
    setConstraintMatchMetricSampleInterval(constraintMatchMetricSampleInterval);
    return this;
  }

  public int determineConstraintMatchMetricSampleInterval() {
    return Objects.requireNonNullElse(constraintMatchMetricSampleInterval, 1);
  }

  @Override
  public @NonNull MonitoringConfig inherit(@NonNull MonitoringConfig inheritedConfig) {
    solverMetricList =
        ConfigUtils.inheritMergeableListProperty(
            solverMetricList, inheritedConfig.solverMetricList);
    constraintMatchMetricSampleInterval =
        ConfigUtils.inheritOverwritableProperty(
            constraintMatchMetricSampleInterval,
            inheritedConfig.constraintMatchMetricSampleInterval);
    return this;
  }

  @Override
  public @NonNull MonitoringConfig copyConfig() {
    return new MonitoringConfig().inherit(this);
  }

  @Override
  public void visitReferencedClasses(@NonNull Consumer<Class<?>> classVisitor) {
    // No referenced classes currently
    // If we add custom metrics here, then this should
    // register the custom metrics
  }
}
