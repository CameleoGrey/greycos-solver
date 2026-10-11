#!/usr/bin/env python3
"""Run the predeclared bounded panel, preserving every result and failure."""

import argparse
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import shutil
import signal
import subprocess
import sys
import time
from datetime import datetime, timezone


MAIN = "greycos.solver.core.impl.islandmodel.resumable.ResumableSearchBenchmark"
MODES = ("SERIAL", "MOVE_THREADS", "ISLANDS", "INDEPENDENT_ISLANDS", "SESSIONS", "COOPERATIVE")
FAMILIES = (("basic", 100), ("list", 80), ("timetable", 48))
PARITY_FIELDS = ("trace_sha256", "state_sha256", "best_score", "completed_steps", "consumed_moves",
                 "reported_score_calculations", "budget_overshoot")


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def read_command(command, cwd, environment):
    result = subprocess.run(command, cwd=cwd, env=environment, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, check=True, timeout=30)
    return result.stdout.strip()


def panel():
    cases = []
    for family, size in FAMILIES:
        for controller in ("la", "dla"):
            for seed in (0, 37):
                for mode in ("SESSIONS", "COOPERATIVE"):
                    for workers in (1, 2, 4):
                        cases.append(dict(kind="fixed", workload=family, size=size,
                                          controller=controller, seed=seed, mode=mode,
                                          workers=workers, width=4, quantum=8, unit="steps",
                                          budget=259, warmup=0, trace=True))
    cell = 0
    for family, size in FAMILIES:
        for controller in ("la", "dla"):
            for seed in (0, 37):
                rotation = cell % len(MODES)
                for mode in MODES[rotation:] + MODES[:rotation]:
                    cases.append(dict(kind="timed", workload=family, size=size,
                                      controller=controller, seed=seed, mode=mode,
                                      workers=4, width=4, quantum=8, unit="seconds",
                                      budget=3, warmup=1, trace=False))
                cell += 1
    for index, case in enumerate(cases):
        case["id"] = f"{index:03d}-{case['kind']}-{case['workload']}-{case['controller']}-{case['seed']}-{case['mode']}-p{case['workers']}"
    return cases


def stop_process(process):
    if process.poll() is not None:
        return
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=2)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait(timeout=5)


def parse_result(path):
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    headers = [index for index, line in enumerate(lines) if line.startswith("workload,mode,controller,")]
    if len(headers) != 1:
        raise ValueError(f"Expected one CSV header, got {len(headers)}")
    records = list(csv.DictReader(io.StringIO("\n".join(lines[headers[0]:]))))
    if len(records) != 1 or records[0].get("validation") != "passed":
        raise ValueError("Expected one independently validated benchmark row")
    if any(field not in records[0] for field in PARITY_FIELDS):
        raise ValueError("The benchmark row is missing required parity fields")
    return records[0]


def run_case(case, command, repo, output, environment, watchdog):
    stdout = output / "raw" / (case["id"] + ".stdout")
    stderr = output / "raw" / (case["id"] + ".stderr")
    result = dict(case_id=case["id"], status="not_started", command=command,
                  stdout=str(stdout.relative_to(output)), stderr=str(stderr.relative_to(output)))
    started = time.monotonic()
    process = None
    try:
        with stdout.open("wb") as out, stderr.open("wb") as err:
            process = subprocess.Popen(command, cwd=repo, env=environment, stdin=subprocess.DEVNULL,
                                       stdout=out, stderr=err, start_new_session=True)
            result["pid"] = process.pid
            try:
                process.wait(timeout=watchdog)
                result["status"] = "passed" if process.returncode == 0 else "failed"
            except subprocess.TimeoutExpired:
                result["status"] = "timeout"
                stop_process(process)
            result["exit_code"] = process.returncode
        if result["status"] == "passed":
            result["measurement"] = parse_result(stdout)
    except KeyboardInterrupt:
        if process is not None:
            stop_process(process)
        result["status"] = "interrupted"
    except Exception as failure:
        if process is not None:
            stop_process(process)
        result["status"] = "failed"
        result["error"] = f"{type(failure).__name__}: {failure}"
    result["wall_seconds"] = time.monotonic() - started
    return result


