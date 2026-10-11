package greycos.solver.greybay.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable ordered conditional schema. Parameter names are globally unique. */
public record SearchSpace(String version, List<Dimension> dimensions) {
  public SearchSpace {
    if (version == null || version.isBlank())
      throw new IllegalArgumentException("Search-space version must not be blank.");
    dimensions = List.copyOf(dimensions);
    var preceding = new LinkedHashMap<String, Dimension>();
    for (var dimension : dimensions) {
      if (preceding.containsKey(dimension.name()))
        throw new IllegalArgumentException("Duplicate dimension (" + dimension.name() + ").");
      for (var clause : dimension.activation().alternatives())
        for (var entry : clause.entrySet()) {
          if (!(preceding.get(entry.getKey()) instanceof CategoricalDimension parent))
            throw new IllegalArgumentException(
                "Activation of ("
                    + dimension.name()
                    + ") must reference an earlier categorical dimension ("
                    + entry.getKey()
                    + "); forward references and cycles are forbidden.");
          if (!parent.choices().containsAll(entry.getValue()))
            throw new IllegalArgumentException(
                "Activation of ("
                    + dimension.name()
                    + ") refers to unknown choices of ("
                    + parent.name()
                    + ").");
        }
      preceding.put(dimension.name(), dimension);
    }
    var root = preceding.get("template");
    if (root != null
        && (!(root instanceof CategoricalDimension)
            || !root.activation().equals(Activation.always())
            || !dimensions.getFirst().equals(root)))
      throw new IllegalArgumentException(
          "The template dimension must be the first unconditional categorical dimension.");
  }

  public SearchSpace(List<Dimension> dimensions) {
    this("1", dimensions);
  }

  public void validate(ParameterAssignment assignment) {
    Objects.requireNonNull(assignment, "assignment");
    var active = new LinkedHashSet<String>();
    for (var dimension : dimensions) {
      if (dimension.activation().isActive(assignment.values())) {
        active.add(dimension.name());
        if (!assignment.contains(dimension.name()))
          throw new IllegalArgumentException(
              "Missing active parameter (" + dimension.name() + ").");
        dimension.validateValue(assignment.get(dimension.name()));
      }
    }
    if (!active.equals(assignment.values().keySet()))
      throw new IllegalArgumentException(
          "Assignment contains inactive or unknown parameters: "
              + assignment.values().keySet().stream()
                  .filter(name -> !active.contains(name))
                  .toList());
  }

  public List<String> rootChoices() {
    return !dimensions.isEmpty()
            && dimensions.getFirst() instanceof CategoricalDimension root
            && root.name().equals("template")
        ? root.choices()
        : List.of("");
  }

  /** Full categorical ancestor context, including transitive activation dependencies. */
  public Set<String> contextNames(Dimension dimension) {
    var result = new LinkedHashSet<String>();
    addAncestors(dimension, result);
    return Collections.unmodifiableSet(result);
  }

  private void addAncestors(Dimension dimension, Set<String> result) {
    for (var dependency : dimension.activation().dependencies()) {
      if (result.add(dependency))
        addAncestors(
            dimensions.stream()
                .filter(candidate -> candidate.name().equals(dependency))
                .findFirst()
                .orElseThrow(),
            result);
    }
  }

  public String contextKey(Dimension dimension, ParameterAssignment assignment) {
    var context = new LinkedHashMap<String, Object>();
    for (var name : contextNames(dimension))
      if (assignment.contains(name)) context.put(name, assignment.get(name));
    return ParameterAssignment.of(context).canonicalForm();
  }

