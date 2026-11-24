"""Read scoped cytometry intensity evidence; never reruns a scientific analysis."""
from pathlib import Path
import argparse
import gzip
import hashlib
import json
import sys
import zipfile


def pin(raw):
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def file_pin(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1048576), b""):
            digest.update(block)
    return {"bytes": path.stat().st_size, "sha256": digest.hexdigest()}


def require(value, message):
    if not value:
        raise ValueError(message)


def verify(root, jar=None, private_root=None, cache=None):
    root = root.resolve()
    folder = root / "docs/literature/cytometry-intensity-validated-checkpoint"
    state = json.loads((folder / "STATUS.json").read_bytes())
    require(state["status"] == "PASS_NATIVE_UNBINNED_CYTOMETRY_INTENSITY_V1", "Unexpected profile")
    require(not state["vendor_statistics_qualified"] and not state["whole_vendor_tool_qualified"], "Expanded vendor claim")
    require(not state["catalog_admitted"] and state["new_family_credit"] == state["new_reviews"] == 0 and not state["whole_goal_complete"], "Expanded catalog or whole-goal claim")
    for name, key in (("FILES.json", "manifest"), ("evidence.zip", "archive"), ("OMISSIONS.json", "omissions")):
        require(file_pin(folder / name) == state[key], "Changed packet: " + name)
    members = json.loads((folder / "FILES.json").read_bytes())
    with zipfile.ZipFile(folder / "evidence.zip") as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())) and set(archive.namelist()) == set(members), "Unrecorded/duplicate archive members")
        payload = {}
        for name, identity in members.items():
            require(not name.startswith(("/", "\\")) and ".." not in Path(name).parts, "Unsafe archive member")
            raw = archive.read(name)
            require(pin(raw) == identity, "Changed archive member: " + name)
            payload[name] = raw
    def obj(name):
        return json.loads(payload[name])
    for name, identity in state["source_code"].items():
        require(file_pin(root / name) == identity, "Changed source: " + name)
    require(obj("normal-build-1/RESULT.json")["normal_tests"] == dict(tests=1910, failures=0, errors=0, skipped=0), "Changed initial regression scope")
    require(obj("normal-build-3/RESULT.json")["normal_tests"] == dict(tests=76, failures=0, errors=0, skipped=0), "Changed final regression scope")
    require({k: obj("normal-build-3/RESULT.json")["producer"][k] for k in ("bytes", "sha256")} == state["producer"], "Changed exact producer")
    extension = obj("INTEGRITY-EXTENSION.json")
    require(extension["mathematical_and_gate_class_payloads_unchanged"] and extension["all_final_exports_equal_independently_qualified_exports"], "Changed unqualified scientific implementation")
    numeric = obj("numerical-probe-2/RESULT.json")
    require(numeric["rows"] == 910 and numeric["raw_R_matching_rows"] == 908 and numeric["explicit_raw_R_differences"] == numeric["scaled_confirmation_rows"] == 2, "Hidden raw R underflow discrepancy")
    differences = obj("numerical-probe-2/RAW-R-COMPARISON.json")["raw_R_differences"]
    require({(r["case"], r["metric"]) for r in differences} == {("scaled--200", "SAMPLE_SD"), ("scaled--200", "CV_PERCENT")}, "Changed raw R exceptions")
    windows = obj("packaged-windows-2/SUMMARY.json")
    linux = obj("packaged-linux-2/retained/SUMMARY.json")
    for rows in (windows["cases"], linux["cases"]):
        require(len(rows) == len({r["sample"] for r in rows}) == 23, "Incomplete/duplicate sample population")
        require(all(r["pass_all"] and all(r[mode]["exit_code"] == 0 for mode in ("run", "verify", "replay", "replay_verify")) for r in rows), "Unexecuted/failed packaged check")
        require(sum(r["event_gate_pairs"] for r in rows) == 53019143, "Changed full gate comparison population")
    require(all(r["all_exported_values_and_statistics_binary64_identical_to_windows"] for r in linux["cases"]), "Host-specific result difference")
    controls = obj("controls-windows-4/SUMMARY.json")
    require(len(controls["cases"]) == 25 and all(r["exit_code"] == r["expected"] for r in controls["cases"]), "Changed boundary control outcome")
    require(controls["forged_parent_metadata_verification_passed"] and controls["forged_parent_fresh_scientific_evaluation_refused"], "Lost fresh-science forgery control")
    require(len(linux["controls"]) == 5 and all(r["exit_code"] == r["expected"] for r in linux["controls"]), "Changed Linux boundary checks")
    independent = obj("packaged-independent-reference-2/SUMMARY.json")
    exact = obj("native-values-R-reference-1/SUMMARY.json")
    require(len(independent["cases"]) == len(exact["cases"]) == 23, "Incomplete independent comparisons")
    require(all(r["count_nonpositive_count_and_status_exact"] for r in exact["cases"]), "Native formula/reference mismatch")
    require(any(c["zero_boundary_differences"] for r in independent["cases"] for c in r["checks"]), "Hidden compensation zero-boundary differences")
    roles = json.loads((root / "docs/literature/cytometry-source-roles-2026-11-23.json").read_bytes())
    require(roles["paper_tool_pairs"] == len(roles["records"]) == 44 and roles["new_reviews"] == 0, "Unadmitted source roles credited")
    require(len({(r["paper_id"], r["tool_id"]) for r in roles["records"]}) == 44, "Duplicate source role")
    require(all(r["semantic_role_read"] and not r["catalog_admitted"] and not r["paper_reproduced"] for r in roles["records"]), "Expanded source-role qualification")
    checked = 0
    if jar is not None:
        require(file_pin(Path(jar)) == state["producer"], "Supply the exact retained producer JAR")
    if private_root is not None:
        for name, identity in state["private_evidence"].items():
            require(file_pin(Path(private_root) / name) == identity, "Missing/changed private evidence: " + name)
            checked += 1
    fresh = 0
    if cache is not None:
        import xml.etree.ElementTree as ET
        sys.path.insert(0, str(root / "tools/literature"))
        from collect_omics_review import paragraphs
        for paper in obj("PRIMARY-CENSUS.json")["identities"]:
            with gzip.open(Path(cache) / (paper["pmcid"] + ".xml.gz"), "rb") as stream:
                raw = stream.read(67108865)
            require(len(raw) <= 67108864 and hashlib.sha256(raw).hexdigest() == paper["xml_sha256"], "Changed frozen XML")
            tree = ET.fromstring(raw)
            dois = [x.text for x in tree.findall("front/article-meta/article-id") if x.get("pub-id-type") == "doi"]
            require(len(dois) == 1 and dois[0].lower() == paper["doi"].lower(), "Changed DOI identity")
            selected = [r for r in roles["records"] if r["paper_id"] == paper["paper_id"]]
            if selected:
                ps = {p["paragraph_index"]: p for p in paragraphs(tree)}
                for r in selected:
                    for anchor in r["primary_anchors"]:
                        require(hashlib.sha256(ps[anchor["paragraph_index"]]["text"].encode()).hexdigest() == anchor["paragraph_sha256"], "Changed source-role paragraph")
            fresh += 1
    return dict(status=state["status"], packaged_samples_per_host=23,
                event_gate_pairs_per_pass=53019143, raw_R_control_rows=910,
                raw_R_underflow_differences_preserved=2, source_role_pairs_prepared=44,
                exact_JAR_checked=jar is not None, private_files_checked=checked,
                fresh_primary_XML_checked=fresh, scientific_analysis_reexecuted=False,
                vendor_statistics_qualified=False, catalog_admitted=False,
                new_family_credit=0, new_reviews=0, whole_goal_complete=False)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    for name in ("jar", "private-root", "cache"):
        parser.add_argument("--" + name, type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.root, args.jar, args.private_root, args.cache), indent=2))