def summarize(cases, results, output):
    by_id = {result["case_id"]: result for result in results}
    groups = {}
    for case in cases:
        if case["kind"] == "fixed":
            key = (case["workload"], case["controller"], case["seed"], case["mode"])
            groups.setdefault(key, []).append(case)
    comparisons = []
    for key, members in groups.items():
        rows = [by_id[case["id"]] for case in members]
        passed = all(row["status"] == "passed" for row in rows)
        mismatches = []
        if passed:
            for field in PARITY_FIELDS:
                values = [row["measurement"][field] for row in rows]
                if len(set(values)) != 1 or (field == "trace_sha256" and not values[0]):
                    mismatches.append(dict(field=field, values=values))
        comparisons.append(dict(group=list(key), case_ids=[case["id"] for case in members],
                                status="passed" if passed and not mismatches else "failed",
                                mismatches=mismatches))
    statuses = {}
    for result in results:
        statuses[result["status"]] = statuses.get(result["status"], 0) + 1
    summary = dict(status_counts=statuses, fixed_trace_comparisons=comparisons,
                   complete=len(results) == len(cases),
                   passed=all(result["status"] == "passed" for result in results)
                   and all(item["status"] == "passed" for item in comparisons))
    write_json(output / "summary.json", summary)
    measurements = [dict(case_id=result["case_id"], **result["measurement"])
                    for result in results if "measurement" in result]
    if measurements:
        with (output / "measurements.csv").open("w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=list(measurements[0]))
            writer.writeheader()
            writer.writerows(measurements)
    report = ["# Bounded resumable-search execution evidence", "",
              "These short runs qualify execution and reporting. They do not establish scalability,",
              "search superiority, adoption thresholds, or production defaults.", "",
              f"Planned: {len(cases)} runs (72 fixed-work traces and 72 timed runs).",
              f"Statuses: `{json.dumps(statuses, sort_keys=True)}`.",
              f"Fixed logical trace groups passing: {sum(item['status'] == 'passed' for item in comparisons)}/{len(comparisons)}.", "",
              "[Manifest](manifest.json), [artifact inventory](artifacts.json), [all run statuses](results.jsonl),",
              "[raw measurements](measurements.csv), and [comparison details](summary.json).", "",
              "The manifest was written before the first solver run. Failures, timeouts and runs",
              "not started are retained. No unfavorable result was selectively retried.", ""]
    for result in results:
        if result["status"] != "passed":
            report.append(f"- `{result['case_id']}`: {result['status']}; "
                          f"[stdout]({result.get('stdout', 'raw/')}) / [stderr]({result.get('stderr', 'raw/')}).")
    (output / "report.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    return summary["passed"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--classpath-file", type=Path, default=Path("core/target/resumable-benchmark-classpath.txt"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--heap", default="512m")
    parser.add_argument("--watchdog-seconds", type=float, default=30)
    parser.add_argument("--campaign-timeout-seconds", type=float, default=1800)
    args = parser.parse_args()
    if args.watchdog_seconds <= 0 or args.campaign_timeout_seconds <= 0:
        parser.error("Watchdog and campaign limits must be positive")
    repo = args.repo.resolve()
    output = args.output.resolve()
    environment = dict(os.environ)
    for name in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
        environment.pop(name, None)
    java = Path(shutil.which(args.java) or args.java).resolve()
    classpath_file = (repo / args.classpath_file).resolve()
    entries = [repo / "core/target/test-classes", repo / "core/target/classes"]
    entries.extend(Path(entry) for entry in classpath_file.read_text().strip().split(os.pathsep) if entry)
    entries = list(dict.fromkeys((repo / entry).resolve() for entry in entries))
    if any(not entry.exists() for entry in entries):
        raise FileNotFoundError("Every classpath entry must exist before qualification")
    classpath = os.pathsep.join(map(str, entries))
    revision = read_command(["git", "rev-parse", "HEAD"], repo, environment)
    status = read_command(["git", "status", "--porcelain"], repo, environment)
    source_diff = subprocess.check_output(["git", "diff", "--binary", "HEAD", "--"], cwd=repo)
    artifacts = []
    for entry in entries:
        for path in sorted(entry.rglob("*")) if entry.is_dir() else [entry]:
            if path.is_file():
                artifacts.append(dict(path=str(path), size=path.stat().st_size, sha256=digest(path)))
    cases = panel()
    base_command = [str(java), f"-Xms{args.heap}", f"-Xmx{args.heap}",
                    "-Dlogback.level.greycos.solver=error",
                    f"-Dgreycos.benchmark.revision={revision}{'-dirty' if status else ''}", "-cp", classpath, MAIN]
    for case in cases:
        case["command"] = base_command + [str(case[name]) for name in
            ("workload", "mode", "controller", "seed", "size", "workers", "width", "quantum", "unit", "budget", "warmup")]
        case["command"].append(str(case["trace"]).lower())
    output.mkdir(parents=True, exist_ok=False)
    (output / "raw").mkdir()
    (output / "source.patch").write_bytes(source_diff)
    untracked_sources = []
    paths = subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard", "-z", "--",
                                     "core/src", "tools/benchmark/src"], cwd=repo).split(b"\0")
    for raw_path in paths:
        if not raw_path:
            continue
        relative = Path(os.fsdecode(raw_path))
        source = repo / relative
        if not source.resolve().is_relative_to(repo):
            raise ValueError(f"An untracked source resolves outside the repository: {relative}")
        destination = output / "source-untracked" / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        untracked_sources.append(dict(path=str(relative), sha256=digest(destination)))
    write_json(output / "source-untracked.json", untracked_sources)
    write_json(output / "artifacts.json", artifacts)
    manifest = dict(created_utc=datetime.now(timezone.utc).isoformat(), repo=str(repo), revision=revision,
                    worktree_status=status, source_diff_sha256=hashlib.sha256(source_diff).hexdigest(),
                    untracked_sources_sha256=digest(output / "source-untracked.json"),
                    artifact_inventory_sha256=digest(output / "artifacts.json"),
                    runner_sha256=digest(Path(__file__)), java=str(java), java_sha256=digest(java),
                    java_version=read_command([str(java), "-version"], repo, environment),
                    platform=platform.uname()._asdict(), logical_cpus=os.cpu_count(),
                    cpu_affinity=sorted(os.sched_getaffinity(0)) if hasattr(os, "sched_getaffinity") else None,
                    removed_ambient_java_options=["JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"],
                    watchdog_seconds=args.watchdog_seconds, campaign_timeout_seconds=args.campaign_timeout_seconds,
                    parity_fields=PARITY_FIELDS, cases=cases)
    for name in ("/proc/cpuinfo", "/proc/meminfo", "/proc/loadavg", "/sys/fs/cgroup/cpu.max", "/sys/fs/cgroup/memory.max"):
        path = Path(name)
        if path.is_file():
            manifest[name] = path.read_text(errors="replace")
    write_json(output / "manifest.json", manifest)
    deadline = time.monotonic() + args.campaign_timeout_seconds
    results = []
    interrupted = False
    with (output / "results.jsonl").open("w", encoding="utf-8") as journal:
        for index, case in enumerate(cases):
            remaining = deadline - time.monotonic()
            if interrupted or remaining <= 0:
                result = dict(case_id=case["id"], status="not_started",
                              reason="campaign_interrupted" if interrupted else "campaign_deadline")
            else:
                print(f"[{index + 1}/{len(cases)}] {case['id']}", flush=True)
                result = run_case(case, case["command"], repo, output, environment,
                                  min(args.watchdog_seconds, remaining))
                interrupted = result["status"] == "interrupted"
            results.append(result)
            journal.write(json.dumps(result, sort_keys=True) + "\n")
            journal.flush()
    passed = summarize(cases, results, output)
    print(f"Evidence: {output / 'report.md'}; passed={passed}", flush=True)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
