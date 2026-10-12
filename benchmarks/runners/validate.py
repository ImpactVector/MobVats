"""Validate the subset of JSON Schema used here, plus benchmark-specific invariants; stdlib only."""
import argparse
import json
from pathlib import Path


def matches(value, kind):
    return {"null": value is None, "object": isinstance(value, dict), "array": isinstance(value, list),
            "string": isinstance(value, str), "boolean": isinstance(value, bool),
            "integer": isinstance(value, int) and not isinstance(value, bool),
            "number": isinstance(value, (int, float)) and not isinstance(value, bool)}[kind]


def validate(value, schema, location):
    if "type" in schema:
        kinds = schema["type"] if isinstance(schema["type"], list) else [schema["type"]]
        assert any(matches(value, kind) for kind in kinds), f"{location}: wrong type"
    if "const" in schema:
        assert value == schema["const"], f"{location}: wrong constant"
    if "enum" in schema:
        assert value in schema["enum"], f"{location}: unexpected value"
    if value is not None and "minimum" in schema:
        assert value >= schema["minimum"], f"{location}: below minimum"
    if isinstance(value, dict):
        for key in schema.get("required", []):
            assert key in value, f"{location}: missing {key}"
        properties = schema.get("properties", {})
        for key, child in value.items():
            rule = properties.get(key, schema.get("additionalProperties", {}))
            if isinstance(rule, dict):
                validate(child, rule, f"{location}.{key}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("results", type=Path)
    args = parser.parse_args()
    schema = json.loads((Path(__file__).resolve().parents[1] / "scenarios/result-schema.json").read_text())
    rows = [json.loads(line) for line in args.results.read_text(encoding="utf-8").splitlines()]
    hashes = set()
    runtimes = set()
    idle = 0
    for i, row in enumerate(rows):
        validate(row, schema, f"row {i}")
        env = row["environment"]
        hashes.add(env["artifacts"]["zerocore"]["sha256"])
        runtimes.add((env["java"], env["minecraft"], env["neoforge"], env["max_heap"], env["warmup_ticks"], env["measure_ticks"]))
        assert row["network"] is None and row["deferred_operation_counts"] is None
        if row["scenario"] == "idle" and env["probes"]:
            callbacks = row["callbacks"]
            units = 2 if row["machine"] == "connected_pair" else 1
            expected = env["measure_ticks"] * row["count"] * units
            for suffix in (".updateServer", ".ZeroCore.updateMultiblockEntity"):
                assert sum(v["calls"] for k, v in callbacks.items() if k.endswith(suffix)) == expected
            assert all(k.endswith((".updateServer", ".ZeroCore.updateMultiblockEntity")) for k in callbacks)
            idle += 1
    assert len(hashes) == 1, "ZeroCore binaries differ"
    assert len(runtimes) == 1, "Core runtime/settings differ"
    print(f"Validated {len(rows)} schema rows, {idle} exact idle tick windows, identical ZeroCore and runtime settings.")


if __name__ == "__main__":
    main()
