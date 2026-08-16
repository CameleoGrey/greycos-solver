/**
 * Includes support for serialization of {@link
 * greycos.solver.core.preview.api.cotwin.solution.diff.PlanningSolutionDiff}. The serialization
 * happens automatically, if the user has registered {@link
 * greycos.solver.quarkus.jackson.GreyCOSJacksonModule} with their {@link
 * com.fasterxml.jackson.databind.ObjectMapper}.
 *
 * <p>Deserialization is not implemented, on account of losing the information about the type of the
 * solution, its entities and values.
 */
package greycos.solver.quarkus.jackson.diff;
