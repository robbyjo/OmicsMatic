"""Re-extract the frozen 250-paper cohort after a reviewed registry expansion."""
import gzip
import hashlib
import json
from collect_omics_review import CACHE, DEST, process, publication_record, write_json
from pathlib import Path


def main():
    registry_path = Path(__file__).with_name("tool-registry.json")
    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    papers = json.loads((DEST / "papers.json").read_text(encoding="utf-8"))
    previous_hash = hashlib.sha256((DEST / "papers.json").read_bytes()).hexdigest()
    history_cache = CACHE / "catalog-history"
    history_cache.mkdir(parents=True, exist_ok=True)
    previous_cache = history_cache / (previous_hash + ".json.gz")
    if not previous_cache.exists():
        previous_cache.write_bytes(gzip.compress((DEST / "papers.json").read_bytes(), mtime=0))
    registry_hash = hashlib.sha256(registry_path.read_bytes()).hexdigest()
    registry_cache = history_cache / (registry_hash + "-registry.json.gz")
    if not registry_cache.exists():
        registry_cache.write_bytes(gzip.compress(registry_path.read_bytes(), mtime=0))
    old_report = DEST / "catalog-enrichment.json"
    prior = json.loads(old_report.read_text(encoding="utf-8")) if old_report.exists() else {}
    history = prior.get("history", []) + [{k: v for k, v in prior.items() if k != "history"}] if prior else []
    metadata = {}
    for path in CACHE.glob("*-search*.json.gz"):
        response = json.loads(gzip.decompress(path.read_bytes()))
        for record in response["resultList"]["result"]:
            if record.get("pmcid"):
                metadata[record["pmcid"]] = record
    enriched, changes = [], []
    for paper in papers:
        record = dict(metadata[paper["pmcid"]]); record["review_journal"] = paper["journal_abbreviation"]
        result = process(record, registry)
        assert result["status"] == "extracted", paper["paper_id"] + ": source no longer supplies tool evidence"
        assert result["xml_sha256"] == paper["xml_sha256"] and result["doi"] == paper["doi"]
        old_ids = {t["tool_id"] for t in paper["tools"]}
        new_ids = {t["tool_id"] for t in result["tools"]}
        changes.append({"paper_id": paper["paper_id"], "added": sorted(new_ids - old_ids), "removed": sorted(old_ids - new_ids)})
        write_json(CACHE / "extracted" / (paper["pmcid"] + ".json"), result)
        published = publication_record(result); published["paper_id"] = paper["paper_id"]
        enriched.append(published)
    assert [p["doi"] for p in papers] == [p["doi"] for p in enriched], "Frozen cohort changed"
    write_json(DEST / "papers.json", enriched)
    write_json(DEST / "catalog-enrichment.json", {"previous_catalog_sha256": previous_hash,
        "registry_sha256": registry_hash, "previous_catalog_cache": str(previous_cache),
        "registry_cache": str(registry_cache), "history": history,
        "source_cohort_unchanged": True, "paper_count": len(papers), "changes": changes})
    print(json.dumps({"papers": len(enriched), "added_observations": sum(len(c["added"]) for c in changes),
                      "removed_observations": sum(len(c["removed"]) for c in changes)}))


if __name__ == "__main__":
    main()
