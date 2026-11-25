"""Readonly complete CrossMap checkpoint and saved-contract verification.

Use the exact producing JAR and retained private evidence. This reader does not
install software, run a source conversion or refresh a historical expected hash.
"""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import sys
import zipfile

from crossmap_primary import audit, pin, require

ROOT = Path(__file__).resolve().parents[2]
FOLDER = "docs/literature/crossmap-workflow-checkpoint"
STATUS = "PASS_ORIGINAL_CROSSMAP_SELECTED_FORMATS_AND_PORTABLE_CAPTURE"


def file_pin(path):
    with Path(path).open("rb") as stream:
        return dict(bytes=Path(path).stat().st_size, sha256=hashlib.file_digest(stream, "sha256").hexdigest())


def member(name):
    require(isinstance(name, str) and name and "\\" not in name and ":" not in name
            and not name.startswith("/") and all(x not in ("", ".", "..") for x in name.split("/")),
            "Unsafe checkpoint member")
    return name


def compare_jars(before, after, expected):
    require(file_pin(before) == expected["old"] and file_pin(after) == expected["new"], "Restore exact producer JARs")
    with zipfile.ZipFile(before) as a, zipfile.ZipFile(after) as b:
        left, right = set(a.namelist()), set(b.namelist())
        require(len(left) == len(a.namelist()) and len(right) == len(b.namelist()), "Duplicate producer archive member")
        changed = [n for n in sorted(left & right) if a.read(n) != b.read(n)]
        require(changed == expected["changed"] == ["org/jlinalg/cli/JLinAlgCli.class"]
                and sorted(right - left) == expected["added"] and sorted(left - right) == expected["removed"] == [],
                "Producer changed outside the selected CrossMap additions/dispatcher")
        require(len(left & right) - len(changed) == expected["common_unchanged"] == 24595,
                "Changed complete producer member population")
        require(sum(n.startswith("org/jlinalg/genetics/") and n.endswith(".class") for n in left) ==
                expected["scientific_classes_unchanged"] == 681, "Changed prior genetics class population")


def verify(args):
    root, private = args.root.absolute(), args.private_root.absolute()
    folder = root / FOLDER
    state = json.loads((folder / "STATUS.json").read_bytes())
    require(state["status"] == STATUS and state["whole_paper_reproduced"] is False
            and state["catalog_admitted"] is False, "Expanded or changed workflow checkpoint scope")
    for name, wanted in json.loads((folder / "FILES.json").read_bytes()).items():
        require(file_pin(root / member(name)) == wanted, "Changed exact source context: " + name)
    for name, wanted in state["evidence"].items():
        require(file_pin(folder / member(name)) == wanted, "Changed checkpoint evidence: " + name)
    require(file_pin(args.producer) == state["producer"], "Restore the exact producing JAR; do not refresh old seals")
    require(file_pin(args.predecessor_producer) == state["predecessor_producer"], "Restore exact predecessor JAR")
    primary = audit(root, args.cache)
    index = json.loads((folder / "PRIVATE-INDEX.json").read_bytes())
    for name, wanted in index.items():
        path = private / member(name)
        if not path.is_file():
            raise FileNotFoundError("Restore retained private checkpoint member: " + name)
        require(not path.is_symlink() and file_pin(path) == wanted, "Changed retained evidence: " + name)
    comparison = json.loads((folder / "COMPATIBILITY.json").read_bytes())
    compare_jars(args.predecessor_producer, args.producer, comparison)
    validation = json.loads((folder / "VALIDATION.json").read_bytes())
    require(validation["admitted_captures"] == 26 and validation["intentional_conversion_refusals"] == 17
            and validation["Java_tests_windows"] == validation["Java_tests_linux"] == 26,
            "Incomplete qualified workflow populations")
    payloads = json.loads((folder / "PAYLOADS.json").read_bytes())
    require(len(payloads) == len(set(payloads)) == 26, "Missing/duplicate complete captured result")
    for name in payloads:
        command = [str(args.java), "-Xmx2g", "-jar", str(args.producer), "crossmap-tools", "--verify", str(private / member(name))]
        result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=120)
        require(result.returncode == 0, "Complete captured-result refusal: " + name + "; " + result.stderr.decode("utf-8", "replace"))
    # A readback may not silently alter any retained witness or input.
    for name, wanted in index.items():
        require(file_pin(private / name) == wanted, "Evidence changed during complete checkpoint readback")
    return dict(status=STATUS, public_source_files_checked=len(json.loads((folder / "FILES.json").read_bytes())),
                private_files_checked=len(index), saved_captures_checked=26,
                primary_XML_checked=primary["primary_XML_checked"],
                original_source_conversion_reexecuted=False, whole_paper_reproduced=False, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--private-root", type=Path, required=True)
    parser.add_argument("--producer", type=Path, required=True)
    parser.add_argument("--predecessor-producer", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--cache", type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args), sort_keys=True))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(("SKIP3 DEPENDENT CHECKPOINT READBACK SKIPPED: " if code == 3 else "FAIL2 NO CHECKPOINT READBACK ADMITTED: ")
              + str(error) + ". Preserve every attempt; restore the exact named permitted producer/source/evidence. "
              "Never refresh historical expected hashes or treat a partial conversion as complete.", file=sys.stderr)
        return code


if __name__ == "__main__":
    raise SystemExit(main())
