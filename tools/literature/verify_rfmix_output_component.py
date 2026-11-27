"""Check retained native RFMix output-component evidence; this is not new inference/replay."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
FOLDER = "docs/literature/rfmix-native-audit"


def require(value, message):
    if not value:
        raise ValueError(message)


def pin(path):
    digest = hashlib.sha256()
    size = 0
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1048576), b""):
            size += len(block)
            digest.update(block)
    require(size == Path(path).stat().st_size, "Evidence length changed during read")
    return dict(bytes=size, sha256=digest.hexdigest())


def member(root, name):
    require(isinstance(name, str) and name and not name.startswith("/") and "\\" not in name and ":" not in name
            and all(p not in ("", ".", "..") for p in name.split("/")), "Invalid evidence member")
    path = root / name
    require(path.resolve().is_relative_to(root.resolve()) and not path.is_symlink(), "Linked/escaping evidence member")
    return path


def check(root, evidence):
    folder = root / FOLDER
    q = json.loads((folder / "QUALIFICATION.json").read_bytes())
    require(q["status"] == "PASS_NATIVE_RFMIX_OUTPUT_COMPONENT_FULL_ADAPTER_PENDING" and q["new_family_credit"] == 0
            and q["whole_goal_complete"] is False and q["SIS_final_iteration_qualified"] is False, "Component scope/qualification differs")
    archive = folder / "source-context.zip"
    require(pin(archive) == q["source_context_archive"], "Retained source-context archive changed")
    current_matches = True
    with zipfile.ZipFile(archive) as stream:
        require(len(stream.namelist()) == len(q["source_files"]) and set(stream.namelist()) == set(q["source_files"]), "Source archive population differs")
        for name, expected in q["source_files"].items():
            raw = stream.read(name)
            require(dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == expected, "Archived source bytes changed")
            if not name.startswith("qualification/"):
                path = member(root, name)
                current_matches &= path.is_file() and pin(path) == expected
    proof_path = evidence / "QUALIFICATION-PROOF.json"
    require(pin(proof_path) == q["private_evidence_proof"], "Private proof changed")
    proof = json.loads(proof_path.read_bytes())
    require(proof["schema_version"] == 1 and len(proof["files"]) == q["protected_private_files"], "Private evidence population differs")
    for name, expected in proof["files"].items():
        require(pin(member(evidence, name)) == expected, "Private scientific/source/failed-attempt evidence changed: " + name)
    windows = json.loads((evidence / "project-build-Windows-2/COMPLETED.json").read_bytes())
    linux = json.loads((evidence / "tests-Linux-1/COMPLETED.json").read_bytes())
    require(windows["totals"] == dict(tests=20, failures=0, errors=0, skipped=0) and linux["tests"] == 20 and linux["skipped"] == 0,
            "Retained focused tests differ")
    replay = json.loads((evidence / "fresh-original-replay-Linux-1/COMPLETED.json").read_bytes())
    require(replay["status"] == "PASS_ALL5_COMPLETE_ORIGINAL_OUTPUT_BYTE_REPLAYS_AND_CROSS_OS_NATIVE_CORE_AUDITS"
            and len(replay["cases"]) == 5 and replay["new_family_credit"] == 0, "Original replay evidence differs")
    return dict(status="PASS_RETAINED_RFMIX_COMPONENT_EVIDENCE_ONLY_NO_NEW_INFERENCE_OR_NUMERICAL_REPLAY",
                current_source_matches=current_matches, archived_historical_context_only=not current_matches,
                protected_private_files=len(proof["files"]), original_cases=5, original_fresh_replays=5,
                Windows_tests=20, Linux_tests=20, SIS_final_iteration_qualified=False,
                full_adapter_qualified=False, new_family_credit=0, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--evidence-root", type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(check(args.root.resolve(), args.evidence_root.resolve()), sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(("SKIP3 RETAINED COMPONENT VERIFICATION SKIPPED: " if code == 3 else "FAIL2 NO COMPLETE RETAINED COMPONENT VERIFICATION: ")
              + str(error) + ". Required action: restore exact retained permitted evidence and matching source context, or retain the skip. "
              "Preserve failures and never refresh old seals. This verifier performs no new inference/replay; SIS final-iteration analysis remains unqualified.", file=sys.stderr)
        return code


if __name__ == "__main__":
    raise SystemExit(main())
