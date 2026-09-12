#!/usr/bin/env python3
"""Run isolated ALNS benchmark JVMs sequentially and preserve every raw observation."""

import argparse
import csv
import io
import os
from pathlib import Path
import re
import subprocess
import sys


def result_rows(output):
    """Logging on stderr can be interleaved between the stdout CSV header and data."""
    lines = output.splitlines()
    header = next(line for line in lines if line.startswith("workload,threads,seed,"))
    for line in lines:
        if line.startswith(("basic,", "list,", "mixed,")):
            yield next(csv.DictReader(io.StringIO(header + "\n" + line)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", type=Path, required=True)
    parser.add_argument("--baseline-classpath", type=Path)
    parser.add_argument("--java", default="java")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--seeds", default="0,1,2")
    parser.add_argument("--threads", default="NONE,1,2,4,8")
    parser.add_argument("--shapes", default="basic,list,mixed")
    parser.add_argument("--modes", default="fixed,time")
    parser.add_argument("--warmup-ms", type=int, default=2000)
    parser.add_argument("--measure-ms", type=int, default=3000)
    parser.add_argument("--trials", type=int, default=60)
    parser.add_argument("--destroyed", type=int, default=6)
    parser.add_argument("--basic-size", type=int, default=400)
    parser.add_argument("--list-size", type=int, default=120)
    parser.add_argument("--mixed-size", type=int, default=120)
    parser.add_argument("--repair", default="GREEDY")
    parser.add_argument("--diagnostics", action="store_true")
    parser.add_argument("--trace", action="store_true")
    parser.add_argument("--validation", action="store_true",
                        help="Run the full eight-trial correctness matrix in one JVM per version")
    parser.add_argument("--suite", action="store_true",
                        help="Run validation, small/large GREEDY, large REGRET_2, fixed timing")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if args.suite:
        common = [sys.executable, str(Path(__file__).resolve()), "--classpath", str(args.classpath),
                  "--java", args.java, "--seeds", args.seeds, "--threads", args.threads,
                  "--warmup-ms", str(args.warmup_ms), "--measure-ms", str(args.measure_ms),
                  "--destroyed", str(args.destroyed)]
        if args.baseline_classpath:
            common += ["--baseline-classpath", str(args.baseline_classpath)]
        scenarios = [
            ("validation", ["--validation"]),
            ("small-greedy", ["--modes", "time", "--basic-size", "80", "--list-size", "60",
                              "--mixed-size", "60"]),
            ("large-greedy", ["--modes", "time", "--list-size", "500", "--mixed-size", "240"]),
            ("large-regret2", ["--modes", "time", "--shapes", "list,mixed", "--list-size", "500",
                               "--mixed-size", "240", "--repair", "REGRET_2"]),
            ("fixed-greedy", ["--modes", "fixed", "--seeds", "0", "--shapes", "list,mixed",
                              "--list-size", "500", "--mixed-size", "240", "--trials", "60"]),
            ("fixed-regret2", ["--modes", "fixed", "--seeds", "0", "--shapes", "list,mixed",
                               "--list-size", "500", "--mixed-size", "240", "--trials", "20",
                               "--repair", "REGRET_2"]),
        ]
        for name, options in scenarios:
            subprocess.run(common + ["--output", str(args.output / name)] + options, check=True)
            if name == "validation":
                subprocess.run([sys.executable,
                                str(Path(__file__).with_name("alns-move-threading-report.py")),
                                "--inputs", str(args.output / name), "--output",
                                str(args.output / name / "validation.html"), "--environment",
                                "Correctness gate only: these solves share validation JVMs."],
                               check=True)
        return
    classpaths = {"current": args.classpath.read_text().strip()}
    variants = [("current", count) for count in args.threads.split(",")]
    if args.baseline_classpath:
        classpaths["baseline"] = args.baseline_classpath.read_text().strip()
        variants.insert(0, ("baseline", "NONE"))
    if args.validation:
        rows = []
        for version in classpaths:
            threads = args.threads if version == "current" else "NONE"
            command = ["/usr/bin/time", "-v", args.java, "-Xms512m", "-Xmx2g",
                       "-cp", classpaths[version],
                       "greycos.solver.core.impl.alns.AlnsMoveThreadingBenchmark",
                       "--validate", threads, args.seeds]
            result = subprocess.run(command, text=True, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, env={**os.environ, "LC_ALL": "C"})
            (args.output / f"{version}-validation.log").write_text(result.stdout)
            if result.returncode:
                raise RuntimeError(f"{version} validation failed; inspect its preserved log")
            rss = re.search(r"Maximum resident set size \(kbytes\): (\d+)", result.stdout)
            for row in result_rows(result.stdout):
                rows.append({"version": version, "mode": "fixed",
                             "run_layout": "shared-validation-jvm", **row,
                             "peak_rss_kib": rss.group(1) if rss else ""})
            print(f"{version}: all requested independent trial traces completed", flush=True)
        with (args.output / "results.csv").open("w", newline="") as output:
            writer = csv.DictWriter(output, fieldnames=rows[0].keys())
            writer.writeheader()
            writer.writerows(rows)
        return
    rows = []
    sizes = {"basic": args.basic_size, "list": args.list_size, "mixed": args.mixed_size}
    for seed_index, seed in enumerate(args.seeds.split(",")):
        ordered = variants[seed_index % len(variants):] + variants[:seed_index % len(variants)]
        for shape in args.shapes.split(","):
            for mode in args.modes.split(","):
                for version, threads in ordered:
                    trial_limit = args.trials if mode == "fixed" else 0
                    label = f"{version}-{shape}-{threads}-seed{seed}-{mode}"
                    command = ["/usr/bin/time", "-v", args.java, "-Xms512m", "-Xmx2g"]
                    if args.diagnostics:
                        command.append("-Dgreycos.solver.moveThreadDiagnostics=true")
                    command += ["-cp", classpaths[version],
                                "greycos.solver.core.impl.alns.AlnsMoveThreadingBenchmark",
                                shape, threads, seed, str(sizes[shape]), str(args.destroyed),
                                args.repair, str(args.warmup_ms), str(args.measure_ms),
                                str(trial_limit), str(args.trace and mode == "fixed").lower()]
                    result = subprocess.run(command, text=True, stdout=subprocess.PIPE,
                                            stderr=subprocess.STDOUT,
                                            env={**os.environ, "LC_ALL": "C"})
                    (args.output / f"{label}.log").write_text(result.stdout)
                    if result.returncode:
                        raise RuntimeError(f"{label} failed; inspect its preserved log")
                    row = next(result_rows(result.stdout))
                    rss = re.search(r"Maximum resident set size \(kbytes\): (\d+)", result.stdout)
                    row = {"version": version, "mode": mode, "run_layout": "isolated-jvm", **row,
                           "peak_rss_kib": rss.group(1) if rss else ""}
                    rows.append(row)
                    with (args.output / "results.csv").open("w", newline="") as output:
                        writer = csv.DictWriter(output, fieldnames=rows[0].keys())
                        writer.writeheader()
                        writer.writerows(rows)
                    print(f"{label}: {row['solve_ms']} ms, {row['useful_probes']} probes, "
                          f"best {row['best_score']}", flush=True)


if __name__ == "__main__":
    main()
