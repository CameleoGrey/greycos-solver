package greycos.solver.core.impl.nodesharing.external;

import greycos.solver.core.testcotwin.TestdataEntity;

/** Helpers outside the provider package exercise transitive bytecode analysis. */
public final class ExternalStackObservationHelper {

  public static int initializationCount;

  private ExternalStackObservationHelper() {}

  public static boolean selected(TestdataEntity entity) {
    return callerName().contains("$" + entity.getCode() + "$");
  }

  private static String callerName() {
    return new Throwable().getStackTrace()[2].getMethodName();
  }

  public static String observedCaller() {
    return new Throwable().getStackTrace()[1].getMethodName();
  }

  public static boolean recursiveObservationFirst(int depth) {
    if (depth > 0) {
      return recursiveObservationSecond(depth - 1);
    }
    return new Throwable().getStackTrace().length > 0;
  }

  public static boolean recursiveObservationSecond(int depth) {
    return depth > 0 && recursiveObservationFirst(depth - 1);
  }

  public static boolean even(int value) {
    return value == 0 || odd(value - 1);
  }

  private static boolean odd(int value) {
    return value != 0 && even(value - 1);
  }

  public static class UninitializedHelper {
    static {
      initializationCount++;
    }

    public static boolean matches(String value) {
      return !value.isEmpty();
    }
  }
}
