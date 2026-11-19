"""Read back the qualified scoped PLINK-relatedness packet without running PLINK."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import subprocess
import sys
import zipfile


def pin(path):
    with path.open("rb") as stream:
        return dict(bytes=path.stat().st_size,
                    sha256=hashlib.file_digest(stream, "sha256").hexdigest())


def check_package(jar, index):
    with zipfile.ZipFile(jar) as package:
        if len(package.namelist()) != len(set(package.namelist())):
            raise ValueError("Duplicate candidate JAR member")
        expected = dict(index["parent_payloads"], **index["added_payloads"])
        if set(package.namelist()) != set(expected):
            raise ValueError("Candidate JAR member population differs")
        for name, wanted in expected.items():
            data = package.read(name)
            actual = dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
            if actual != wanted:
                raise ValueError("Candidate payload differs: " + name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    project, jar, output = (p.resolve() for p in (args.project, args.jar, args.out))
    packet = project / "docs/literature/plink-relatedness-development"
    proof = json.loads((packet / "QUALIFICATION.json").read_bytes())
    if not jar.is_file():
        print("SKIP3: exact relatedness candidate is unavailable. Restore the checkpoint "
              "JAR or rebuild bit-identical payloads with build_plink_relatedness_workflows.py; "
              "capture verification was skipped.", file=sys.stderr)
        return 3
    for name, wanted in proof["source_pins"].items():
        if pin(project / name) != wanted:
            raise ValueError("Qualified source differs: " + name)
    if pin(packet / "PACKAGE.json") != proof["package_index"]:
        raise ValueError("Package index differs")
    index = json.loads((packet / "PACKAGE.json").read_bytes())
    check_package(jar, index)
    archive = packet / "evidence.zip"
    if pin(archive) != proof["archive"]:
        raise ValueError("Evidence archive differs")
    output.mkdir(parents=True, exist_ok=False)
    with zipfile.ZipFile(archive) as bundle:
        if len(bundle.namelist()) != len(set(bundle.namelist())):
            raise ValueError("Duplicate evidence member")
        if set(bundle.namelist()) != set(proof["members"]):
            raise ValueError("Evidence member population differs")
        for info in bundle.infolist():
            relative = PurePosixPath(info.filename)
            if (relative.is_absolute() or not relative.parts or info.is_dir() or
                    any(p in (".", "..") or ":" in p or "\\" in p for p in relative.parts) or
                    (info.external_attr >> 16) & 0o170000 == 0o120000 or
                    info.file_size > 64 * 1024 * 1024):
                raise ValueError("Unsafe evidence member: " + info.filename)
            data = bundle.read(info)
            actual = dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
            if actual != proof["members"][info.filename]:
                raise ValueError("Evidence payload differs: " + info.filename)
            target = output / "evidence" / Path(*relative.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    results = {}
    for number, (name, expected) in enumerate(proof["capture_verification"].items(), 1):
        protocol = output / ("verify-" + str(number) + ".json")
        protocol.write_text(json.dumps(dict(schema_version=1, mode="VERIFY",
                                            analysis=str(output / "evidence" / name),
                                            wall_seconds=600)) + "\n", encoding="utf-8")
        command = [args.java, "-Xmx1g", "-cp", str(jar),
                   "org.jlinalg.cli.PlinkRelatednessCli", "--request", str(protocol)]
        with (output / (str(number) + ".stdout")).open("xb") as stdout, \
                (output / (str(number) + ".stderr")).open("xb") as stderr:
            process = subprocess.run(command, stdin=subprocess.DEVNULL,
                                     stdout=stdout, stderr=stderr, timeout=660)
        results[name] = dict(exit_code=process.returncode, expected=expected)
        (output / "PROGRESS.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
        print(name + ": VERIFY " + str(process.returncode), flush=True)
        if process.returncode != expected:
            raise ValueError("Unexpected verification result: " + name)
    for name, wanted in proof["members"].items():
        if pin(output / "evidence" / name) != wanted:
            raise ValueError("Readback changed retained evidence: " + name)
    result = dict(status="PASS_SCOPED_RELATEDNESS_CAPTURE_READBACK", platform=sys.platform,
                  captures=results, candidate=pin(jar),
                  original_candidate_container=pin(jar) == proof["candidate"],
                  all_candidate_payloads_identical=True,
                  unchanged_evidence_files=len(proof["members"]),
                  original_PLINK_executed=False, catalog_credit=0, whole_goal_complete=False)
    (output / "COMPLETED.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(result["status"], flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print("FAIL2: scoped qualification readback failed; retain output, fix the named "
              "source/artifact/input issue and retry NEW paths: " + repr(error), file=sys.stderr)
        raise SystemExit(2)
