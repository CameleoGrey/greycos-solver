package ai.greycos.solver.core.impl.nodesharing;

final class PackagePrivateAccessHelper {

  private PackagePrivateAccessHelper() {}

  static boolean accept(String value) {
    return value != null && value.startsWith("A");
  }
}
