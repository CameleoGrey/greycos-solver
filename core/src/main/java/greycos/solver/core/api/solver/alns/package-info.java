/**
 * Stable extension contracts for adaptive large neighborhood search. All operator changes must use
 * the context's recorded mutation surface; working objects and contexts must not be shared between
 * islands or retained beyond their trial. Problem facts are read-only.
 */
@org.jspecify.annotations.NullMarked
package greycos.solver.core.api.solver.alns;
