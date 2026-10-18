package greycos.solver.jackson.api.score.analysis;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;

/** This subclass intentionally implements only hooks present before raw analysis. */
public final class LegacyRawAnalysisDeserializerFixture
    extends AbstractScoreAnalysisJacksonDeserializer<SimpleScore> {
  public int oldHookCalls;
  public ConstraintRef lastConstraint;
  public SimpleScore lastScore;
  public final LegacyJustification justification = new LegacyJustification("legacy");

  public record LegacyJustification(String reason) implements ConstraintJustification {}

  @Override
  protected SimpleScore parseScore(String scoreString) {
    return SimpleScore.parseScore(scoreString);
  }

  @Override
  protected ConstraintJustification deserializeConstraintJustification(
      ConstraintRef ref, JsonNode node, DeserializationContext context, SimpleScore score)
      throws JacksonException {
    oldHookCalls++;
    lastConstraint = ref;
    lastScore = score;
    return justification;
  }

  public static void main(String[] args) throws JacksonException {
    if (args.length == 1 && args[0].equals("inventory")) {
      var api = AbstractScoreAnalysisJacksonDeserializer.class;
      Arrays.stream(api.getDeclaredMethods())
          .filter(
              method ->
                  Modifier.isPublic(method.getModifiers())
                      || Modifier.isProtected(method.getModifiers()))
          .map(
              method ->
                  api.getName()
                      + "|"
                      + (Modifier.isAbstract(method.getModifiers()) ? "abstract" : "concrete")
                      + "|"
                      + (Modifier.isStatic(method.getModifiers()) ? "static" : "instance")
                      + "|"
                      + method.getName()
                      + "("
                      + Arrays.stream(method.getParameterTypes())
                          .map(Class::getName)
                          .collect(Collectors.joining(","))
                      + "):"
                      + method.getReturnType().getName())
          .sorted()
          .forEach(System.out::println);
    } else {
      var fixture = new LegacyRawAnalysisDeserializerFixture();
      var ref = ConstraintRef.of("legacy");
      if (fixture.deserializeConstraintJustification(ref, null, null, SimpleScore.ONE)
              != fixture.justification
          || fixture.oldHookCalls != 1
          || fixture.lastConstraint != ref
          || fixture.lastScore != SimpleScore.ONE
          || !fixture.parseScore("1").equals(SimpleScore.ONE)) {
        throw new AssertionError("The old deserializer hook changed.");
      }
      System.out.println("Legacy deserializer hook passed.");
    }
  }
}
