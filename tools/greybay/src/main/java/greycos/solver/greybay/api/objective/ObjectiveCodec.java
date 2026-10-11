package greycos.solver.greybay.api.objective;

/**
 * Deterministic, versioned encodings for a study objective. Implementations reject unsupported
 * versions and malformed values; arbitrary Java object serialization is not a study format.
 */
public interface ObjectiveCodec {
  String encodeValue(ObjectiveValue value);

  ObjectiveValue decodeValue(String encoded);

  String encodeCalibration(ObjectiveCalibration calibration);

  ObjectiveCalibration decodeCalibration(String encoded);
}
