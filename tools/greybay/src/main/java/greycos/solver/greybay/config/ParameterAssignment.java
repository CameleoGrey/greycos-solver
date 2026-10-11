package greycos.solver.greybay.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable typed active values. Strings are categories, longs integers and doubles real
 * coordinates.
 */
public final class ParameterAssignment {
  private final Map<String, Object> values;

  public ParameterAssignment(Map<String, ?> values) {
    Objects.requireNonNull(values, "values");
    var sorted = new TreeMap<String, Object>();
    values.forEach(
        (name, value) -> {
          if (name == null || name.isBlank())
            throw new IllegalArgumentException("Parameter name must not be blank.");
          sorted.put(name, normalize(value));
        });
    this.values = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
  }

  private static Object normalize(Object value) {
    if (value instanceof String) return value;
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      return ((Number) value).longValue();
    }
    if (value instanceof Float || value instanceof Double) {
      double number = ((Number) value).doubleValue();
      if (!Double.isFinite(number))
        throw new IllegalArgumentException("Real parameter value (" + number + ") must be finite.");
      return number == 0.0 ? 0.0 : number;
    }
    throw new IllegalArgumentException(
        "Parameter value (" + value + ") must be a string, integer or finite real.");
  }

  public static ParameterAssignment of(Map<String, ?> values) {
    return new ParameterAssignment(values);
  }

  public Map<String, Object> values() {
    return values;
  }

  public boolean contains(String name) {
    return values.containsKey(name);
  }

  public String getString(String name) {
    return require(name, String.class);
  }

  public long getLong(String name) {
    return require(name, Long.class);
  }

  public double getDouble(String name) {
    return require(name, Double.class);
  }

  public Object get(String name) {
    if (!values.containsKey(name))
      throw new IllegalArgumentException("No active parameter (" + name + ").");
    return values.get(name);
  }

  private <T> T require(String name, Class<T> type) {
    var value = get(name);
    if (!type.isInstance(value))
      throw new IllegalArgumentException(
          "Parameter (" + name + ") must have type (" + type.getSimpleName() + ").");
    return type.cast(value);
  }

  /** Unambiguous, ordered, type-tagged representation suitable for hashing. */
  public String canonicalForm() {
    var result = new StringBuilder();
    values.forEach(
        (name, value) -> {
          result.append(name.length()).append(':').append(name);
          if (value instanceof String text)
            result.append('s').append(text.length()).append(':').append(text);
          else if (value instanceof Long integer) result.append('i').append(integer).append(';');
          else result.append('r').append(Double.toHexString((Double) value)).append(';');
        });
    return result.toString();
  }

  public Map<String, Object> toMap() {
    var result = new LinkedHashMap<String, Object>();
    values.forEach(
        (name, value) ->
            result.put(
                name,
                Map.of(
                    "type",
                    value instanceof String
                        ? "categorical"
                        : value instanceof Long ? "integer" : "real",
                    "value",
                    value instanceof Double number
                        ? Double.toHexString(number)
                        : value.toString())));
    return Collections.unmodifiableMap(result);
  }

  public static ParameterAssignment fromMap(Map<String, ?> encoded) {
    var values = new LinkedHashMap<String, Object>();
    encoded.forEach(
        (name, entry) -> {
          if (!(entry instanceof Map<?, ?> map)
              || map.size() != 2
              || !(map.get("value") instanceof String value))
            throw new IllegalArgumentException("Invalid encoded parameter (" + name + ").");
          var type = map.get("type");
          if ("categorical".equals(type)) values.put(name, value);
          else if ("integer".equals(type)) values.put(name, Long.parseLong(value));
          else if ("real".equals(type)) values.put(name, Double.valueOf(value));
          else throw new IllegalArgumentException("Unknown parameter type (" + type + ").");
        });
    return of(values);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ParameterAssignment assignment && values.equals(assignment.values);
  }

  @Override
  public int hashCode() {
    return values.hashCode();
  }

  @Override
  public String toString() {
    return values.toString();
  }
}
