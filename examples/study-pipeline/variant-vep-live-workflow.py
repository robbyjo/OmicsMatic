"""Real Ensembl acceptance for one public variant; all association inputs synthetic.

run NEW_DIRECTORY --jar JAR
verify DIRECTORY --jar JAR
Run downloads a public reference and requests consequences; verify is local.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("run", "verify")); parser.add_argument("project", type=Path); parser.add_argument("--jar", type=Path, required=True)
    args = parser.parse_args(); project = args.project.resolve(); jar = args.jar.resolve()
    if args.action == "run":
        project.mkdir(parents=True, exist_ok=False)
        url = "https://rest.ensembl.org/sequence/region/human/MT:1..16569:1?content-type=text/x-fasta"
        request = urllib.request.Request(url, headers={"Accept": "text/x-fasta", "User-Agent": "JLinAlg-public-variant-acceptance"})
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read(); status = response.status
        (project / "reference-response.fasta").write_bytes(raw)
        lines = raw.decode("utf-8").splitlines(); assert lines[0].startswith(">") and "GRCh38" in lines[0]
        sequence = "".join(line.strip() for line in lines[1:]).upper(); assert len(sequence) == 16569 and sequence[0] == "G"
        (project / "reference.fa").write_text(">MT\n" + sequence + "\n", encoding="utf-8")
        (project / "results.tsv").write_text("feature_id\tbeta\tse\tp_value\tN\nMT:1:G:A\t.1\t.1\t.9\t1000\n", encoding="utf-8")
        study = {"schema_version": 1, "project": {"id": "public-vep-acceptance", "title": "Public mitochondrial variant annotation", "description": "Synthetic association statistics at a public reference coordinate; no biological association claim."}, "resources": {"network": "online", "downloads": "automatic"}, "datasets": [{"id": "a", "cohort": "synthetic", "type": "gwas", "stage": "association_results", "genome_build": "GRCh38", "files": {"results": "results.tsv"}}], "hypotheses": [{"id": "H", "description": "Retrieve real Ensembl consequences for a public variant with synthetic association results.", "outcome": "synthetic", "inputs": {"discovery": ["a"]}, "analysis": {"mode": "manual", "goals": ["variant_scoring"], "harmonization": {"effect_outliers": {"enabled": False}}, "variant_followup": {"reference_fasta": "reference.fa", "consequences": {"engine": "vep-rest"}, "alphagenome": {"enabled": False}}}}]}
        (project / "study.yaml").write_text(json.dumps(study, indent=2) + "\n", encoding="utf-8")
        provenance = {"scope": "public reference retrieval; synthetic association inputs", "url": url, "http_status": status, "retrieved_at": datetime.now(timezone.utc).isoformat(), "reference_response_sha256": hashlib.sha256(raw).hexdigest(), "sequence_length": len(sequence), "derived_fasta_change": "contig header MT; identical reference sequence", "generator_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
        (project / "reference-provenance.json").write_text(json.dumps(provenance, indent=2) + "\n", encoding="utf-8")
        shutil.copyfile(Path(__file__), project / "generator-source.py")
        subprocess.run(["java", "-jar", str(jar), "study", "auto", str(project / "study.yaml")], check=True)
    verifier = Path(__file__).with_name("verify-variant-vep-live.py")
    subprocess.run([sys.executable, "-B", str(verifier), str(project), "--jar", str(jar)], check=True)


if __name__ == "__main__":
    main()
