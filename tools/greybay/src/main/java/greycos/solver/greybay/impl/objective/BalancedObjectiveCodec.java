package greycos.solver.greybay.impl.objective;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.objective.BalancedObjectiveCalibration;
import greycos.solver.greybay.api.objective.BalancedObjectiveValue;
import greycos.solver.greybay.api.objective.ExactRational;
import greycos.solver.greybay.api.objective.ObjectiveCalibration;
import greycos.solver.greybay.api.objective.ObjectiveCodec;
import greycos.solver.greybay.api.objective.ObjectiveValue;
import greycos.solver.greybay.api.objective.ScoreLayout;

/** Strict canonical text codec. Arbitrary identifiers are UTF-8 URL-base64 without padding. */
public final class BalancedObjectiveCodec implements ObjectiveCodec {
  public static final BalancedObjectiveCodec INSTANCE = new BalancedObjectiveCodec();

  private BalancedObjectiveCodec() {}

  @Override
  public String encodeValue(ObjectiveValue value) {
    if (!(value instanceof BalancedObjectiveValue balanced)) {
      throw new IllegalArgumentException("Expected a BalancedObjectiveValue.");
    }
    var lines = new ArrayList<String>();
    lines.add("greybay-balanced-value:1");
    lines.add(balanced.feasibilityRate().toString());
    lines.add(Integer.toString(balanced.normalizedGains().size()));
    balanced.normalizedGains().forEach(gain -> lines.add(gain.toString()));
    return String.join("\n", lines);
  }

  @Override
  public BalancedObjectiveValue decodeValue(String encoded) {
    var cursor = new Cursor(encoded);
    cursor.expect("greybay-balanced-value:1");
    var feasibility = ExactRational.parse(cursor.next());
    var gains = cursor.vector(cursor.count());
    cursor.end();
    var value = new BalancedObjectiveValue(feasibility, gains);
    requireCanonical(encoded, encodeValue(value));
    return value;
  }

  @Override
  public String encodeCalibration(ObjectiveCalibration calibration) {
    if (!(calibration instanceof BalancedObjectiveCalibration balanced)) {
      throw new IllegalArgumentException("Expected a BalancedObjectiveCalibration.");
    }
    var lines = new ArrayList<String>();
    lines.add("greybay-balanced-calibration:1");
    lines.add(encodeText(balanced.layout().scoreType()));
    lines.add(Integer.toString(balanced.layout().levelCount()));
    lines.add(Integer.toString(balanced.layout().hardLevelCount()));
    lines.add(encodeText(balanced.scoreAdapterIdentity().id()));
    lines.add(encodeText(balanced.scoreAdapterIdentity().version()));
    lines.add(encodeText(balanced.scoreAdapterIdentity().artifactIdentity()));
    lines.add(Integer.toString(balanced.seeds().size()));
    balanced.seeds().forEach(seed -> lines.add(Long.toString(seed)));
    lines.add(Integer.toString(balanced.problemIds().size()));
    for (var problemId : balanced.problemIds()) {
      lines.add(encodeText(problemId));
      balanced.anchors().get(problemId).forEach(anchor -> lines.add(anchor.toString()));
      balanced.scales().get(problemId).forEach(scale -> lines.add(scale.toString()));
    }
    return String.join("\n", lines);
  }

  @Override
  public BalancedObjectiveCalibration decodeCalibration(String encoded) {
    var cursor = new Cursor(encoded);
    cursor.expect("greybay-balanced-calibration:1");
    var layout = new ScoreLayout(cursor.text(), cursor.count(), Integer.parseInt(cursor.next()));
    var adapterIdentity = new ComponentIdentity(cursor.text(), cursor.text(), cursor.text());
    var seeds = new ArrayList<Long>();
    int seedCount = cursor.count();
    for (int i = 0; i < seedCount; i++) {
      seeds.add(Long.parseLong(cursor.next()));
    }
    var problemIds = new ArrayList<String>();
    var anchors = new LinkedHashMap<String, List<ExactRational>>();
    var scales = new LinkedHashMap<String, List<ExactRational>>();
    int problemCount = cursor.count();
    for (int i = 0; i < problemCount; i++) {
      var problemId = cursor.text();
      problemIds.add(problemId);
      anchors.put(problemId, cursor.vector(layout.levelCount()));
      scales.put(problemId, cursor.vector(layout.levelCount()));
    }
    cursor.end();
    var calibration =
        new BalancedObjectiveCalibration(
            layout, adapterIdentity, problemIds, seeds, anchors, scales);
    requireCanonical(encoded, encodeCalibration(calibration));
    return calibration;
  }

  private static String encodeText(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static void requireCanonical(String actual, String canonical) {
    if (!actual.equals(canonical)) {
      throw new IllegalArgumentException("Noncanonical balanced objective encoding.");
    }
  }

  private static final class Cursor {
    private final String[] lines;
    private int index;

    private Cursor(String encoded) {
      lines = Objects.requireNonNull(encoded, "encoded").split("\n", -1);
    }

    private String next() {
      if (index == lines.length) {
        throw new IllegalArgumentException(
            "Truncated balanced objective encoding at field " + index + ".");
      }
      return lines[index++];
    }

    private String text() {
      return new String(Base64.getUrlDecoder().decode(next()), StandardCharsets.UTF_8);
    }

    private int count() {
      int count = Integer.parseInt(next());
      if (count <= 0 || count > lines.length - index) {
        throw new IllegalArgumentException(
            "Invalid balanced objective field count (" + count + ").");
      }
      return count;
    }

    private List<ExactRational> vector(int count) {
      if (count > lines.length - index) {
        throw new IllegalArgumentException("Truncated balanced objective vector.");
      }
      var vector = new ArrayList<ExactRational>(count);
      for (int i = 0; i < count; i++) {
        vector.add(ExactRational.parse(next()));
      }
      return List.copyOf(vector);
    }

    private void expect(String value) {
      if (!next().equals(value)) {
        throw new IllegalArgumentException(
            "Unsupported balanced objective codec version; expected " + value + ".");
      }
    }

    private void end() {
      if (index != lines.length) {
        throw new IllegalArgumentException("Trailing fields in balanced objective encoding.");
      }
    }
  }
}
