#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record the original iDEP2.4.4 counts -> preprocessing -> DESeq2 workflow."""
import argparse
import shutil
from Runtime import *
from Schema import validate, input_files, capture, reference_preflight

SUCCESS = "PASS_RECORDED_ORIGINAL_IDEP244_COUNTS_DESEQ2_DEVELOPMENT_WORKFLOW"


def execute(runtime, out, effective, inputs, original_request, ready):
    """Execute already captured inputs in a new owned bundle, never originals."""
    validate(effective)
    reference_preflight(effective, out)
    sources_before = engine_sources()
    engine = out / "engine"
    engine.mkdir()
    for name in sources_before:
        shutil.copyfile(HERE / name, engine / name)
    if {p.name: pin(p) for p in engine.iterdir()} != sources_before:
        raise ValueError("Engine source changed during capture")
    save(out / "engine-sources.json", sources_before)
    save(out / "effective-request.json", effective)
    save(out / "captured-inputs.json", inputs)
    (out / "original-request.json").write_bytes(original_request)
    (out / "libraries.txt").write_text("\n".join(ready["libraries"]) + "\n",
                                     encoding="utf-8", newline="\n")
    evidence = out / "runtime-evidence"
    evidence.mkdir()
    for name in ["READY.json", "COMPLETED.json", "runtime-files.json",
                 "INSPECTED.json", "adoption-source-pins.json", "namespace-audit.rds"]:
        shutil.copyfile(runtime / name, evidence / name)
    (out / "original/stages").mkdir(parents=True)
    save(out / "control.json", dict(source=ready["source"], libraries=ready["libraries"]))
    count, size = footprint(out)
    if count > effective["limits"]["files"] or size > effective["limits"]["bundle_bytes"]:
        raise ValueError("Complete captured inputs/engine/runtime records exceed the recording limit")
    result = launch([ready["rscript"], "--vanilla", "engine/Worker.R", "control.json"],
                    out, "worker", effective["limits"])
    # Check even a failing original process; no source/runtime mutation is hidden
    # behind its error. The first/full raw process records remain available.
    provider_check(runtime)
    if sources_before != engine_sources():
        raise ValueError("Installed engine source changed during analysis")
    if {p.name: pin(p) for p in engine.iterdir()} != sources_before:
        raise ValueError("Captured engine source changed during analysis")
    for name, identity in inputs.items():
        if pin(out / name) != {k: identity[k] for k in ["bytes", "sha256"]}:
            raise ValueError("Captured original input changed during analysis: " + name)
    status_file = out / "original/R-status.json"
    status = load(status_file) if status_file.is_file() else {}
    if status.get("captured_R_condition_messages"):
        with (out / "WARNINGS.md").open("a", encoding="utf-8", newline="\n") as stream:
            stream.write("\n# Captured R warnings, errors and messages\n\n"
                         "These are the original captured condition messages. Also read both full raw consoles; "
                         "this is not a count of every possible native diagnostic.\n\n")
            for condition in status["captured_R_condition_messages"]:
                stream.write("## " + condition["kind"] + " / " + condition["phase"] + "\n\n    " +
                             condition["message"].replace("\n", "\n    ") + "\n\n")
    if (result["exit_code"] != 0 or
            status.get("status") != "PASS_ORIGINAL_IDEP244_COMPLETE_COUNT_RETURN_RECORDED" or
            status.get("phase") != "COMPLETE" or
            status.get("complete_original_return_recorded") is not True or
            status.get("original_dispatcher_returned") is not True or
            status.get("generated_original_R_script_executed") is not False or
            not (out / "original/complete-original-returns.rds").is_file()):
        raise ValueError("Original iDEP analysis is unadmitted; phase=" +
                         str(status.get("phase")) + "; original error=" + str(status.get("error")) +
                         "; read complete original returns, conditions and consoles")
    if status["adapter_warnings"]:
        with (out / "WARNINGS.md").open("a", encoding="utf-8", newline="\n") as stream:
            stream.write("\n# Original workflow limitations in this run\n\n" +
                         "\n".join(str(s) for s in status["adapter_warnings"]) + "\n")
    run = dict(schema="omicsmatic-idep244-original-count-run-v1", profile=effective["profile"],
               operation=effective["operation"], provider=str(runtime), provider_ready=pin(runtime / "READY.json"),
               provider_runtime_index=pin(runtime / "runtime-files.json"),
               original_request=rawpin(original_request), effective_request=pin(out / "effective-request.json"),
               engine_sources=sources_before, captured_inputs=pin(out / "captured-inputs.json"),
               runtime_and_source_before_after_unchanged=True, source_algorithms_patched=False,
               R_status=pin(status_file), original_complete_return=pin(out / "original/complete-original-returns.rds"),
               complete_original_conditions=pin(out / "original/complete-conditions.rds"),
               original_500_sample_500_comparison_rules_retained=True,
               author_environment_established=False, whole_tool_qualified=False,
               catalog_credit_added=0, whole_goal_complete=False)
    save(out / "RUN.json", run)
    members = inventory(out)
    save(out / "MANIFEST.json", members)
    completion = dict(status=SUCCESS, schema=run["schema"], run=pin(out / "RUN.json"),
                      manifest=pin(out / "MANIFEST.json"), original_dimensions=status["original_input_dimensions"],
                      result_dimensions=status["result_dimensions"], comparisons=status["comparisons"],
                      original_condition_count=status["original_condition_count"],
                      analysis_admitted=True, development_workflow_only=True,
                      catalog_credit_added=0, paper_reproduced=False, whole_tool_qualified=False)
    # Check the entire prospective result, including final metadata, before
    # promotion. Limits never permit truncation to make a marker fit.
    current = inventory(out)
    current.pop("MANIFEST.json")
    if current != members:
        raise ValueError("Output changed during completion recording")
    count, size = footprint(out)
    if count+1 > effective["limits"]["files"] or size+len(encoded(completion)) > effective["limits"]["bundle_bytes"]:
        raise ValueError("Complete final metadata exceeds the recording files/bundle_bytes limit")
    save(out / "COMPLETED.json", completion)
    if load(out / "COMPLETED.json") != completion:
        raise ValueError("Final completion recording differs")
    return completion


