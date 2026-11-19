# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit bounded original-Seurat workflow evidence; optional Java-only capture replay."""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[3]
PACKET = ROOT / "docs/literature/seurat-workflow-development"


def require(condition, detail):
    if not condition:
        raise ValueError(detail)


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def file_pin(path):
    with path.open("rb") as stream:
        return dict(bytes=path.stat().st_size,
                    sha256=hashlib.file_digest(stream, "sha256").hexdigest())


def safe_name(name):
    p = PurePosixPath(name)
    require(bool(name) and not p.is_absolute() and ".." not in p.parts
            and "\\" not in name and ":" not in name, "Unsafe evidence name")
    return p


def audit(out, candidate=None, parent=None, java=None):
    proof = json.loads((PACKET / "VALIDATION.json").read_bytes())
    require(proof["status"] == "PASS_SCOPED_ORIGINAL_RNA_WORKFLOW_QUALIFICATION",
            "Qualification scope/status changed")
    require(proof["new_family_credit"] == 0 and proof["whole_goal_complete"] is False,
            "Unproved catalog or goal credit")
    for name, expected in proof["source_guards"].items():
        safe_name(name)
        require(file_pin(ROOT / name) == expected, "Qualified source changed: " + name)
    archive_path = PACKET / "qualification-evidence.zip"
    require(file_pin(archive_path) == proof["archive"], "Evidence archive changed")
    with zipfile.ZipFile(archive_path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)) and set(names) == set(proof["payloads"]),
                "Evidence membership changed")
        require(sum(i.file_size for i in archive.infolist()) < 500_000_000,
                "Evidence expansion exceeds audit capacity")
        for name in names:
            safe_name(name)
            require(pin(archive.read(name)) == proof["payloads"][name],
                    "Evidence payload changed: " + name)
        for platform in ["windows", "linux"]:
            tree = ET.fromstring(archive.read("qualification/" + platform +
                                             "-junit/TEST-junit-jupiter.xml"))
            require(int(tree.attrib["tests"]) == 6 and int(tree.attrib["errors"]) == 0
                    and int(tree.attrib["failures"]) == 0
                    and int(tree.attrib.get("skipped", 0)) == 0,
                    "JUnit qualification incomplete: " + platform)
        controls = json.loads(archive.read("controls/RESULTS.json"))
        require(controls["status"] == "PASS_REAL_RUNTIME_AND_RESEALED_CORRUPTION_CONTROLS"
                and len(controls["controls"]) == 26, "Control population differs")
        for name, row in controls["controls"].items():
            require(row["exit_code"] == row["expected"], "Control outcome differs: " + name)
        require(all(not row["alive_after_cleanup"] for row in controls["owned_timeout_processes"]),
                "Owned timeout cleanup was incomplete")
        captures = proof["successful_captures"]
        require(len(captures) == 7, "Six profiles plus actual replay required")
        for prefix in captures:
            safe_name(prefix)
            sealed = json.loads(archive.read(prefix + "/capture-files.json"))
            actual = {n[len(prefix) + 1:] for n in names if n.startswith(prefix + "/")}
            require(actual == set(sealed["files"]) | {"capture-files.json"},
                    "Capture population differs: " + prefix)
            for name, digest in sealed["files"].items():
                safe_name(name)
                require(hashlib.sha256(archive.read(prefix + "/" + name)).hexdigest() == digest,
                        "Capture pin differs: " + prefix + "/" + name)
            state = json.loads(archive.read(prefix + "/analysis-status.json"))
            require(state["status"] == "completed-with-warnings" and state["results_admitted"] is True,
                    "Capture is not admitted: " + prefix)
        reference = json.loads(archive.read("reference-comparison-3/RESULTS.json"))
        require(reference["status"] == "PASS_EXACT_INPUT_ORIGINAL_REFERENCE_COMPARISON"
                and len(reference["profiles"]) == 4, "Exact-input reference comparison missing")
        for profile in reference["profiles"].values():
            for method in ["pca.tsv", "umap.tsv", "tsne.tsv"]:
                require(profile[method]["maximum_relative_to_one_error"] < 1e-10,
                        "Exact-input reference differs")
            require(profile["clusters.tsv"]["exact_cells_columns_labels"] is True,
                    "Original cluster tables differ")
        # The earlier t-SNE discrepancy remains evidence, not a discarded result.
        sensitivity = json.loads(archive.read("reference-comparison-2/RESULTS.json"))
        require(sensitivity["profiles"]["log-regression-irlba-rann-multilevel"]
                ["tsne.tsv"]["maximum_relative_to_one_error"] > 1,
                "Lost retained older-fixture t-SNE discrepancy")
        package = json.loads(archive.read("qualification/PACKAGE.json"))
        checked_members = 0
        if candidate:
            require(file_pin(candidate) == package["candidate"], "Candidate JAR changed")
            with zipfile.ZipFile(candidate) as jar:
                require(len(jar.namelist()) == len(set(jar.namelist())), "Duplicate JAR members")
                for name in package["added_members"]:
                    require(jar.read(name) == archive.read("candidate-added-payloads/" + name),
                            "Candidate added payload differs: " + name)
                if parent:
                    require(file_pin(parent) == package["parent"], "Parent JAR changed")
                    with zipfile.ZipFile(parent) as old:
                        require(set(jar.namelist()) == set(old.namelist()) | set(package["added_members"]),
                                "Candidate member population differs")
                        for name in old.namelist():
                            require(old.read(name) == jar.read(name), "Prior JAR payload changed: " + name)
                            checked_members += 1
        java_verifications = 0
        if java:
            require(candidate is not None, "--java requires --candidate-jar")
            extracted = out / "captured-evidence"
            for prefix in captures:
                for name in names:
                    if name.startswith(prefix + "/"):
                        target = extracted / Path(*safe_name(name).parts)
                        target.parent.mkdir(parents=True, exist_ok=True)
                        with target.open("xb") as stream:
                            stream.write(archive.read(name))
                command = [str(java), "-Xmx1g", "-cp", str(candidate),
                           "org.jlinalg.cli.SeuratWorkflowCli", "--request",
                           str(extracted / prefix / "verify-request.json")]
                i = java_verifications
                with (out / ("java-" + str(i) + ".stdout")).open("xb") as so, \
                        (out / ("java-" + str(i) + ".stderr")).open("xb") as se:
                    process = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=so,
                                             stderr=se, timeout=180, check=False)
                require(process.returncode == 0, "Java capture audit failed: " + prefix)
                java_verifications += 1
    return dict(status="PASS_SEURAT_WORKFLOW_SOURCE_AND_EVIDENCE_AUDIT",
                payloads=len(names), successful_captures=len(captures), controls=26,
                tests_per_JVM=6, prior_JAR_members_checked_now=checked_members,
                java_capture_verifications_now=java_verifications,
                original_R_reexecuted_now=False, installed_DLL_authenticated=False,
                new_family_credit=0, whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--candidate-jar", type=Path)
    parser.add_argument("--parent-jar", type=Path)
    parser.add_argument("--java", type=Path)
    args = parser.parse_args()
    args.out = args.out.resolve()
    args.out.mkdir(parents=True, exist_ok=False)
    try:
        require(not args.parent_jar or args.candidate_jar,
                "--parent-jar requires --candidate-jar")
        result = audit(args.out, args.candidate_jar.resolve() if args.candidate_jar else None,
                       args.parent_jar.resolve() if args.parent_jar else None,
                       args.java.resolve() if args.java else None)
        (args.out / "RESULTS.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result, indent=2))
    except Exception as error:
        (args.out / "FAILURE.json").write_text(json.dumps(dict(status="FAIL2_NO_COMPLETE_AUDIT",
             error=str(error), required_action="Preserve this attempt; restore exact source/evidence or supply the named matching JAR/runtime, then retry a NEW output path."), indent=2) + "\n", encoding="utf-8")
        print("FAIL2: " + str(error) + ". Read " + str(args.out / "FAILURE.json"), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
