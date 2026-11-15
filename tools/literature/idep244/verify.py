#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay captured original counts/design/reference bytes in a NEW bundle."""
import argparse
import shutil
from Runtime import *
from Schema import validate, reference_preflight
from run import SUCCESS, execute


def check_bundle(bundle):
    completed = load(bundle / "COMPLETED.json")
    if completed.get("status") != SUCCESS or completed.get("analysis_admitted") is not True:
        raise ValueError("A final complete recorded development workflow is required")
    if completed["manifest"] != pin(bundle / "MANIFEST.json") or completed["run"] != pin(bundle / "RUN.json"):
        raise ValueError("Bundle admission does not identify the complete manifest/run")
    expected = load(bundle / "MANIFEST.json")
    actual = inventory(bundle)
    actual.pop("COMPLETED.json")
    actual.pop("MANIFEST.json")
    if actual != expected:
        raise ValueError("Complete bundle inventory differs (including added/missing files)")
    run = load(bundle / "RUN.json")
    if run.get("schema") != "omicsmatic-idep244-original-count-run-v1":
        raise ValueError("Unsupported original count-workflow schema")
    if run["engine_sources"] != engine_sources() or run["engine_sources"] != load(bundle / "engine-sources.json"):
        raise ValueError("Replay requires the exact recorded installed engine version")
    if {p.name: pin(p) for p in (bundle / "engine").iterdir()} != run["engine_sources"]:
        raise ValueError("Complete captured engine differs")
    for key, name in [("R_status", "original/R-status.json"),
                      ("original_complete_return", "original/complete-original-returns.rds"),
                      ("complete_original_conditions", "original/complete-conditions.rds"),
                      ("effective_request", "effective-request.json"),
                      ("captured_inputs", "captured-inputs.json")]:
        if run[key] != pin(bundle / name):
            raise ValueError("Declared complete record differs: " + name)
    request = validate(load(bundle / "effective-request.json"))
    if request["profile"] != run["profile"] or request["operation"] != run["operation"]:
        raise ValueError("Typed operation/profile contradiction")
    if run["original_request"] != pin(bundle / "original-request.json"):
        raise ValueError("Exact original request differs")
    if (bundle / "SUPPLIED-REQUEST.json").is_file() and pin(bundle / "SUPPLIED-REQUEST.json") != run["original_request"]:
        raise ValueError("Supplied/original request contradiction")
    inputs = load(bundle / "captured-inputs.json")
    captured = {"inputs/" + n: v for n, v in inventory(bundle / "inputs").items()}
    if set(captured) != set(inputs):
        raise ValueError("Every complete captured input is required")
    for name, identity in inputs.items():
        p = bundle / name
        if not p.resolve().is_relative_to((bundle / "inputs").resolve()):
            raise ValueError("Captured input path escapes the original input directory")
        if pin(p) != {k: identity[k] for k in ["bytes", "sha256"]}:
            raise ValueError("Complete original input differs: " + name)
    for name in [request["expression"], request["design"]]:
        if name is not None and name not in inputs:
            raise ValueError("Effective input is not a captured original file")
    if request["mapping"]["mode"] == "database" and request["mapping"]["reference_dir"] != "inputs/reference":
        raise ValueError("Exact captured reference directory required")
    status = load(bundle / "original/R-status.json")
    if (status.get("status") != "PASS_ORIGINAL_IDEP244_COMPLETE_COUNT_RETURN_RECORDED" or
            status["phase"] != "COMPLETE" or status["exit_code"] != 0 or
            status["error"] is not None or
            status["final_adapter_admission_established"] is not False or
            status["generated_original_R_script_executed"] is not False):
        raise ValueError("Original status contradicts final admission")
    for key in ["result_dimensions", "comparisons", "original_condition_count"]:
        if completed[key] != status[key]:
            raise ValueError("Summary contradicts complete original status: " + key)
    if completed["original_dimensions"] != status["original_input_dimensions"]:
        raise ValueError("Original input-dimension contradiction")
    runtime = Path(run["provider"])
    ready = provider_check(runtime)
    if run["provider_ready"] != pin(runtime / "READY.json") or run["provider_runtime_index"] != pin(runtime / "runtime-files.json"):
        raise ValueError("Exact recorded provider is required for replay")
    for name in ["READY.json", "COMPLETED.json", "runtime-files.json",
                 "INSPECTED.json", "adoption-source-pins.json", "namespace-audit.rds"]:
        if pin(bundle / "runtime-evidence" / name) != pin(runtime / name):
            raise ValueError("Captured runtime-audit record differs: " + name)
    reference_preflight(request, bundle)
    return run, request, inputs, ready


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    out = None
    try:
        bundle = args.bundle.resolve()
        light_run = load(bundle / "RUN.json")
        provider = Path(light_run["provider"]).resolve()
        protected = [HERE, bundle, provider]
        if (provider / "READY.json").is_file():
            light_ready = load(provider / "READY.json")
            protected += [light_ready["source"], Path(light_ready["rscript"]).parent.parent,
                          *light_ready["libraries"]]
        out = new_output(args.out, protected)
        warning(out)
        before = inventory(bundle)
        run, request, inputs, ready = check_bundle(bundle)
        replay = out / "replay"
        replay.mkdir()
        warning(replay)
        for name in inputs:
            dest = replay / name
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(bundle / name, dest)
        execute(Path(run["provider"]), replay, request, inputs,
                (bundle / "original-request.json").read_bytes(), ready)
        (out / "libraries.txt").write_text("\n".join(ready["libraries"]) + "\n", encoding="utf-8", newline="\n")
        process = launch([ready["rscript"], "--vanilla", HERE / "Compare.R",
                          bundle / "original/complete-original-returns.rds",
                          replay / "original/complete-original-returns.rds", "COMPARISON.json",
                          str(request["limits"]["vector_heap_mib"])],
                         out, "compare", dict(wall_seconds=request["limits"]["wall_seconds"],
                             files=request["limits"]["files"]*2,
                             bundle_bytes=request["limits"]["bundle_bytes"]*2))
        compared = load(out / "COMPARISON.json") if (out / "COMPARISON.json").exists() else {}
        if process["exit_code"] or compared.get("complete_values_equal") is not True:
            raise ValueError("Complete original replay differs; no verified result is admitted")
        original_status = load(bundle / "original/R-status.json")
        replay_status = load(replay / "original/R-status.json")
        if original_status != replay_status:
            raise ValueError("Complete original R status differs on replay")
        if engine_sources() != run["engine_sources"]:
            raise ValueError("Installed engine changed during replay comparison")
        provider_check(Path(run["provider"]))
        if inventory(bundle) != before:
            raise ValueError("Original recorded bundle changed during replay")
        proof = dict(status="PASS_CAPTURED_INPUT_REPLAY_ORIGINAL_IDEP244_DEVELOPMENT_WORKFLOW",
                     original_bundle=str(bundle), original_manifest=pin(bundle / "MANIFEST.json"),
                     comparison=pin(out / "COMPARISON.json"), complete_original_values_compared=True,
                     saved_original_bundle_unchanged=True, reference_authenticity_established=False,
                     whole_tool_qualified=False, catalog_credit_added=0, whole_goal_complete=False)
        members = inventory(out)
        save(out / "MANIFEST.json", members)
        proof["manifest"] = pin(out / "MANIFEST.json")
        current = inventory(out)
        current.pop("MANIFEST.json")
        if current != members:
            raise ValueError("Verification output changed during completion recording")
        count, size = footprint(out)
        if count+1 > request["limits"]["files"]*2 or size+len(encoded(proof)) > request["limits"]["bundle_bytes"]*2:
            raise ValueError("Complete verification metadata exceeds the doubled replay recording budget")
        save(out / "COMPLETED.json", proof)
        print(proof["status"], out, flush=True)
        return 0
    except BaseException as error:
        started = out is not None and (attempted(out / "replay", "worker") or attempted(out, "compare"))
        return failure(out, error, "VERIFY_CAPTURED_ORIGINAL_WORKFLOW", started)


if __name__ == "__main__":
    if not __debug__:
        raise RuntimeError("Never disable source/evidence assertions")
    raise SystemExit(main())