  /** Combines template-local spaces; caller map iteration defines stable template order. */
  public static SearchSpace combine(String version, Map<String, SearchSpace> templateSpaces) {
    if (templateSpaces.isEmpty())
      throw new IllegalArgumentException("At least one template is required.");
    var dimensions = new LinkedHashMap<String, Dimension>();
    dimensions.put(
        "template", new CategoricalDimension("template", List.copyOf(templateSpaces.keySet())));
    templateSpaces.forEach(
        (template, space) -> {
          for (var dimension : space.dimensions()) {
            if (dimension.name().equals("template"))
              throw new IllegalArgumentException(
                  "Template-local space must not declare the reserved dimension template.");
            var activated =
                dimension.withActivation(
                    Activation.when("template", template).and(dimension.activation()));
            var previous = dimensions.get(dimension.name());
            if (previous == null) dimensions.put(dimension.name(), activated);
            else {
              if (!previous
                  .withActivation(Activation.always())
                  .equals(dimension.withActivation(Activation.always())))
                throw new IllegalArgumentException(
                    "Conflicting definitions of ("
                        + dimension.name()
                        + "); use distinct scoped names.");
              dimensions.put(
                  dimension.name(),
                  previous.withActivation(previous.activation().or(activated.activation())));
            }
          }
        });
    var ordered = new ArrayList<Dimension>();
    var emitted = new LinkedHashSet<String>();
    while (ordered.size() < dimensions.size()) {
      boolean progressed = false;
      for (var dimension : dimensions.values()) {
        if (!emitted.contains(dimension.name())
            && emitted.containsAll(dimension.activation().dependencies())) {
          ordered.add(dimension);
          emitted.add(dimension.name());
          progressed = true;
        }
      }
      if (!progressed)
        throw new IllegalArgumentException(
            "Combined template dimensions contain conflicting activation cycles; use distinct scoped names.");
    }
    return new SearchSpace(version, ordered);
  }

  public Map<String, Object> toMap() {
    var encoded = new ArrayList<Map<String, Object>>();
    for (var dimension : dimensions) {
      var value = new LinkedHashMap<String, Object>();
      value.put("name", dimension.name());
      value.put(
          "activation",
          dimension.activation().alternatives().stream()
              .map(
                  clause -> {
                    var sorted = new java.util.TreeMap<String, List<String>>();
                    clause.forEach(
                        (name, choices) -> sorted.put(name, choices.stream().sorted().toList()));
                    return Collections.unmodifiableMap(sorted);
                  })
              .toList());
      switch (dimension) {
        case CategoricalDimension category -> {
          value.put("type", "categorical");
          value.put("choices", category.choices());
          value.put("weights", category.weights().stream().map(Double::toHexString).toList());
        }
        case IntegerDimension integer -> {
          value.put("type", "integer");
          value.put("lower", Long.toString(integer.lower()));
          value.put("upper", Long.toString(integer.upper()));
          value.put("step", Long.toString(integer.step()));
          value.put("prior", integer.prior().name());
        }
        case RealDimension real -> {
          value.put("type", "real");
          value.put("lower", Double.toHexString(real.lower()));
          value.put("upper", Double.toHexString(real.upper()));
          value.put("prior", real.prior().name());
        }
        case FixedDimension fixed -> {
          value.put("type", "fixed");
          value.put(
              "value",
              ParameterAssignment.of(Map.of(fixed.name(), fixed.value()))
                  .toMap()
                  .get(fixed.name()));
        }
      }
      encoded.add(Collections.unmodifiableMap(value));
    }
    return Map.of("version", version, "dimensions", List.copyOf(encoded));
  }

  public String canonicalForm() {
    var result = new StringBuilder();
    appendCanonical(result, toMap());
    return result.toString();
  }

  private static void appendCanonical(StringBuilder result, Object value) {
    if (value instanceof Map<?, ?> map) {
      result.append('m').append(map.size()).append(':');
      map.entrySet().stream()
          .sorted(java.util.Comparator.comparing(entry -> (String) entry.getKey()))
          .forEach(
              entry -> {
                appendCanonical(result, entry.getKey());
                appendCanonical(result, entry.getValue());
              });
    } else if (value instanceof List<?> list) {
      result.append('l').append(list.size()).append(':');
      list.forEach(element -> appendCanonical(result, element));
    } else if (value instanceof String text)
      result.append('s').append(text.length()).append(':').append(text);
    else throw new IllegalArgumentException("Unsupported canonical schema value (" + value + ").");
  }
}
