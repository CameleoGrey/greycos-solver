package greycos.solver.core.api.score;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Manual fixed-work arithmetic benchmark; never run as an ordinary test.
 *
 * <p>Run this main class on the core test classpath, optionally passing iterations, warmups and
 * repetitions (defaults: 50000, 2, 3). Every variant replaces the same 64 active contributions,
 * reads every 16 updates and checks every read against an independent exact decimal oracle. Integer
 * traces have equal mathematical results across all four numeric types. Fractional traces compare
 * each floating precision against its own represented-input, rounded-contribution oracle.
 *
 * <p>Output is CSV with update throughput, current-thread allocated bytes, process-wide GC deltas
 * and a verified checksum. The measurements cover these arithmetic operations and allocations; they
 * are not whole-solver performance results. Run in an otherwise idle JVM and report all
 * repetitions. Allocation is -1 when the VM does not expose the allocation MXBean.
 */
public final class FloatingScoreArithmeticBenchmark {

  private static final int WINDOW = 64;
  private static volatile long checksumSink;

  private FloatingScoreArithmeticBenchmark() {}

  public static void main(String[] args) {
    int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 50000;
    int warmups = args.length > 1 ? Integer.parseInt(args[1]) : 2;
    int repetitions = args.length > 2 ? Integer.parseInt(args[2]) : 3;
    if (iterations < 16 || warmups < 0 || repetitions < 1) {
      throw new IllegalArgumentException(
          "Require iterations >= 16, warmups >= 0 and repetitions >= 1.");
    }
    var workloads = new ArrayList<Workload>();
    var integral = trace(iterations, false);
    var fractional = trace(iterations, true);
    for (String type : List.of("long", "BigDecimal", "Float", "Double")) {
      workloads.add(new Workload(type, false, integral, oracle(integral, type, false)));
    }
    for (String type : List.of("Float", "Double")) {
      workloads.add(new Workload(type, true, fractional, oracle(fractional, type, true)));
    }
    for (int i = 0; i < warmups; i++) {
      for (var workload : workloads) {
        run(workload);
      }
    }
    var allocation = new AllocationMeter();
    System.out.println(
        "type,trace,repetition,updates,reads,elapsed_ns,updates_per_second,allocated_bytes,bytes_per_update,gc_count,gc_millis,checksum,parity");
    for (int repetition = 0; repetition < repetitions; repetition++) {
      for (int offset = 0; offset < workloads.size(); offset++) {
        var workload = workloads.get((offset + repetition) % workloads.size());
        long gcCountBefore = gcCount();
        long gcMillisBefore = gcMillis();
        long allocatedBefore = allocation.bytes();
        long start = System.nanoTime();
        long checksum = run(workload);
        long elapsed = System.nanoTime() - start;
        long allocatedAfter = allocation.bytes();
        long bytes =
            allocatedBefore < 0 || allocatedAfter < 0 ? -1L : allocatedAfter - allocatedBefore;
        System.out.printf(
            Locale.ROOT,
            "%s,%s,%d,%d,%d,%d,%.3f,%d,%.3f,%d,%d,%d,true%n",
            workload.type(),
            workload.fractional() ? "fractional" : "integer-exact",
            repetition,
            iterations,
            workload.expected().length,
            elapsed,
            iterations * 1_000_000_000.0 / elapsed,
            bytes,
            bytes < 0 ? -1.0 : (double) bytes / iterations,
            gcCount() - gcCountBefore,
            gcMillis() - gcMillisBefore,
            checksum);
      }
    }
  }

  private static long run(Workload workload) {
    State state =
        switch (workload.type()) {
          case "long" -> new LongState();
          case "BigDecimal" -> new DecimalState();
          case "Float" -> new FloatState();
          case "Double" -> new DoubleState();
          default -> throw new IllegalArgumentException(workload.type());
        };
    long checksum = 1L;
    int read = 0;
    var trace = workload.trace();
    for (int i = 0; i < trace.length; i++) {
      state.replace(i % WINDOW, trace[i]);
      if ((i & 15) == 15 || i == trace.length - 1) {
        double value = state.read();
        long bits = encode(value, workload.type(), workload.fractional());
        if (bits != workload.expected()[read]) {
          throw new IllegalStateException(
              "Arithmetic parity failed for "
                  + workload.type()
                  + " at update "
                  + i
                  + ": actual bits="
                  + bits
                  + ", expected bits="
                  + workload.expected()[read]);
        }
        checksum = 31L * checksum + bits;
        read++;
      }
    }
    checksumSink = checksum;
    return checksum;
  }

  private static Operation[] trace(int size, boolean fractional) {
    var trace = new Operation[size];
    for (int i = 0; i < size; i++) {
      double weight = (i * 37L % 511L) - 255L;
      if (fractional) {
        weight *= 0.1;
        if (i % 97 == 0) {
          weight = Math.scalb(i % 2 == 0 ? 1.0 : -1.0, 40);
        }
      }
      long longFactor = (i * 13L % 7L) - 3L;
      float floatFactor = fractional ? (float) (longFactor * 0.1) : longFactor;
      double doubleFactor = fractional ? longFactor / 3.0 : longFactor;
      trace[i] = new Operation(weight, i % 3, longFactor, floatFactor, doubleFactor);
    }
    return trace;
  }

