"""Standard-library JSONL/CSV reduction. Never substitutes zero for an unavailable measurement."""
import argparse
import csv
import json
import statistics
from collections import defaultdict
from pathlib import Path

METRICS = ("duration_ms", "transition_observed_ms", "server_cpu_ms", "server_tick_processing_ms",
           "allocation_bytes", "gc_count", "gc_ms")


def describe(values):
    return {"n": len(values), "median": statistics.median(values), "min": min(values), "max": max(values),
            "stdev": statistics.stdev(values) if len(values) > 1 else None,
            "mad": statistics.median(abs(x - statistics.median(values)) for x in values)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("inputs", nargs="+", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    rows = []
    for root in args.inputs:
        paths = [root] if root.is_file() else sorted(root.rglob("results.jsonl"))
        for path in paths:
            if not (path.parent / "complete.txt").exists() or (path.parent / "failed.txt").exists():
                raise ValueError(f"Incomplete or failed run: {path}")
            for line in path.read_text(encoding="utf-8-sig").splitlines():
                row = json.loads(line)
                row["source_run"] = path.parent.name
                row["phase"] = next((flag.split("=", 1)[1] for flag in row["environment"]["jvm_flags"]
                                     if flag.startswith("-Dbenchmark.phase=")), "suite")
                if row["phase"] == "cold" and "-cold-" in path.parent.name:
                    row["process_repetition"] = int(path.parent.name.rsplit("-cold-", 1)[1])
                rows.append(row)
    groups = defaultdict(list)
    for row in rows:
        env = row["environment"]
        groups[(row["implementation"], row["machine"], row["count"], row["scenario"], row["phase"],
                env["profile"], env.get("probes", False), env.get("harness_sha256", "pilot"))].append(row)
    summary = []
    for key, group in sorted(groups.items()):
        implementation, machine, count, scenario, phase, profile, probes, harness = key
        entry = dict(implementation=implementation, machine=machine, count=count, scenario=scenario, phase=phase,
                     profile=profile, probes=probes, harness_sha256=harness,
                     connected_parts=sorted({r["connected_parts"] for r in group}), metrics={})
        for metric in METRICS:
            values = [r[metric] for r in group if r.get(metric) is not None]
            if values:
                entry["metrics"][metric] = describe(values)
        callbacks = defaultdict(list)
        for row in group:
            for name, value in (row.get("callbacks") or {}).items():
                callbacks[name].append(value)
        entry["callbacks"] = {name: {metric: describe([v[metric] for v in values])
                                     for metric in ("calls", "inclusive_ms")}
                              for name, values in callbacks.items()}
        summary.append(entry)
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "raw.jsonl").write_text("".join(json.dumps(r) + "\n" for r in rows), encoding="utf-8")
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    with (args.output / "raw.csv").open("w", newline="", encoding="utf-8") as stream:
        fields = ["implementation", "machine", "count", "scenario", "phase", "repetition", "process_repetition", "connected_parts", *METRICS, "source_run"]
        writer = csv.DictWriter(stream, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    lines = ["# Measured lifecycle summary", "", "Median (min–max), sample standard deviation and MAD are in summary.json.", "",
             "| Implementation / machine | Count | Scenario | Profile / probes | n | Wall ms: median [min, max] | Wall SD | Wall MAD | CPU ms: median [min, max] | Allocated bytes: median [min, max] |", "|---|---:|---|---|---:|---:|---:|---:|---:|---:|"]
    for item in summary:
        m = item["metrics"]
        def fmt(key):
            if key not in m:
                return "unavailable"
            v = m[key]
            places = 0 if key == "allocation_bytes" else 3
            return f'{v["median"]:.{places}f} [{v["min"]:.{places}f}, {v["max"]:.{places}f}]'
        sd = m["duration_ms"]["stdev"]
        sd = f"{sd:.3f}" if sd is not None else "unavailable"
        lines.append(f'| {item["implementation"]} / {item["machine"]} | {item["count"]} | {item["scenario"]} ({item["phase"]}) | '
                     f'{item["profile"]} / {item["probes"]} | {m["duration_ms"]["n"]} | {fmt("duration_ms")} | '
                     f'{sd} | {m["duration_ms"]["mad"]:.3f} | {fmt("server_cpu_ms")} | {fmt("allocation_bytes")} |')
    (args.output / "SUMMARY.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(rows)} measurements and {len(summary)} groups to {args.output}")


if __name__ == "__main__":
    main()
