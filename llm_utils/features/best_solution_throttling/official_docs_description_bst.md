Throttling best solution events in SolverManager

This feature helps you avoid overloading your system with best solution events, especially in the early phase of the solving process when the solver is typically improving the solution very rapidly.

To enable event throttling, use ThrottlingBestSolutionEventConsumer when starting a new SolverJob using SolverManager:

...
import ai.timefold.solver.enterprise.core.api.ThrottlingBestSolutionEventConsumer;
import java.time.Duration;
...

public class TimetableService {

    private SolverManager<Timetable, Long> solverManager;

    public String solve(Timetable problem) {
        var bestSolutionEventConsumer = ThrottlingBestSolutionEventConsumer.of(
            event -> {
               // Your custom event handling code goes here.
            },
            Duration.ofSeconds(1)); // Throttle to 1 event per second.

        String jobId = ...;
        solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblem(problem)
                .withBestSolutionEventConsumer(bestSolutionEventConsumer)
                .run(); // Start the solver job and listen to best solutions, with throttling.
        return jobId;
    }

}
java
Copy
This will ensure that your system will never receive more than one best solution event per second. Some other important points to note:

If multiple events arrive during the pre-defined 1-second interval, only the last event will be delivered.

When the SolverJob terminates, the last event received will be delivered regardless of the throttle, unless it was already delivered before.

If your consumer throws an exception, we will still count the event as delivered.

If the system is too occupied to start and execute new threads, event delivery will be delayed until a thread can be started.

If you are using the ThrottlingBestSolutionEventConsumer for intermediate best solutions together with a final best solution consumer, both these consumers will receive the final best solution.

/**
* Submits a planning problem to solve and returns immediately. The planning problem is solved on
* a solver {@link Thread}, as soon as one is available.
*
* <p>When the solver finds a new best solution, the {@code bestSolutionConsumer} is called every
* time, on a consumer {@link Thread}, as soon as one is available (taking into account any
* throttling waiting time), unless a newer best solution is already available by then (in which
* case skip ahead discards it).
*
* <p>Defaults to logging exceptions as an error.
*
* <p>To stop a solver job before it naturally terminates, call {@link #terminateEarly(Object)}.
*
* @param problemId a ID for each planning problem. This must be unique. Use this problemId to
*     {@link #terminateEarly(Object) terminate} the solver early, {@link #getSolverStatus(Object)
*     to get the status} or if the problem changes while solving.
* @param problemFinder a function that returns a {@link PlanningSolution}, usually with
*     uninitialized planning variables
* @param bestSolutionConsumer called multiple times, on a consumer thread
* @deprecated It is recommended to use {@link #solveBuilder()} while also providing a consumer
*     for the best solution
*/