  private static long[] oracle(Operation[] trace, String type, boolean fractional) {
    var expected = new long[(trace.length + 15) / 16];
    var active = new BigDecimal[WINDOW];
    Arrays.fill(active, BigDecimal.ZERO);
    var sum = BigDecimal.ZERO;
    int read = 0;
    for (int i = 0; i < trace.length; i++) {
      var operation = trace[i];
      var factor =
          switch (operation.kind()) {
            case 0 -> BigDecimal.valueOf(operation.longFactor());
            case 1 -> new BigDecimal((double) operation.floatFactor());
            default -> new BigDecimal(operation.doubleFactor());
          };
      var weight =
          new BigDecimal(
              type.equals("Float") ? (double) (float) operation.weight() : operation.weight());
      var exactProduct = weight.multiply(factor);
      var contribution =
          switch (type) {
            case "Float" -> new BigDecimal((double) exactProduct.floatValue());
            case "Double" -> new BigDecimal(exactProduct.doubleValue());
            default -> exactProduct;
          };
      int slot = i % WINDOW;
      sum = sum.subtract(active[slot]).add(contribution);
      active[slot] = contribution;
      if ((i & 15) == 15 || i == trace.length - 1) {
        double value = type.equals("Float") ? sum.floatValue() : sum.doubleValue();
        expected[read++] = encode(value, type, fractional);
      }
    }
    return expected;
  }

  private static long encode(double value, String type, boolean fractional) {
    if (!fractional) {
      return (long) value;
    }
    return type.equals("Float")
        ? Float.floatToRawIntBits((float) value)
        : Double.doubleToRawLongBits(value);
  }

  private interface State {
    void replace(int slot, Operation operation);

    double read();
  }

  private static final class LongState implements State {
    private SimpleScore sum = SimpleScore.ZERO;
    private final SimpleScore[] active = new SimpleScore[WINDOW];

    private LongState() {
      Arrays.fill(active, SimpleScore.ZERO);
    }

    public void replace(int slot, Operation operation) {
      var contribution =
          SimpleScore.of(Math.multiplyExact((long) operation.weight(), operation.longFactor()));
      sum = sum.subtract(active[slot]).add(contribution);
      active[slot] = contribution;
    }

    public double read() {
      return sum.score();
    }
  }

  private static final class DecimalState implements State {
    private SimpleBigDecimalScore sum = SimpleBigDecimalScore.ZERO;
    private final SimpleBigDecimalScore[] active = new SimpleBigDecimalScore[WINDOW];

    private DecimalState() {
      Arrays.fill(active, SimpleBigDecimalScore.ZERO);
    }

    public void replace(int slot, Operation operation) {
      var contribution =
          SimpleBigDecimalScore.of(
              BigDecimal.valueOf((long) operation.weight())
                  .multiply(BigDecimal.valueOf(operation.longFactor())));
      sum = sum.subtract(active[slot]).add(contribution);
      active[slot] = contribution;
    }

    public double read() {
      return sum.score().doubleValue();
    }
  }

  private static final class FloatState implements State {
    private final FloatingScoreAccumulator<SimpleFloatScore> sum =
        FloatingScoreAccumulator.create(SimpleFloatScore.ZERO);
    private final SimpleFloatScore[] active = new SimpleFloatScore[WINDOW];

    private FloatState() {
      Arrays.fill(active, SimpleFloatScore.ZERO);
    }

    public void replace(int slot, Operation operation) {
      sum.subtract(active[slot]);
      var weight = SimpleFloatScore.of((float) operation.weight());
      active[slot] =
          switch (operation.kind()) {
            case 0 -> sum.addWeighted(weight, operation.longFactor());
            case 1 -> sum.addWeighted(weight, operation.floatFactor());
            default -> sum.addWeighted(weight, operation.doubleFactor());
          };
    }

    public double read() {
      return sum.extractScore().score();
    }
  }

  private static final class DoubleState implements State {
    private final FloatingScoreAccumulator<SimpleDoubleScore> sum =
        FloatingScoreAccumulator.create(SimpleDoubleScore.ZERO);
    private final SimpleDoubleScore[] active = new SimpleDoubleScore[WINDOW];

    private DoubleState() {
      Arrays.fill(active, SimpleDoubleScore.ZERO);
    }

    public void replace(int slot, Operation operation) {
      sum.subtract(active[slot]);
      var weight = SimpleDoubleScore.of(operation.weight());
      active[slot] =
          switch (operation.kind()) {
            case 0 -> sum.addWeighted(weight, operation.longFactor());
            case 1 -> sum.addWeighted(weight, operation.floatFactor());
            default -> sum.addWeighted(weight, operation.doubleFactor());
          };
    }

    public double read() {
      return sum.extractScore().score();
    }
  }

  private record Operation(
      double weight, int kind, long longFactor, float floatFactor, double doubleFactor) {}

  private record Workload(String type, boolean fractional, Operation[] trace, long[] expected) {}

  private static long gcCount() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0L, bean.getCollectionCount()))
        .sum();
  }

  private static long gcMillis() {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0L, bean.getCollectionTime()))
        .sum();
  }

  private static final class AllocationMeter {
    private final Object bean;
    private final Method allocatedBytes;

    private AllocationMeter() {
      Object candidate = null;
      Method method = null;
      try {
        var type = Class.forName("com.sun.management.ThreadMXBean");
        candidate = ManagementFactory.getThreadMXBean();
        if (type.isInstance(candidate)
            && (boolean) type.getMethod("isThreadAllocatedMemorySupported").invoke(candidate)) {
          type.getMethod("setThreadAllocatedMemoryEnabled", boolean.class).invoke(candidate, true);
          method = type.getMethod("getThreadAllocatedBytes", long.class);
        }
      } catch (ReflectiveOperationException | SecurityException unavailable) {
        candidate = null;
      }
      bean = candidate;
      allocatedBytes = method;
    }

    private long bytes() {
      if (allocatedBytes == null) {
        return -1L;
      }
      try {
        return (long) allocatedBytes.invoke(bean, Thread.currentThread().threadId());
      } catch (ReflectiveOperationException unavailable) {
        return -1L;
      }
    }
  }
}
