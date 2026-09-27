/**
 * Solver implementation classes including throttling support for best solution events.
 *
 * <h2>Best Solution Throttling</h2>
 *
 * <p>Throttling prevents system overload during rapid solution improvement phases by limiting the
 * rate at which best solution events are delivered. Use throttling when the solver produces many
 * events per second, processing is expensive, or you only need periodic progress tracking.
 *
 * <p>The throttling consumer implements a skip-ahead strategy: events arriving within the throttle
 * interval overwrite previous pending events, only the last event in each interval is delivered,
 * and the final best solution is always delivered regardless of the throttle interval.
 *
 * <p>Managed jobs throttle delivery on their consumer executor and acknowledge problem changes
 * after the delegate returns. Each job has independent throttling state. Standalone wrappers
 * implement AutoCloseable to drain pending events and release their scheduler.
 *
 * @since 1.30.0
 */
package greycos.solver.core.impl.solver;
