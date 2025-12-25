/**
 * Solver implementation classes including throttling support for best solution events.
 *
 * <h2>Best Solution Throttling</h2>
 *
 * <p>The {@link ai.greycos.solver.core.impl.solver.ThrottlingBestSolutionEventConsumer} provides
 * throttling functionality to prevent system overload during rapid solution improvement phases.
 *
 * <h3>When to Use Throttling</h3>
 *
 * <p>Throttling is beneficial when:
 *
 * <ul>
 *   <li>The solver produces hundreds of best solution events per second
 *   <li>Processing each event is expensive (e.g., database writes, network calls)
 *   <li>You only need to track progress periodically rather than continuously
 *   <li>The consumer system has limited capacity or resources
 * </ul>
 *
 * <h3>How Throttling Works</h3>
 *
 * <p>The throttling consumer implements a skip-ahead strategy:
 *
 * <ol>
 *   <li>Events arriving within the throttle interval overwrite previous pending events
 *   <li>Only the last event in each interval is delivered to the delegate consumer
 *   <li>The final best solution is always delivered regardless of throttle interval
 *   <li>Consumer exceptions don't affect throttle timing
 * </ol>
 *
 * <h3>Basic Usage</h3>
 *
 * <pre>{@code
 * SolverManager<TestdataSolution, Long> solverManager =
 *     SolverManager.create(solverConfig);
 *
 * var throttledConsumer = ThrottlingBestSolutionEventConsumer.of(
 *     event -> {
 *         // Handle best solution
 *         System.out.println("Score: " + event.solution().getScore());
 *     },
 *     Duration.ofSeconds(1) // Throttle to 1 event per second
 * );
 *
 * solverManager.solveBuilder()
 *     .withProblemId(1L)
 *     .withProblem(problem)
 *     .withBestSolutionEventConsumer(throttledConsumer)
 *     .run();
 * }</pre>
 *
 * <h3>Using Builder Method</h3>
 *
 * <pre>{@code
 * solverManager.solveBuilder()
 *     .withProblemId(1L)
 *     .withProblem(problem)
 *     .withThrottledBestSolutionEventConsumer(
 *         event -> handleBestSolution(event),
 *         Duration.ofMillis(500)
 *     )
 *     .run();
 * }</pre>
 *
 * <h3>With Final Consumer</h3>
 *
 * <pre>{@code
 * solverManager.solveBuilder()
 *     .withProblemId(1L)
 *     .withProblem(problem)
 *     .withThrottledBestSolutionEventConsumer(
 *         event -> trackProgress(event),
 *         Duration.ofSeconds(1)
 *     )
 *     .withFinalBestSolutionEventConsumer(
 *         event -> saveFinalSolution(event.solution())
 *     )
 *     .run();
 * }</pre>
 *
 * <h3>Performance Characteristics</h3>
 *
 * <ul>
 *   <li><strong>Time complexity:</strong> O(1) per event (simple atomic reference update)
 *   <li><strong>Space complexity:</strong> O(1) (single pending event reference)
 *   <li><strong>Overhead:</strong> Minimal - one scheduler thread per consumer instance
 * </ul>
 *
 * <h3>Thread Safety</h3>
 *
 * <p>The throttling consumer is thread-safe and can handle concurrent event submission from
 * multiple threads. It uses atomic references and volatile flags to ensure consistency.
 *
 * <h3>Resource Management</h3>
 *
 * <p>The consumer implements {@link AutoCloseable} and should be closed when no longer needed to
 * ensure proper cleanup of scheduler resources. When used with {@link
 * ai.greycos.solver.core.api.solver.SolverManager}, the consumer is automatically closed when the
 * solver job terminates.
 *
 * @since 1.30.0
 */
package ai.greycos.solver.core.impl.solver;
