package greycos.solver.greybay.impl.persistence;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Strict deterministic JSON for durable metadata; numeric quantities must be encoded as strings.
 */
public final class StoreJson {
  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  private StoreJson() {}

  public static Map<String, Object> immutableObject(Map<String, Object> value) {
    Objects.requireNonNull(value, "value");
    @SuppressWarnings("unchecked")
    var result = (Map<String, Object>) copy(value, new IdentityHashMap<>());
    return result;
  }

  public static byte[] canonicalBytes(Map<String, Object> value) {
    return MAPPER.writeValueAsBytes(immutableObject(value));
  }

  public static Map<String, Object> readObject(byte[] bytes) throws IOException {
    try {
      Object parsed = MAPPER.readValue(bytes, Object.class);
      if (!(parsed instanceof Map<?, ?>)) {
        throw new IllegalArgumentException("Expected a JSON object.");
      }
      @SuppressWarnings("unchecked")
      var map = (Map<String, Object>) parsed;
      return immutableObject(map);
    } catch (RuntimeException failure) {
      throw new IOException("Invalid durable JSON: " + failure.getMessage(), failure);
    }
  }

  public static String sha256(byte[] bytes) {
    try {
      return "sha256:"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("The JVM does not provide SHA-256.", failure);
    }
  }

  private static Object copy(Object value, IdentityHashMap<Object, Boolean> active) {
    if (value == null || value instanceof String || value instanceof Boolean) {
      return value;
    }
    if (active.put(value, Boolean.TRUE) != null) {
      throw new IllegalArgumentException("Durable JSON cannot contain cyclic collections.");
    }
    try {
      if (value instanceof Map<?, ?> map) {
        var result = new TreeMap<String, Object>();
        for (var entry : map.entrySet()) {
          if (!(entry.getKey() instanceof String key)) {
            throw new IllegalArgumentException("Durable JSON object keys must be strings.");
          }
          result.put(key, copy(entry.getValue(), active));
        }
        return Collections.unmodifiableMap(result);
      }
      if (value instanceof List<?> list) {
        var result = new ArrayList<Object>(list.size());
        for (var item : list) {
          result.add(copy(item, active));
        }
        return Collections.unmodifiableList(result);
      }
      throw new IllegalArgumentException(
          "Unsupported durable JSON value type ("
              + value.getClass().getName()
              + "). Encode exact numeric values as strings.");
    } finally {
      active.remove(value);
    }
  }
}
