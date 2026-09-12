#!/usr/bin/env python3
"""Summarize ALNS observations and reject incompatible fixed-work comparisons."""

import argparse
from collections import defaultdict
import csv
from html import escape
from pathlib import Path
import os
import statistics


def median(rows, field):
    return statistics.median(float(row[field]) for row in rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inputs", nargs="+", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--environment", default="Hardware/environment details were not supplied.")
    args = parser.parse_args()
    rows = []
    for path in args.inputs:
        if path.is_dir():
            path = path / "results.csv"
        with path.open() as source:
            for row in csv.DictReader(source):
                row["source_csv"] = os.path.relpath(path, args.output.parent)
                log_name = (row["version"] + "-validation.log"
                            if row.get("run_layout") == "shared-validation-jvm" else
                            f"{row['version']}-{row['workload']}-{row['threads']}-seed{row['seed']}-{row['mode']}.log")
                row["source_log"] = os.path.relpath(path.parent / log_name, args.output.parent)
                rows.append(row)
    for row in rows:
        row.setdefault("run_layout", "unspecified")
        row.setdefault("warmup_termination", "none" if row["warmup_ms"] == "0" else "time")
        row["timing_status"] = (
            "elapsed-shorter-than-phase-budget"
            if row["mode"] == "time" and float(row["solve_ms"]) < .9 * float(row["budget_ms"])
            else "ok")
    fixed = defaultdict(list)
    for row in rows:
        if row["mode"] == "fixed":
            key = tuple(row[field] for field in
                        ("workload", "size", "destroyed", "repair", "seed", "trial_limit"))
            fixed[key].append(row)
    checked = 0
    for key, group in fixed.items():
        for field in ("best_score", "state_sha256", "useful_probes", "trials"):
            values = {row[field] for row in group}
            if len(values) != 1:
                raise ValueError(f"Fixed-work mismatch for {key}, {field}: {values}")
        traces = {row["trace_sha256"] for row in group if row["trace_sha256"]}
        if len(traces) > 1:
            raise ValueError(f"Trial trace mismatch for {key}: {traces}")
        checked += len(group)
    groups = defaultdict(list)
    for row in rows:
        if row["timing_status"] != "ok":
            continue
        # Trace instrumentation and sampled diagnostics must not mix with ordinary timings.
        key = tuple(row[field] for field in ("workload", "size", "destroyed", "repair", "mode",
                    "trial_limit", "budget_ms", "warmup_ms", "warmup_termination",
                    "diagnostic_timing_enabled", "run_layout"))
        key += (str(bool(row["trace_sha256"])).lower(), row["version"], row["threads"])
        groups[key].append(row)
    records = []
    for key, group in groups.items():
        (shape, size, destroyed, repair, mode, trials, budget, warmup, warmup_termination, diagnostic, layout,
         trace, version, threads) = key
        reference_current = groups.get(key[:-2] + ("current", "NONE"))
        reference_baseline = groups.get(key[:-2] + ("baseline", "NONE"))
        rate = median(group, "probes_per_second")
        elapsed = median(group, "solve_ms")

        def relative(reference):
            if (not reference or trace == "true" or diagnostic == "true"
                    or mode == "fixed" and warmup_termination == "time"):
                return ""
            if mode == "fixed":
                return f"{median(reference, 'solve_ms') / elapsed:.2f}"
            reference_rate = median(reference, "probes_per_second")
            return f"{rate / reference_rate:.2f}" if reference_rate else ""

        records.append({
            "Workload": shape, "Size": size, "Destroyed": destroyed, "Repair": repair, "Mode": mode,
            "Variant": version + " " + threads +
                       (" (original warmup)" if mode == "fixed" and warmup_termination == "time" else ""),
            "Runs": str(len(group)),
            "Solve ms": f"{elapsed:.1f}", "Probes/s": f"{rate:.0f}",
            "vs current NONE": relative(reference_current),
            "vs old NONE": relative(reference_baseline),
            "Best score median": f"{median(group, 'best_score'):.0f}",
            "Trials median": f"{median(group, 'trials'):.0f}",
            "CPU cores": f"{statistics.median(float(r['process_cpu_ms']) / float(r['solve_ms']) for r in group):.2f}",
            "RSS MiB": f"{median(group, 'peak_rss_kib') / 1024:.1f}",
            "GC ms incl. validation": f"{median(group, 'gc_ms'):.0f}",
            "Trace": trace, "Diagnostics": diagnostic, "Warmup ms": warmup,
            "Warmup termination": warmup_termination, "Run layout": layout,
            "Budget": trials + " trials" if mode == "fixed" else budget + " ms",
        })
    records.sort(key=lambda row: (row["Workload"], int(row["Size"]), row["Repair"],
                                  row["Mode"], row["Trace"], row["Variant"]))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    combined = args.output.with_suffix(".csv")
    with combined.open("w", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    title = "ALNS move threading measurements"
    isolated = sum(row["run_layout"] == "isolated-jvm" for row in rows)
    validation = sum(row["run_layout"] == "shared-validation-jvm" for row in rows)
    anomalous = sum(row["timing_status"] != "ok" for row in rows)
    note = (
        f"{len(rows)} observations: {isolated} isolated JVM runs and {validation} correctness "
        f"traces from shared validation JVMs. {checked} fixed-work results passed score, "
        "state, useful-probe and trial-count equivalence. Nonempty trial traces also match. "
        "The table shows medians. Fixed-work ratios compare elapsed time for identical logical work; "
        "equal-time ratios compare observed completed-trial probe rates along potentially different "
        "search trajectories. Best scores are best-found values. Three seeds do not establish "
        "statistical significance. Trace and diagnostic runs are separated from ordinary timings. "
        "Fixed runs warmed with time-based termination are retained as original observations and "
        "grouped separately from corrected fixed runs warmed with the same trial-count termination. "
        "RSS includes warmup and setup; CPU cores is process CPU time divided by solve elapsed time. "
        "GC deltas end after final independent validation and state/trace hashing; solve elapsed, "
        "CPU and used-heap snapshots end before that final verification."
    )
    if anomalous:
        note += (f" {anomalous} runs whose monotonic elapsed time was more than 10% shorter than "
                 "the configured phase clock budget are retained and flagged in raw CSV, but excluded "
                 "from these summaries. Wall-clock adjustments can invalidate an equal-time comparison.")
    columns = list(records[0])
    sources = sorted({row["source_csv"] for row in rows})
    logs = sorted({row["source_log"] for row in rows})
    supporting = [name for name in ("measurement-manifest.json", "hardware.txt", "rerun-provenance.json")
                  if (args.output.parent / name).exists()]
    source_links = " · ".join(f'<a href="{escape(name)}">{escape(name)}</a>'
                              for name in supporting + sources)
    log_links = "".join(f'<li><a href="{escape(name)}">{escape(name)}</a></li>' for name in logs)
    header = "".join(f"<th>{escape(column)}</th>" for column in columns)
    body = "\n".join("<tr>" + "".join(f"<td>{escape(row[column])}</td>" for column in columns)
                     + "</tr>" for row in records)
    page = f"""<!doctype html><html lang="en"><meta charset="utf-8">
<title>{title}</title><style>
body{{font:15px system-ui;margin:32px;color:#17212b;background:#fafbfc}}
h1{{font-size:26px}}p{{max-width:1000px;line-height:1.5}}input{{padding:10px;width:360px}}
.table{{overflow:auto;margin-top:20px}}table{{border-collapse:collapse;background:white}}
th,td{{padding:9px 12px;border-bottom:1px solid #dfe4e8;text-align:right;white-space:nowrap}}
th{{position:sticky;top:0;background:#eaf0f5;cursor:pointer}}td:first-child,th:first-child{{text-align:left}}
tr:hover{{background:#f0f6fa}}.muted{{color:#506070}}
</style><h1>{title}</h1><p>{escape(note)}</p>
<p class="muted">{escape(args.environment)}</p>
<p><a href="{escape(combined.name)}">Download every raw CSV observation</a></p>
<p>{source_links}</p><details><summary>Raw process logs ({len(logs)})</summary><ul>{log_links}</ul></details>
<label>Filter table <input id="filter" placeholder="For example: list 500 GREEDY"></label>
<div class="table"><table><thead><tr>{header}</tr></thead><tbody>{body}</tbody></table></div>
<script>
const rows=[...document.querySelectorAll('tbody tr')];
document.querySelector('#filter').addEventListener('input',event=>{{
 const terms=event.target.value.toLowerCase().split(/\\s+/).filter(Boolean);
 rows.forEach(row=>row.hidden=!terms.every(term=>row.textContent.toLowerCase().includes(term)));
}});
document.querySelectorAll('th').forEach((header,index)=>header.addEventListener('click',()=>{{
 const direction=header.dataset.direction==='up'?-1:1;
 header.dataset.direction=direction===1?'up':'down';
 rows.sort((a,b)=>{{const x=a.children[index].textContent,y=b.children[index].textContent;
 return direction*((x!==''&&y!==''&&!isNaN(x)&&!isNaN(y))?Number(x)-Number(y):x.localeCompare(y));}});
 rows.forEach(row=>document.querySelector('tbody').append(row));
}}));
</script></html>"""
    args.output.write_text(page)
    markdown = "# " + title + "\n\n" + note + "\n\n" + args.environment + "\n\n"
    markdown += "Sources: " + ", ".join(f"[{name}]({name})" for name in supporting + sources) + "\n\n"
    markdown += "| " + " | ".join(columns) + " |\n"
    markdown += "| " + " | ".join("---" for _ in columns) + " |\n"
    markdown += "\n".join("| " + " | ".join(row[column] for column in columns) + " |"
                          for row in records) + "\n"
    args.output.with_suffix(".md").write_text(markdown)
    print(f"Wrote {args.output}; {checked} fixed-work rows equivalent.")


if __name__ == "__main__":
    main()
