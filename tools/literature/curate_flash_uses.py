"""Review the two FLASH read-merger uses without conflating software homonyms."""
import gzip
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs, tool_matches, tool_sentence_context

ROOT = Path(__file__).resolve().parents[2]
PATTERN = r"(?<![A-Za-z0-9_])FLASH(?![A-Za-z0-9_])"
USES = {
    "P075": (52, "1.2.11",
        "After adapter removal with Trim Galore 0.3.1, reconstructed full paired reads using FLASH 1.2.11 in single-allele MCC analysis. Subsequent stages used BLAT 35 on capture-site mini-chromosomes, custom splitting and Bowtie2 alignment, allele assignment and ligation-junction analysis.",
        "FLASH overlap, mismatch-density, quality-encoding and orientation options are unstated. Native FLASH 1.2.11-convention read merging is a distinct step; it does not implement historical Trim Galore 0.3.1 trimming, BLAT/alternate-genome mapping, capture/SNP splitting, PCR-duplicate selection or allele-specific normalization. No whole-paper parity is inferred."),
    "P091": (51, None,
        "In the Galaxy NGS workflow, applied Trimmomatic HEADCROP 10 to the forward read only, then merged that trimmed forward read and the reverse read using FLASH with minimum overlap 10, maximum overlap 200 and maximum mismatch density 0.25. Cutadapt demultiplexing, BWA-MEM mapping, Samtools formatting and custom R/Excel variant-frequency/enrichment calculations followed.",
        "The FLASH version and quality encoding are unstated. Native merging accepts the stated 10/200/0.25 parameters with an explicitly declared encoding; the original maximum-overlap option caps the score denominator and does not reject every physically longer overlap. Forward-only HEADCROP preparation and all demultiplexing, mapping, mutation selection, replicate/control normalization and enrichment stages remain separate. Do not crop both mates or infer a historical version from this paragraph."),
}


def main():
    data = ROOT / "docs/literature/omics-2023-2026"
    papers = json.loads((data / "papers.json").read_text(encoding="utf-8"))
    registry_path = Path(__file__).with_name("tool-registry.json")
    registry = json.loads(registry_path.read_text(encoding="utf-8"))
    prior = [row for row in registry if row["id"] == "flash-merge"]
    assert len(prior) == 1
    tool = prior[0]
    assert tool["current_coverage"] == "missing" and tool["pattern"] == r"(?<![A-Za-z0-9_])(?-i:FLASH)(?![A-Za-z0-9_])"
    review_path = Path(__file__).with_name("reviewed-uses.json")
    document = json.loads(review_path.read_text(encoding="utf-8"))
    observed = {}; excluded = []; evidence = []; added = 0
    for paper in papers:
        raw = gzip.decompress((ROOT / "build/literature-review/xml" / (paper["pmcid"] + ".xml.gz")).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper["xml_sha256"]
        sources = paragraphs(ET.fromstring(raw))
        found = tool_matches(sources, [tool])
        if found: observed[paper["paper_id"]] = found
        for source in sources:
            if re.search(PATTERN, source["match_text"], re.I):
                sentences = re.split(r"(?<=[.!?])\s+(?=[A-Z])", source["match_text"])
                role = any(re.search(PATTERN, sentence, re.I) and tool_sentence_context("flash-merge", sentence) for sentence in sentences)
                if not role: excluded.append(dict(paper_id=paper["paper_id"], paragraph_index=source["paragraph_index"], text=source["text"], scope="not a FLASH read-merger role; retain independently for semantic audit"))
        if paper["paper_id"] not in USES: continue
        number, version, summary, boundary = USES[paper["paper_id"]]; source = sources[number - 1]
        assert source["paragraph_index"] == number and re.search(PATTERN, source["match_text"])
        row = dict(pmcid=paper["pmcid"], doi=paper["doi"], tool_id="flash-merge",
                   evidence_paragraph_sha256=hashlib.sha256(source["text"].encode()).hexdigest(), use_summary=summary, implementation_scope_note=boundary)
        if version is not None: row["version_reported_near_name"] = version
        existing = [r for r in document["reviews"] if (r["pmcid"], r["tool_id"]) == (row["pmcid"], row["tool_id"])]
        if not existing: document["reviews"].append(row); added += 1
        else:
            assert len(existing) == 1 and existing[0]["evidence_paragraph_sha256"] == row["evidence_paragraph_sha256"]
            document["reviews"][document["reviews"].index(existing[0])] = row
        evidence.append(dict(paper_id=paper["paper_id"], doi=paper["doi"], pmcid=paper["pmcid"], source=source, review=row))
    assert set(observed) == set(USES), observed
    assert any(row["paper_id"] == "P244" and "flash" in row["text"] for row in excluded), "Preserve matrix-factorization homonym"
    cache = ROOT / "build/literature-review/flash-runtime"
    (cache / "reviewed-source-roles.json").write_text(json.dumps(dict(merger_papers=sorted(observed), reviewed_uses=evidence, excluded_contexts=excluded, scope="Two source roles reviewed; other flash/software meanings retained for separate review, not declared absent or implemented."), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    registry_path.write_text(json.dumps(registry, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    review_path.write_text(json.dumps(document, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(dict(merger_papers=sorted(observed), reviews_added=added, excluded_paragraphs=len(excluded), registry_entities=len(registry), reviewed_uses=len(document["reviews"]))))


if __name__ == "__main__": main()
