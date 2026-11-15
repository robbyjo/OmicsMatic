#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install the pinned Apache-2.0 WebSocket client in a NEW private directory."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys
import urllib.request

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
sys.path.append(str(HERE / "legacy" if (HERE / "legacy").is_dir() else HERE.parent / "idep244"))
from Runtime import inventory, load, pin, save

VERSION = "1.9.2"
WHEEL = "websocket_client-1.9.2-py3-none-any.whl"
SHA256 = "e1a673830a9c7bfa47b1cd3d5e4178f4c9651d80a4eab02c9c23a1c3ec6250ce"
URL = "https://files.pythonhosted.org/packages/d5/d2/cc4dc1271e464942db7ee278baae2daa99ee77cb2af744025c04da585a3e/" + WHEEL


def fetch(url, path):
    request = urllib.request.Request(url, headers={"User-Agent": "OmicsMatic-permitted-transport-setup"})
    with urllib.request.urlopen(request, timeout=45) as response, path.open("xb") as f:
        if response.status != 200:
            raise OSError("Original download unavailable: " + url)
        for chunk in iter(lambda: response.read(1024 * 1024), b""):
            f.write(chunk)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--out", required=True, type=Path)
    args = p.parse_args()
    out = args.out.resolve()
    if out.exists():
        raise FileExistsError("Keep the previous setup and choose a NEW output directory")
    out.mkdir(parents=False)
    try:
        fetch("https://pypi.org/pypi/websocket-client/" + VERSION + "/json", out / "PYPI.json")
        metadata = load(out / "PYPI.json")
        matches = [x for x in metadata["urls"] if x["filename"] == WHEEL]
        if len(matches) != 1 or matches[0]["url"] != URL or matches[0]["digests"]["sha256"] != SHA256:
            raise ValueError("Official wheel metadata contradicts the selected pin")
        fetch(URL, out / WHEEL)
        if pin(out / WHEEL) != {"bytes": 95786, "sha256": SHA256}:
            raise ValueError("Downloaded wheel differs from the selected pin")
        cmd = [sys.executable, "-m", "pip", "--disable-pip-version-check", "install", "--no-index",
               "--no-deps", "--no-compile", "--no-cache-dir", "--target", str(out / "python-site"),
               str(out / WHEEL)]
        with (out / "pip.stdout").open("xb") as so, (out / "pip.stderr").open("xb") as se:
            result = subprocess.run(cmd, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=120)
        save(out / "pip.process.json", dict(command=cmd, exit_code=result.returncode))
        if result.returncode:
            raise OSError("Local-only pinned wheel installation failed; read pip.stderr")
        ready = dict(schema="omicsmatic-idep244-websocket-client-v1", version=VERSION,
                     python=sys.version, executable=pin(sys.executable), installer=pin(__file__),
                     wheel=pin(out / WHEEL), site_files=inventory(out / "python-site"),
                     metadata=pin(out / "PYPI.json"), upstream_license="Apache-2.0",
                     upstream_binary_authenticated=False)
        save(out / "READY.json", ready)
        save(out / "COMPLETED.json", dict(status="PASS_PINNED_LOCAL_WEBSOCKET_CLIENT_SETUP_NOT_ANALYSIS",
                                         ready=pin(out / "READY.json")))
        print("PASS_PINNED_LOCAL_WEBSOCKET_CLIENT_SETUP_NOT_ANALYSIS", flush=True)
        return 0
    except BaseException as error:
        note = ("SKIP3: WebSocket transport is unavailable. Skip the session-dependent analysis. "
                "Read FAILURE.json and setup logs, supply the permitted pinned dependency, and retry NEW paths. "
                "The headless runner and earlier evidence have not been replaced.")
        save(out / "FAILURE.json", dict(status="SKIP3_SESSION_TRANSPORT_UNAVAILABLE", cause=repr(error), action=note))
        (out / "WARNINGS.md").write_text("# Warnings and required actions\n\n" + note + "\n", encoding="utf-8")
        print(note, repr(error), flush=True)
        return 3


if __name__ == "__main__":
    raise SystemExit(main())
