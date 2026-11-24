"""Write a NEW explicit unbinned intensity request; no gating or statistics are run."""
from pathlib import Path
import argparse
import hashlib
import json
import sys


def pin(path):
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return {"bytes": path.stat().st_size, "sha256": digest}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("parent", "definitions", "request", "out"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--measurement-units", required=True)
    parser.add_argument("--statistics-provenance", required=True)
    parser.add_argument("--parent-count-policy", choices=("REQUIRE_SAVED_COUNTS_AGREE", "ALLOW_DECLARED_NATIVE_POPULATION"), default="REQUIRE_SAVED_COUNTS_AGREE")
    parser.add_argument("--export-measurements", action="store_true")
    args = parser.parse_args()
    try:
        parent = args.parent.resolve(strict=True)
        definitions_path = args.definitions.resolve(strict=True)
        request, out = args.request.resolve(), args.out.resolve()
        if request.exists() or out.exists() or request.is_relative_to(out):
            raise ValueError("Choose NEW request outside NEW output")
        if any(source.is_relative_to(out) or out.is_relative_to(source) for source in (parent, definitions_path)):
            raise ValueError("Output overlaps a protected input")
        if request.is_relative_to(parent):
            raise ValueError("Keep request outside the sealed parent")
        marker = json.loads((parent / "COMPLETED.json").read_text(encoding="utf-8"))
        if marker.get("profile") != "native-flowjo10-wsp20-flowkit132-v1":
            raise ValueError("Require a completed native FlowJo 10 gate parent")
        # The Java command validates the complete parent closure and freshly reruns every gate.
        definitions = json.loads(definitions_path.read_text(encoding="utf-8"))
        if not isinstance(definitions, list) or not 1 <= len(definitions) <= 128:
            raise ValueError("Definitions must be an explicit JSON array of 1..128 populations")
        for declaration in (args.measurement_units, args.statistics_provenance):
            if not declaration.strip():
                raise ValueError("Supply literal nonempty units and provenance declarations")
        value = {"schema": 1, "profile": "native-cytometry-intensity-unbinned-v1",
                 "parent": str(parent), "parent_manifest_pin": pin(parent / "manifest.json"), "out": str(out),
                 "binning": "NONE", "quantile_method": "R_TYPE_7", "parent_count_policy": args.parent_count_policy,
                 "definitions": definitions, "measurement_units": args.measurement_units,
                 "statistics_provenance": args.statistics_provenance, "export_measurements": args.export_measurements}
        request.parent.mkdir(parents=True, exist_ok=True)
        with request.open("x", encoding="utf-8", newline="\n") as stream:
            json.dump(value, stream, indent=2, allow_nan=False)
            stream.write("\n")
        print("PASS request only. No analysis run. Explicit unbinned R_TYPE_7; parent-count-policy=" + args.parent_count_policy)
        print("REQUIRED ACTION: run packaged SUMMARIZE, require exit 0 and COMPLETED.json, and read WARNINGS.md. Vendor-binned FlowJo statistics remain unqualified.")
        return 0
    except Exception as error:
        print("FAIL2: request not admitted: " + repr(error) + ". Preserve inputs; correct explicit definitions and choose NEW paths.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
