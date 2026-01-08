/**
 * Includes support for deserialization of {@link
 * ai.greycos.solver.core.preview.api.domain.solution.diff.PlanningSolutionDiff}. The serialization
 * happens automatically, if the user has registered {@link
 * ai.greycos.solver.jackson.api.GreyCOSJacksonModule} with their {@link
 * com.fasterxml.jackson.databind.ObjectMapper}.
 *
 * <p>Deserialization is not implemented, on account of losing the information about the type of the
 * solution, its entities and values.
 */
package ai.greycos.solver.jackson.preview.api.domain.solution.diff;
