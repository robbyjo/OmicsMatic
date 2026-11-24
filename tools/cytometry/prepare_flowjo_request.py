"""Write a NEW explicit, checksummed membership/count request; never runs an analysis."""
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
    p = argparse.ArgumentParser(description=__doc__)
    for name in ("workspace", "fcs", "out", "request"):
        p.add_argument("--" + name, required=True, type=Path)
    p.add_argument("--sample-id", required=True, help="Exact WSP SampleNode sampleID; not a filename")
    p.add_argument("--time-policy", required=True, choices=("NONE", "APPLY_FCS_TIMESTEP_IGNORE_TIME_GAIN"))
    p.add_argument("--time-channel", help="Exact original FCS time-channel name; required by the time-step policy")
    p.add_argument("--population-declaration", required=True)
    p.add_argument("--gating-provenance", required=True)
    p.add_argument("--calibrations", type=Path, help="CALIBRATIONS.json from the optional upstream export helper")
    p.add_argument("--saved-count-policy", choices=("REQUIRE_MATCH_WHEN_RECORDED", "REPORT_DIFFERENCES"), default="REQUIRE_MATCH_WHEN_RECORDED")
    p.add_argument("--label-policy", choices=("EXACT", "FLOWJO_SLASH_TO_UNDERSCORE"), default="EXACT")
    p.add_argument("--legacy-charset", default="UTF-8")
    p.add_argument("--allow-text-padding", action="store_true")
    p.add_argument("--allow-numeric-padding", action="store_true")
    p.add_argument("--legacy-zero-log-minimum-as-one", action="store_true")
    args = p.parse_args()
    try:
        workspace, fcs = args.workspace.resolve(strict=True), args.fcs.resolve(strict=True)
        target, request = args.out.resolve(), args.request.resolve()
        if target.exists() or request.exists() or request.is_relative_to(target):
            raise ValueError("Choose NEW output and NEW request outside that output")
        if any(source.is_relative_to(target) for source in (workspace, fcs)):
            raise ValueError("Output overlaps an original input")
        if (args.time_policy == "NONE") != (args.time_channel is None):
            raise ValueError("NONE requires no time-channel; the time-step policy requires an exact time-channel")
        calibration = []
        if args.calibrations is not None:
            meta = json.loads(args.calibrations.read_text(encoding="utf-8"))
            if meta["status"] != "PASS_CALIBRATION_DATA_ONLY" or meta["workspace"] != pin(workspace) or meta["sample_id"] != args.sample_id:
                raise ValueError("Calibration export does not match the exact workspace/sampleID")
            calibration = meta["calibrations"]
        value = {"schema": 1, "profile": "native-flowjo10-wsp20-flowkit132-v1", "operation": "MEMBERSHIP_COUNTS",
                 "workspace": str(workspace), "workspace_pin": pin(workspace), "fcs": str(fcs), "fcs_pin": pin(fcs),
                 "sample_id": args.sample_id, "calibrations": calibration, "out": str(target),
                 "legacy_charset": args.legacy_charset, "text_policy": "ALLOW_TRAILING_SPACE_PADDING" if args.allow_text_padding else "EXACT",
                 "numeric_policy": "ALLOW_ASCII_SPACE_PADDING" if args.allow_numeric_padding else "EXACT", "label_policy": args.label_policy,
                 "time_policy": args.time_policy, "time_channel": args.time_channel, "legacy_zero_log_minimum_as_one": args.legacy_zero_log_minimum_as_one,
                 "svd_relative_cutoff": 1e-12, "saved_count_policy": args.saved_count_policy,
                 "population_declaration": args.population_declaration, "gating_provenance": args.gating_provenance}
        request.parent.mkdir(parents=True, exist_ok=True)
        with request.open("x", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, allow_nan=False)
            stream.write("\n")
        print("PASS request written; no analysis run. Policies: saved-count=" + args.saved_count_policy + "; labels=" + args.label_policy + "; time=" + args.time_policy)
        return 0
    except Exception as error:
        print("FAIL2: request not admitted: " + repr(error) + ". Preserve original inputs and choose correct explicit policies and NEW paths.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