def protected_paths(runtime, request_file, request):
    protected = [HERE, runtime, request_file, request["expression"]]
    if request["design"] is not None:
        protected.append(request["design"])
    if request["mapping"]["mode"] == "database":
        protected.append(request["mapping"]["reference_dir"])
    path = runtime / "READY.json"
    if path.is_file():
        ready = load(path)
        protected += [ready["source"], Path(ready["rscript"]).parent.parent, *ready["libraries"]]
    return protected


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    parser.add_argument("--runtime", type=Path, required=True)
    parser.add_argument("--request", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    out = None
    try:
        if args.request.stat().st_size > 1024*1024:
            raise ValueError("Typed request JSON must not exceed1MiB; data belong in original input files")
        original = args.request.read_bytes()
        request = validate(load(args.request))
        if pin(args.request) != rawpin(original):
            raise ValueError("Original request changed while reading")
        runtime = args.runtime.resolve()
        out = new_output(args.out, protected_paths(runtime, args.request.resolve(), request))
        warning(out)
        # Retain the exact supplied JSON even if a later prerequisite fails.
        (out / "SUPPLIED-REQUEST.json").write_bytes(original)
        ready = provider_check(runtime)
        entries = input_files(request)
        effective, inputs = capture(request, out, entries)
        result = execute(runtime, out, effective, inputs, original, ready)
        print(result["status"], out, flush=True)
        return 0
    except BaseException as error:
        return failure(out, error, "RUN_ORIGINAL_COUNT_WORKFLOW", attempted(out, "worker"))


if __name__ == "__main__":
    if not __debug__:
        raise RuntimeError("Never disable source/evidence assertions")
    raise SystemExit(main())
