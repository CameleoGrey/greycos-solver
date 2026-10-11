package greycos.solver.greybay.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A finite disjunction of conjunctions over earlier categorical dimensions. */
public record Activation(List<Map<String, Set<String>>> alternatives) {
  public Activation {
    Objects.requireNonNull(alternatives, "alternatives");
    var copy = new ArrayList<Map<String, Set<String>>>();
    for (var alternative : alternatives) {
      var clause = new LinkedHashMap<String, Set<String>>();
      alternative.forEach(
          (name, choices) -> {
            if (name == null
                || name.isBlank()
                || choices == null
                || choices.isEmpty()
                || choices.stream().anyMatch(Objects::isNull))
              throw new IllegalArgumentException("Activation names and choices must be nonempty.");
            clause.put(name, Collections.unmodifiableSet(new LinkedHashSet<>(choices)));
          });
      copy.add(Collections.unmodifiableMap(clause));
    }
    alternatives = List.copyOf(copy);
  }

  public static Activation always() {
    return new Activation(List.of(Map.of()));
  }

  public static Activation when(String name, String value) {
    return in(name, Set.of(value));
  }

  public static Activation in(String name, Set<String> values) {
    return new Activation(List.of(Map.of(name, values)));
  }

  public Activation and(Activation other) {
    var clauses = new ArrayList<Map<String, Set<String>>>();
    for (var left : alternatives)
      for (var right : other.alternatives) {
        var clause = new LinkedHashMap<>(left);
        boolean possible = true;
        for (var entry : right.entrySet()) {
          var choices = new LinkedHashSet<>(entry.getValue());
          if (clause.containsKey(entry.getKey())) choices.retainAll(clause.get(entry.getKey()));
          if (choices.isEmpty()) {
            possible = false;
            break;
          }
          clause.put(entry.getKey(), choices);
        }
        if (possible) clauses.add(clause);
      }
    return new Activation(clauses);
  }

  public Activation or(Activation other) {
    var clauses = new ArrayList<>(alternatives);
    clauses.addAll(other.alternatives);
    return new Activation(clauses);
  }

  public Set<String> dependencies() {
    var names = new LinkedHashSet<String>();
    alternatives.forEach(clause -> names.addAll(clause.keySet()));
    return Collections.unmodifiableSet(names);
  }

  public boolean isActive(Map<String, ?> assignment) {
    return alternatives.stream()
        .anyMatch(
            clause ->
                clause.entrySet().stream()
                    .allMatch(entry -> entry.getValue().contains(assignment.get(entry.getKey()))));
  }
}
