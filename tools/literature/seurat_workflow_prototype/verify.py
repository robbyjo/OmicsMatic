"""Read-only integrity/status audit of the exploratory Seurat handover packet."""
from pathlib import Path, PurePosixPath
import hashlib
import json
import sys
import zipfile


ROOT = Path(__file__).resolve().parents[3]
PACKET = ROOT / "docs/literature/seurat-workflow-handover"
PROFILES = {
    "log-exact-rann-louvain",
    "log-default-annoy-slm",
    "log-regression-irlba-rann-multilevel",
    "sct-v2-igraph-leiden",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pin(raw):
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def verify():
    manifest = json.loads((PACKET / "SNAPSHOT.json").read_bytes())
    require(manifest["status"] == "EXPLORATORY_PROTOTYPE_SNAPSHOT", "Wrong scope")
    require(manifest["new_family_credit"] == 0, "Prototype cannot award credit")
    require(manifest["production_adapter_implemented"] is False, "False implementation claim")
    path = PACKET / "prototype-evidence.zip"
    require(path.stat().st_size < 20_000_000, "Unexpected archive size")
    require(pin(path.read_bytes()) == manifest["archive"], "Archive bytes changed")
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "Duplicate archive members")
        require(set(names) == set(manifest["payloads"]), "Archive membership changed")
        for name in names:
            parts = PurePosixPath(name)
            require(not parts.is_absolute() and ".." not in parts.parts and "\\" not in name,
                    "Unsafe archive name")
            require(pin(archive.read(name)) == manifest["payloads"][name],
                    "Payload changed: " + name)
        results = json.loads(archive.read("prototype-2/RESULTS.json"))
        require(set(results) == PROFILES, "Wrong saved profile population")
        for name in sorted(PROFILES):
            row = results[name]
            require(row == json.loads(archive.read("prototype-2/" + name + "/RESULT.json")),
                    "Summary disagrees: " + name)
            require(row["status"] == "PASS_ORIGINAL_PROTOTYPE" and row["stage"] == "export",
                    "Unsuccessful saved profile: " + name)
            require(row["cells"] == 96 and row["pca_dim"] == [96, 10],
                    "Unexpected saved dimensions: " + name)
            require(row["config"]["name"] == name, "Profile identity changed")
            require(row["features"] == (299 if name.startswith("sct-") else 300),
                    "Unexpected saved feature population")
            for filename in ["ORIGINAL-SEURAT.rds", "nn.mtx", "snn.mtx", "pca.tsv",
                             "umap.tsv", "tsne.tsv", "clusters.tsv"]:
                require(manifest["payloads"]["prototype-2/" + name + "/" + filename]["bytes"] > 0,
                        "Missing/empty saved output")
        require(results["log-exact-rann-louvain"]["pca_spectrum_length"] == 80,
                "Lost full exact PCA spectrum")
        failure = json.loads(archive.read("prototype-1/FAILURE.json"))
        require(failure["status"] == "RETAINED_PROTOTYPE_REPORT_SERIALIZATION_FAILURE",
                "Lost historical failed attempt")
        require("prototype-1/RESULTS.json" not in names, "Historical failure misrepresented")
        packages = json.loads(archive.read("runtime-probe-1/PACKAGES.json"))
        require(packages["R"] == "4.6.1", "Unexpected recorded R version")
        indexed = {p["package"]: p for p in packages["packages"]}
        require(indexed["Seurat"]["version"] == "5.6.0", "Unexpected recorded Seurat")
        require(not indexed["leidenbase"]["available"] and not indexed["leiden"]["available"],
                "Missing-backend record changed")
        source = json.loads(archive.read("source-1/SOURCE.json"))
        require(source["commit"] == "75dfa8603befe31505e6287d32dc86c607732bd6",
                "Unexpected recorded source commit")
    return dict(status="PASS_EXPLORATORY_SNAPSHOT_INTEGRITY_AND_SAVED_STATUSES",
                payloads=len(names), saved_successful_profiles=len(PROFILES),
                retained_failed_attempts=1, original_models_reexecuted=False,
                production_adapter_implemented=False, new_family_credit=0,
                whole_goal_complete=False)


if __name__ == "__main__":
    try:
        print(json.dumps(verify(), indent=2))
    except Exception as error:
        print("FAIL2: handover snapshot verification failed: " + str(error) +
              ". Preserve it and restore the exact committed packet before retrying.",
              file=sys.stderr)
        sys.exit(2)
