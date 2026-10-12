"""Summarize JFR samples within the benchmark's explicit scenario windows."""
import argparse
import json
import re
import subprocess
from collections import Counter, defaultdict
from datetime import datetime
from pathlib import Path


def timestamp(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()


def seconds(value):
    match = re.fullmatch(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?", value)
    if not match:
        raise ValueError(value)
    h, m, s = match.groups()
    return float(h or 0) * 3600 + float(m or 0) * 60 + float(s or 0)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("recording", type=Path)
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    result = subprocess.run(["jfr", "print", "--json", "--stack-depth", "64", "--events",
                             "mobvats.benchmark.Window,jdk.ExecutionSample,jdk.ObjectAllocationSample", str(args.recording)],
                            check=True, capture_output=True, encoding="utf-8")
    events = json.loads(result.stdout)["recording"]["events"]
    windows = []
    for event in events:
        if event["type"] == "mobvats.benchmark.Window":
            v = event["values"]
            windows.append(dict(scenario=v["scenario"], repetition=v["repetition"],
                                start=timestamp(v["startTime"]), duration=seconds(v["duration"]),
                                cpu=Counter(), allocations=Counter(), allocation_count=0, cpu_count=0))
    for event in events:
        if event["type"] == "mobvats.benchmark.Window":
            continue
        v = event["values"]
        thread = v.get("sampledThread") or v.get("eventThread") or {}
        if thread.get("javaName") != "Server thread":
            continue
        t = timestamp(v["startTime"])
        frames = (v.get("stackTrace") or {}).get("frames", [])
        stack = []
        for frame in frames:
            method = frame["method"]
            stack.append(method["type"]["name"].replace("/", ".") + "." + method["name"])
        if not stack:
            continue
        # Retain both the sampled leaf and first relevant machine/framework caller.
        owner = next((f for f in stack if f.startswith(("net.impactvector.mobvats.", "it.zerono.mods."))), "vanilla/framework/runner")
        label = f"{stack[0]} <- {owner}"
        for window in windows:
            if window["start"] <= t <= window["start"] + window["duration"]:
                if event["type"] == "jdk.ExecutionSample":
                    window["cpu"][label] += 1
                    window["cpu_count"] += 1
                else:
                    window["allocations"][label] += v["weight"]
                    window["allocation_count"] += 1
                break
    output = []
    for window in windows:
        output.append({**{k: v for k, v in window.items() if k not in ("cpu", "allocations")},
                       "cpu_top": window["cpu"].most_common(10),
                       "sampled_allocation_weight_top": window["allocations"].most_common(10)})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2) + "\n", encoding="utf-8")
    print(f"Reduced {len(events)} JFR events into {len(windows)} windows: {args.output}")


if __name__ == "__main__":
    main()
