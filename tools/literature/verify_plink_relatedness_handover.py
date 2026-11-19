"""Verify the retained development checkpoint; this is not tool qualification.

Uses only the supplied exact candidate JVM artifact. Never starts PLINK,
downloads dependencies, changes captures, or credits the literature catalog.
"""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import subprocess
import sys
import zipfile


def pin(path):
    with path.open("rb") as stream:
        return {"bytes": path.stat().st_size,
                "sha256": hashlib.file_digest(stream, "sha256").hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    project, jar, java, output = (p.resolve() for p in
                                  (args.project, args.jar, args.java, args.out))
    packet = project / "docs/literature/plink-relatedness-handover"
    manifest = json.loads((packet / "MANIFEST.json").read_bytes())
    if pin(jar) != manifest["candidate"]:
        raise ValueError("Exact checkpoint JAR unavailable/different: preserve this checkpoint; "
                         "restore its named bytes or qualify a new build separately")
    archive = packet / "checkpoint.zip"
    if pin(archive) != manifest["archive"]:
        raise ValueError("Checkpoint archive bytes/hash differ")
    for name, expected in manifest["source_pins"].items():
        if pin(project / name) != expected:
            raise ValueError("Checkpoint source differs: " + name)
    # This is a fresh output owned by this invocation; no recursive deletion.
    output.mkdir(parents=True, exist_ok=False)
    with zipfile.ZipFile(archive) as bundle:
        if len(bundle.infolist()) != len(set(bundle.namelist())):
            raise ValueError("Duplicate checkpoint member")
        for info in bundle.infolist():
            relative = PurePosixPath(info.filename)
            if (relative.is_absolute() or not relative.parts or
                    any(p in (".", "..") or ":" in p or "\\" in p for p in relative.parts) or
                    info.is_dir() or (info.external_attr >> 16) & 0o170000 == 0o120000):
                raise ValueError("Unsafe checkpoint member: " + info.filename)
            expected = manifest["members"].get(info.filename)
            payload = bundle.read(info)
            actual = {"bytes": len(payload), "sha256": hashlib.sha256(payload).hexdigest()}
            if actual != expected:
                raise ValueError("Checkpoint member differs: " + info.filename)
            destination = output / "checkpoint" / Path(*relative.parts)
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(payload)
        if set(bundle.namelist()) != set(manifest["members"]):
            raise ValueError("Checkpoint population differs")
    results = {}
    for name, expected in manifest["pilot_cases"].items():
        capture = output / "checkpoint/pilot-build4" / name
        request = capture / "verify-request.json"
        command = [str(java), "-Xmx1g", "-cp", str(jar),
                   "org.jlinalg.cli.PlinkRelatednessCli", "--request", str(request)]
        with (output / (name + ".stdout")).open("xb") as stdout, \
                (output / (name + ".stderr")).open("xb") as stderr:
            process = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=stdout,
                                     stderr=stderr, timeout=180)
        # A failed original attempt must remain refused by VERIFY.
        wanted = 0 if expected["exit_code"] == 0 else 2
        results[name] = {"verify_exit_code": process.returncode, "expected": wanted}
        (output / "PROGRESS.json").write_text(json.dumps(results, indent=2) + "\n",
                                             encoding="utf-8")
        print(name + ": VERIFY " + str(process.returncode), flush=True)
        if process.returncode != wanted:
            raise ValueError("Unexpected portable verification result: " + name)
    for name, expected in manifest["members"].items():
        if pin(output / "checkpoint" / name) != expected:
            raise ValueError("Verification changed a checkpoint member: " + name)
    result = {"status": "PASS_RELOCATED_CHECKPOINT_ONLY",
              "cases": results, "analysis_qualified": False, "catalog_credit": 0,
              "whole_goal_complete": False, "original_program_executed": False,
              "checkpoint_members_unchanged": len(manifest["members"]),
              "platform": sys.platform}
    (output / "COMPLETED.json").write_text(json.dumps(result, indent=2) + "\n",
                                          encoding="utf-8")
    print(result["status"], flush=True)


if __name__ == "__main__":
    main()
