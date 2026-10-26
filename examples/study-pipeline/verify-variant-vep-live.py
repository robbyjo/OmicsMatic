"""Verify a completed native Ensembl public-variant acceptance run and pin its JAR.

python -B examples/study-pipeline/verify-variant-vep-live.py DIRECTORY --jar JAR
This validates retrieval/identity/transcript preservation, not functional accuracy.
"""
import argparse
from collections import Counter
import csv
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

import yaml


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def table(path):
    with path.open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream, delimiter="\t"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", type=Path); parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args(); project = args.project.resolve(); jar = args.jar.resolve()
    run = yaml.safe_load((project / "state/project-status.yaml").read_text(encoding="utf-8"))["run_id"]
    operations = project / "runs" / run / "operations"
    folders = [path for path in operations.iterdir() if path.name.endswith("-variant_scoring")]
    assert len(folders) == 1
    folder = folders[0]; manifest = yaml.safe_load((folder / "manifest.yaml").read_text(encoding="utf-8"))
    assert manifest["state"] == "complete"
    provenance = json.loads((project / "reference-provenance.json").read_text(encoding="utf-8"))
    assert sha(project / "reference-response.fasta") == provenance["reference_response_sha256"]
    raw_sequence = "".join((project / "reference-response.fasta").read_text(encoding="utf-8").splitlines()[1:])
    derived_sequence = "".join((project / "reference.fa").read_text(encoding="utf-8").splitlines()[1:])
    assert raw_sequence == derived_sequence and len(derived_sequence) == 16569 and derived_sequence[0] == "G"
    inventory = table(folder / "variant-inventory.tsv")
    assert len(inventory) == 1 and inventory[0]["variant_id"] == "GRCh38:MT:1:G:A"
    assert inventory[0]["reference_status"] == "reference_matched" and inventory[0]["consequence_status"] == "annotated"
    assert inventory[0]["alphagenome_status"] == "disabled_by_protocol"
    expected = Counter(); releases = set(); batches = list((folder / "consequences").glob("batch-*"))
    for batch in batches:
        release = yaml.safe_load((batch / "provider-release.yaml").read_text(encoding="utf-8"))
        assert release["genome_build"] == "GRCh38" and sha(batch / "ensembl-info-data.json") == release["raw_sha256"]
        releases.update(release["releases"])
        for response in (batch / "output").glob("response-*.json"):
            for item in json.loads(response.read_text(encoding="utf-8")):
                assert item["assembly_name"] == "GRCh38"
                assert item["input"].split()[:5] == ["MT", "1", "GRCh38:MT:1:G:A", "G", "A"]
                for transcript in item["transcript_consequences"]:
                    consequence = "[" + ", ".join(transcript["consequence_terms"]) + "]"
                    expected[("GRCh38:MT:1:G:A", transcript.get("gene_id", "NA"), transcript.get("transcript_id", "NA"), consequence)] += 1
    observed = Counter((row["variant_id"], row["gene"], row["transcript"], row["consequence"]) for row in table(folder / "variant-consequences.tsv"))
    assert observed == expected and observed
    subprocess.run(["java", "-jar", str(jar), "study", "verify", "--project", str(project)], check=True)
    pinned = project / "software" / f"jlinalg-{sha(jar)}.jar"; pinned.parent.mkdir(exist_ok=True)
    if not pinned.exists(): shutil.copyfile(jar, pinned)
    verifier = project / "live-verifier-source.py"
    if verifier.exists(): assert sha(verifier) == sha(Path(__file__)), "Use the pinned verifier for a historical bundle"
    else: shutil.copyfile(Path(__file__), verifier)
    report = {"scope": "one public mitochondrial variant, synthetic association results; real Ensembl retrieval", "run_id": run, "jar_sha256": sha(jar), "tested_jar": str(pinned.relative_to(project)), "verifier_sha256": sha(verifier), "reference_source_sha256": provenance["reference_response_sha256"], "ensembl_releases": sorted(releases), "transcript_consequence_rows": sum(observed.values()), "raw_response_rows_reconstructed": True, "reference_identity_verified": True, "alphagenome_requests": 0, "installed_tools_tested": False, "functional_prediction_accuracy_tested": False, "full_pipeline_conformance": False}
    (project / "live-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
