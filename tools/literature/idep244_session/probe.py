#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Development probe: original iDEP loader with an actual local Shiny session."""
from pathlib import Path
import argparse
import base64
import secrets
import shutil
import sys
from Transport import *
from Runtime import attempted, check_source, engine_sources, failure, provider_check


def retain_notifications(out):
    notifications = []
    path = out / "websocket-messages.jsonl"
    if path.is_file():
        for index, line in enumerate(path.read_bytes().splitlines()):
            row = json.loads(line)
            if row["direction"] == "received" and row["opcode"] == 1:
                message = json.loads(base64.b64decode(row["payload_base64"], validate=True))
                if "notification" in message:
                    notifications.append(dict(message_index=index, notification=message["notification"]))
    target = out / "received-notifications.json"
    if target.exists():
        if load(target) != notifications:
            raise ValueError("Previously retained original notification messages differ")
        return notifications
    save(target, notifications)
    notice = ("DEVELOPMENT LOADER PROBE ONLY. No differential-expression result or catalog credit is admitted. "
              "Raw original notifications are preserved in received-notifications.json and websocket-messages.jsonl. "
              "Original loader values, conditions, errors and RNG are in complete-loader-return.rds. "
              "An error or NULL loader value admits no downstream analysis. Inspect the original input and diagnostics "
              "and retry a NEW path with an appropriate counts file. This establishes no full GUI, biological, paper "
              "or whole-tool qualification.")
    if notifications:
        notice += "\n\nOriginal session notifications, without HTML normalization:\n\n" + "\n\n".join(
            "    " + json.dumps(n["notification"], ensure_ascii=True) for n in notifications)
    (out / "WARNINGS.md").write_text("# Warnings and required actions\n\n" + notice + "\n", encoding="utf-8")
    print(notice, flush=True)
    return notifications


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--runtime", type=Path, required=True)
    p.add_argument("--transport", type=Path, required=True)
    p.add_argument("--expression", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    a = p.parse_args()
    out = None
    try:
        destination = a.out.resolve()
        if destination.exists():
            raise ValueError("Keep prior evidence and choose a NEW directory")
        ready = provider_check(a.runtime)
        ws = transport_check(a.transport)
        source = a.expression.resolve(strict=True)
        if source.suffix.lower() != ".csv" or not source.is_file():
            raise ValueError("This initial loader probe requires a regular CSV file")
        protected = [Path(ready["source"]).resolve(), HERE.resolve(), a.runtime.resolve(), a.transport.resolve()]
        if any(destination.is_relative_to(x) or x.is_relative_to(destination) for x in protected):
            raise ValueError("Probe output overlaps protected source/runtime")
        destination.mkdir(parents=False)
        out = destination
        (out / "inputs").mkdir()
        (out / "engine").mkdir()
        original_pin = pin(source)
        shutil.copyfile(source, out / "inputs/counts.csv")
        if pin(source) != original_pin or pin(out / "inputs/counts.csv") != original_pin:
            raise ValueError("Original input changed during capture")
        for name in ["Transport.py", "SessionProbe.R", "probe.py", "install_transport.py"]:
            shutil.copyfile(HERE / name, out / "engine" / name)
        port, token = free_port(), secrets.token_hex(32)
        control = dict(port=port, token=token, source=ready["source"],
                       audit=str(HERE.parent / "idep244/SourceAudit.R"))
        save(out / "control.json", control)
        (out / "libraries.txt").write_text("\n".join(ready["libraries"]) + "\n", encoding="utf-8")
        save(out / "PROVENANCE.json", dict(runtime=str(a.runtime.resolve()),ready=pin(a.runtime / "READY.json"),
            transport=str(a.transport.resolve()),transport_ready=pin(a.transport / "READY.json"),
            original_input=str(source),input=original_pin,legacy_engine_sources=engine_sources(),
            probe_engine_sources={p.name: pin(p) for p in (out / "engine").iterdir()}))
        limits = dict(wall_seconds=180, files=5000, bundle_bytes=256*1024*1024)
        result = launch_session([ready["rscript"],"--vanilla",str(out / "engine/SessionProbe.R"),
                                 str(out / "control.json")], out, limits, port, token, ws)
        status = load(out / "R-status.json")
        retain_notifications(out)
        if result["exit_code"] or status["exit_code"] or not status["acknowledged"]:
            raise ValueError("Original loader/session failed; retain all original returns and messages")
        provider_check(a.runtime)
        transport_check(a.transport)
        if pin(source) != original_pin:
            raise ValueError("Original source input changed")
        save(out / "MANIFEST.json", inventory(out))
        save(out / "COMPLETED.json", dict(status="PASS_GENUINE_SHINY_LOADER_DEVELOPMENT_PROBE_NOT_ANALYSIS",
            manifest=pin(out / "MANIFEST.json"),R_status=pin(out / "R-status.json"),
            original_return_is_NULL=status["original_return_is_NULL"],
            original_dimensions=status["original_dimensions"],received_notifications=result["transport"]["notifications"],
            scientific_analysis_admitted=False,
            catalog_credit_added=0,full_GUI_qualified=False,whole_tool_qualified=False))
        print(load(out / "COMPLETED.json"),flush=True)
        return 0
    except BaseException as error:
        if out is not None and (out / "websocket-messages.jsonl").is_file():
            try:
                retain_notifications(out)
            except BaseException as recording:
                print("FAIL2_NOTIFICATION_RECORDING_INCOMPLETE",repr(recording),flush=True)
        return failure(out,error,context={"mode":"development-loader-probe","expression":str(a.expression)},
                       analysis_started=attempted(out,"worker"))


if __name__=="__main__":
    raise SystemExit(main())
