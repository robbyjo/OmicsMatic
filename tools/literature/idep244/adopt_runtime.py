#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit an existing original iDEP2.4.4 Windows runtime; install/download nothing."""
import argparse
import shutil
from Runtime import *


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    parser.add_argument("--rscript", type=Path, required=True)
    parser.add_argument("--libraries-file", type=Path, required=True)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    out = None
    initial = None
    try:
        source = args.source.resolve()
        rscript = args.rscript.resolve()
        libraries = args.libraries_file.resolve()
        out = new_output(args.out, [HERE, source, rscript.parent.parent, libraries])
        warning(out)
        if os.name != "nt" or not rscript.is_file():
            raise Unavailable("Existing permitted Windows R4.6.1 Rscript executable is required")
        check_source(source)
        before = adoption_sources()
        save(out / "adoption-source-pins.json", before)
        (out / "adoption-engine").mkdir()
        for name in ADOPTION:
            shutil.copyfile(HERE / name, out / "adoption-engine" / name)
        requested = [str(Path(n).resolve()) for n in libraries.read_text(encoding="utf-8-sig").splitlines() if n]
        (out / "libraries-requested.txt").write_text("\n".join(requested) + "\n", encoding="utf-8", newline="\n")
        protected = sorted({Path(n) for n in requested if Path(n).is_dir()} |
                           {rscript.parent.parent})
        protected = [p for p in protected if not any(p != q and p.is_relative_to(q) for q in protected)]
        initial = {str(p): inventory(p) for p in protected}
        save(out / "initial-protected-files.json", initial)
        limits = dict(wall_seconds=600, files=10000, bundle_bytes=128*1024*1024)
        result = launch([rscript, "--vanilla", HERE / "InspectRuntime.R",
                         "libraries-requested.txt", source, HERE / "SourceAudit.R"],
                        out, "inspect", limits)
        if result["exit_code"] == 3:
            raise Unavailable("Original runtime inspection failed; read complete inspect stdout/stderr")
        if result["exit_code"] != 0:
            raise ValueError("Original runtime audit failed; read complete inspect stdout/stderr")
        inspected = load(out / "INSPECTED.json")
        roots = {entry["path"] for entry in inspected["packages"]}
        r_home = Path(inspected["R_home"])
        roots.update(str(r_home / n) for n in ["bin", "etc", "modules"])
        if not str(rscript).casefold().startswith(str(r_home).casefold() + os.sep):
            # pathlib may report / or \; compare resolved filesystem ancestry too.
            if not rscript.is_relative_to(r_home.resolve()):
                raise ValueError("Inspected R home does not own the requested Rscript")
        records = {str(Path(p).resolve()): inventory(p) for p in sorted(roots)}
        save(out / "runtime-files.json", records)
        # A second probe and full after-inventory prevent adoption of a changing runtime.
        second = launch([rscript, "--vanilla", "-e",
                         "stopifnot(as.character(getRversion())=='4.6.1');cat('R4.6.1\\n')"],
                        out, "after", limits)
        if second["exit_code"] or adoption_sources() != before:
            raise ValueError("Runtime/adoption source changed during audit")
        for root, identities in records.items():
            if inventory(root) != identities:
                raise ValueError("Installed package/R files changed during audit: " + root)
        for root, identities in initial.items():
            if inventory(root) != identities:
                raise ValueError("Original supplied library/R tree changed during audit: " + root)
        check_source(source)
        ready = dict(schema="omicsmatic-idep244-windows-r461-provider-v1",
                     source=str(source), rscript=str(rscript), libraries=inspected["libraries"],
                     source_profile=pin(HERE / "source-profile.json"), adoption_sources=before,
                     runtime_index=pin(out / "runtime-files.json"), inspected=pin(out / "INSPECTED.json"),
                     installed_packages=inspected["packages"], network_isolated=False)
        save(out / "READY.json", ready)
        save(out / "COMPLETED.json", dict(status="PASS_ORIGINAL_IDEP244_RUNTIME_AUDIT_NOT_ANALYSIS",
              ready=pin(out / "READY.json"), original_algorithms_patched=False,
              packages=len(inspected["packages"]), locked_files=sum(map(len, records.values())),
              analysis_admitted=False, catalog_credit_added=0))
        print("PASS_ORIGINAL_IDEP244_RUNTIME_AUDIT_NOT_ANALYSIS", out, flush=True)
        return 0
    except BaseException as error:
        if out is not None and initial is not None:
            try:
                changes = [p for p, before in initial.items() if inventory(p) != before]
                save(out / "FAILURE-PROTECTION.json", dict(
                    complete_before_after_runtime_check=True, changed_trees=changes))
            except BaseException as protection:
                print("FAIL2_FAILURE_PROTECTION_CHECK_UNAVAILABLE", repr(protection), flush=True)
        return failure(out, error, "ADOPT_RUNTIME")


if __name__ == "__main__":
    if not __debug__:
        raise RuntimeError("Never disable runtime/source assertions")
    raise SystemExit(main())
