package greycos.solver.greybay.api.sampling;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** JSON-compatible checkpoint with explicit unsigned-bit RNG state encoded as hexadecimal. */
public record SamplerState(
    String implementationVersion,
    long randomState,
    long requestSequence,
    long postStartupRequestSequence,
    long roundCount,
    long drawCount,
    Set<String> exhaustedRoots,
    Map<String, Object> customState) {
  public SamplerState {
    if (implementationVersion == null
        || implementationVersion.isBlank()
        || requestSequence < 0
        || postStartupRequestSequence < 0
        || postStartupRequestSequence > requestSequence
        || roundCount < 0
        || drawCount < 0) throw new IllegalArgumentException("Invalid sampler state.");
    exhaustedRoots = Set.copyOf(exhaustedRoots);
    customState = freezeMap(customState, 0);
  }

  public SamplerState(
      String implementationVersion,
      long randomState,
      long requestSequence,
      long postStartupRequestSequence,
      long roundCount,
      long drawCount,
      Set<String> exhaustedRoots) {
    this(
        implementationVersion,
        randomState,
        requestSequence,
        postStartupRequestSequence,
        roundCount,
        drawCount,
        exhaustedRoots,
        Map.of());
  }

  public Map<String, Object> toMap() {
    return Map.of(
        "implementationVersion",
        implementationVersion,
        "randomState",
        Long.toUnsignedString(randomState, 16),
        "requestSequence",
        Long.toString(requestSequence),
        "postStartupRequestSequence",
        Long.toString(postStartupRequestSequence),
        "roundCount",
        Long.toString(roundCount),
        "drawCount",
        Long.toString(drawCount),
        "exhaustedRoots",
        exhaustedRoots.stream().sorted().toList(),
        "customState",
        customState);
  }

  public static SamplerState fromMap(Map<String, ?> map) {
    if (!map.keySet()
            .equals(
                Set.of(
                    "implementationVersion",
                    "randomState",
                    "requestSequence",
                    "postStartupRequestSequence",
                    "roundCount",
                    "drawCount",
                    "exhaustedRoots",
                    "customState"))
        || !(map.get("exhaustedRoots") instanceof List<?> roots)
        || roots.stream().anyMatch(root -> !(root instanceof String))
        || !(map.get("customState") instanceof Map<?, ?> custom))
      throw new IllegalArgumentException("Invalid sampler-state encoding.");
    var rootSet =
        roots.stream()
            .map(String.class::cast)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    if (rootSet.size() != roots.size())
      throw new IllegalArgumentException("Duplicate exhausted roots in sampler state.");
    return new SamplerState(
        (String) map.get("implementationVersion"),
        Long.parseUnsignedLong((String) map.get("randomState"), 16),
        Long.parseLong((String) map.get("requestSequence")),
        Long.parseLong((String) map.get("postStartupRequestSequence")),
        Long.parseLong((String) map.get("roundCount")),
        Long.parseLong((String) map.get("drawCount")),
        rootSet,
        freezeMap(custom, 0));
  }

  private static Map<String, Object> freezeMap(Map<?, ?> source, int depth) {
    var frozen = new java.util.TreeMap<String, Object>();
    source.forEach(
        (key, value) -> {
          if (!(key instanceof String text))
            throw new IllegalArgumentException("Custom sampler-state map keys must be strings.");
          frozen.put(text, freeze(value, depth + 1));
        });
    return java.util.Collections.unmodifiableMap(frozen);
  }

  private static Object freeze(Object value, int depth) {
    if (depth > 128)
      throw new IllegalArgumentException(
          "Custom sampler state must be an acyclic JSON tree of depth at most 128.");
    if (value == null || value instanceof String || value instanceof Boolean) return value;
    if (value instanceof Map<?, ?> map) return freezeMap(map, depth);
    if (value instanceof List<?> list) {
      var frozen = new java.util.ArrayList<Object>();
      list.forEach(element -> frozen.add(freeze(element, depth + 1)));
      return java.util.Collections.unmodifiableList(frozen);
    }
    // Require explicit string encodings for exact counters, binary64 bits and custom numeric
    // values, avoiding number-type/precision changes across different JSON implementations.
    throw new IllegalArgumentException(
        "Custom sampler state supports maps, lists, strings, booleans and null; encode numbers as strings: "
            + value);
  }
}
