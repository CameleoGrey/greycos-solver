package greycos.solver.spring.boot.autoconfigure.config;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.convert.DurationStyle;

public enum DiminishedReturnsProperty {
  ENABLED(
      "enabled",
      DiminishedReturnsProperties::setEnabled,
      value -> {
        if (value instanceof Boolean booleanValue) {
          return booleanValue;
        }
        if (value instanceof String stringValue) {
          if ("true".equalsIgnoreCase(stringValue)) {
            return true;
          }
          if ("false".equalsIgnoreCase(stringValue)) {
            return false;
          }
        }
        throw new IllegalArgumentException("Cannot convert (%s) to Boolean".formatted(value));
      }),
  SLIDING_WINDOW_DURATION(
      "sliding-window-duration",
      DiminishedReturnsProperties::setSlidingWindowDuration,
      value -> {
        if (value instanceof Duration duration) {
          return duration;
        }
        if (value instanceof String stringValue) {
          return DurationStyle.detectAndParse(stringValue);
        }
        throw new IllegalArgumentException("Cannot convert (%s) to Duration".formatted(value));
      }),
  MINIMUM_IMPROVEMENT_RATIO(
      "minimum-improvement-ratio",
      DiminishedReturnsProperties::setMinimumImprovementRatio,
      value -> {
        if (value instanceof Number number) {
          return number.doubleValue();
        }
        if (value instanceof String stringValue) {
          return Double.valueOf(stringValue);
        }
        throw new IllegalArgumentException("Cannot convert (%s) to Double".formatted(value));
      }),
  ;

  private final String propertyName;
  private final BiConsumer<DiminishedReturnsProperties, Object> propertyUpdater;
  private static final Set<String> PROPERTY_NAMES =
      Stream.of(DiminishedReturnsProperty.values())
          .map(DiminishedReturnsProperty::getPropertyName)
          .collect(Collectors.toCollection(TreeSet::new));

  <T> DiminishedReturnsProperty(
      String propertyName,
      BiConsumer<DiminishedReturnsProperties, T> propertySetter,
      Function<Object, T> propertyConvertor) {
    this.propertyName = propertyName;
    this.propertyUpdater =
        (properties, object) -> propertySetter.accept(properties, propertyConvertor.apply(object));
  }

  public String getPropertyName() {
    return propertyName;
  }

  public void update(DiminishedReturnsProperties properties, Object value) {
    propertyUpdater.accept(properties, value);
  }

  public static Set<String> getValidPropertyNames() {
    return Collections.unmodifiableSet(PROPERTY_NAMES);
  }

  public static DiminishedReturnsProperty forPropertyName(String propertyName) {
    for (var property : values()) {
      if (property.getPropertyName().equals(propertyName)) {
        return property;
      }
    }
    throw new IllegalArgumentException(
        "No property with the name (%s). Valid properties are %s."
            .formatted(propertyName, PROPERTY_NAMES));
  }
}
