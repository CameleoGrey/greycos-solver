/**
 * Stable contracts for constructing a candidate through ordered stages. Stages select recorded
 * operations through a solver-owned evaluator. Successful probes and validation rejections with
 * normal rollback restore their enclosing stage. Exceptional recovery, cancellation or exhaustion
 * of the probe budget aborts the candidate and restores its original baseline. The solver replays
 * selected operations without invoking application callbacks again.
 *
 * <p>Working solutions and their objects are read-only outside these operations. Providers belong
 * to one working solution and must not share mutable state with other providers or threads.
 */
@org.jspecify.annotations.NullMarked
package greycos.solver.core.api.solver.multistage;
