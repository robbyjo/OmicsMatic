"""Read the scoped native FlowJo-workspace evidence without rerunning scientific analysis."""
from pathlib import Path
import argparse
import gzip
import hashlib
import json
import zipfile


def pin(raw):
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def file_pin(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1048576), b""):
            h.update(chunk)
    return {"bytes": path.stat().st_size, "sha256": h.hexdigest()}


def require(value, message):
    if not value:
        raise ValueError(message)


def verify(root, jar=None, private_root=None, cache=None):
    root = root.resolve()
    folder = root / "docs/literature/flowjo-workspace-checkpoint"
    state = json.loads((folder / "STATUS.json").read_bytes())
    require(state["status"] == "PASS_NATIVE_FLOWJO_WSP20_FLOWKIT132_MEMBERSHIP_COUNTS", "Wrong qualified profile")
    require(not state["catalog_admitted"] and state["new_family_credit"] == state["new_reviews"] == 0 and not state["whole_goal_complete"], "Component claim expanded into catalog or whole-goal admission")
    require(not state["whole_vendor_tool_qualified"] and not state["descriptive_intensity_statistics_computed"], "Expanded vendor/statistics claim")
    for name, key in (("FILES.json", "manifest"), ("evidence.zip", "archive"), ("OMISSIONS.json", "omissions")):
        require(file_pin(folder / name) == state[key], "Changed evidence packet: " + name)
    manifest = json.loads((folder / "FILES.json").read_bytes())
    require(len(manifest) == state["logical_files"], "Changed evidence population")
    with zipfile.ZipFile(folder / "evidence.zip") as z:
        require(len(z.namelist()) == len(set(z.namelist())) == state["unique_objects"], "Duplicate or changed archive objects")
        require(set(z.namelist()) == {"objects/" + v["sha256"] for v in manifest.values()}, "Unrecorded archive objects")
        require(sum(x.file_size for x in z.infolist()) < 150000000, "Oversized evidence archive")
        objects = {name: z.read(name) for name in z.namelist()}
    for name, identity in manifest.items():
        require(not name.startswith("/") and ".." not in name.split("/") and "\\" not in name and ":" not in name, "Unsafe evidence member")
        require(pin(objects["objects/" + identity["sha256"]]) == identity, "Changed saved evidence: " + name)

    def raw(name):
        return objects["objects/" + manifest[name]["sha256"]]

    def obj(name):
        return json.loads(raw(name))

    for name, identity in state["source_code"].items():
        require(file_pin(root / name) == pin(raw("source/" + name)) == identity, "Changed source: " + name)
    normal1, normal2 = obj("normal-build-1/RESULT.json"), obj("normal-build-2/RESULT.json")
    require(normal1["exit_code"] == normal2["exit_code"] == 0, "Failed normal build")
    require(normal1["normal_tests"] == dict(tests=1900, failures=0, errors=0, skipped=0), "Changed initial test scope")
    require(normal2["normal_tests"] == dict(tests=66, failures=0, errors=0, skipped=0), "Changed final regression scope")
    require({k: normal2["producer"][k] for k in ("bytes", "sha256")} == state["producer"], "Changed qualified producer")
    require(obj("PAYLOAD-CONTINUITY.json")["modified"] == ["org/jlinalg/cli/FlowJoToolsCli.class"], "Changed science between normal builds")
    expected = {}
    for host in ("windows", "linux"):
        q = obj("packaged-" + host + "-3/SUMMARY.json")
        require(q["status"] == "PASS_ALL_23_COMPLETE_CASES_NATIVE_RUN_VERIFY_REPLAY" and q["all_pass"] and len(q["cases"]) == 23, "Incomplete host qualification: " + host)
        pairs = 0
        seen = set()
        for row in q["cases"]:
            require(row["sample"] not in seen, "Duplicate sample qualification")
            seen.add(row["sample"])
            require(row["pass"] and row["membership_replay_equal"] and row["counts_replay_equal"], "Failed replay")
            require(all(row[mode]["exit_code"] == 0 for mode in ("gate", "verify", "replay", "replay_verify")), "Missing actual packaged command")
            for kind in ("comparison", "replay_comparison"):
                c = row[kind]
                require(c["event_gate_differences"] == 0 and c["native_sha256"] == c["reference_sha256"] and c["result_admitted"] and not c["whole_vendor_tool_qualified"] and c["saved_count_policy"] == "REPORT_DIFFERENCES", "Changed scientific comparison/profile")
            pairs += row["comparison"]["event_gate_pairs"]
            identity = row["comparison"]["reference_sha256"]
            if host == "windows":
                expected[row["sample"]] = identity
            else:
                require(expected.get(row["sample"]) == identity, "Different host sample/reference population")
        require(pairs == q["event_gate_pairs_per_pass"] == 53019143, "Changed complete event-gate population")
        controls = obj("controls-" + host + "-1/SUMMARY.json")
        require(controls["original_reference_inputs_unchanged"] and len(controls["cases"]) == 18, "Incomplete actual boundary controls")
        require(all(c["exit_code"] == c["expected"] and (c["expected"] == 0 or not c["complete_marker"]) for c in controls["cases"]), "Wrong refusal/positive outcome")
    ref = obj("reference-analysis-1/SUMMARY.json")
    require(len(ref["cases"]) == 14, "Changed upstream fixture population")
    failures = [r for r in ref["cases"] if r["status"] != "REFERENCE_EXECUTED_COMPLETE_MEMBERSHIP"]
    require(len(failures) == 1 and failures[0]["case"] == 3 and "GateTreeError" in failures[0]["error"], "Hidden upstream dot-gate failure")
    require(any(s["saved_counts_different"] > 0 for r in ref["cases"] for s in r.get("samples", [])), "Hidden saved-vendor-count disagreements")
    previous = obj("predecessor-readback-1/RESULT.json")
    require(previous["status"] == "PASS_UNCHANGED_IMPUTE_CATALOG_IN_PHYSICAL_PRE_EXTENSION_CONTEXT" and previous["measured"]["fresh_primary_XML_checked"] == 250 and not previous["historical_source_or_hashes_refreshed"], "Changed historical provenance")
    extension = obj("predecessor-readback-1/SOURCE-EXTENSION.json")
    require(extension["status"] == "EXACT_THREE_LINE_ADDITIVE_MAIN_EXTENSION" and extension["all_other_predecessor_members_unchanged"] == 1674, "Expanded shared-source change")
    census = obj("primary-audit-1/CENSUS.json")
    require(census["papers_checked"] == len(census["identities"]) == 250 and not census["semantic_review_complete"] and not census["canonical_modified"], "Draft census falsely admitted")
    require(obj("reference-install-1/RESULT.json")["exit_code"] == 1 and obj("reference-install-2/RESULT.json")["exit_code"] == 0, "Hidden failed install attempt")
    checked = 0
    if jar is not None:
        require(file_pin(Path(jar)) == state["producer"], "Supply the exact retained producer JAR; no current-build substitution")
    if private_root is not None:
        for name, identity in state["private_evidence"].items():
            require(file_pin(Path(private_root) / name) == identity, "Changed/missing retained private evidence: " + name)
            checked += 1
    fresh = 0
    if cache is not None:
        import xml.etree.ElementTree as ET
        for paper in census["identities"]:
            with gzip.open(Path(cache) / (paper["pmcid"] + ".xml.gz"), "rb") as stream:
                xml = stream.read(67108865)
            require(len(xml) <= 67108864 and hashlib.sha256(xml).hexdigest() == paper["xml_sha256"], "Changed frozen XML")
            dois = [x.text for x in ET.fromstring(xml).findall("front/article-meta/article-id") if x.get("pub-id-type") == "doi"]
            require(len(dois) == 1 and dois[0].lower() == paper["doi"].lower(), "Changed primary DOI identity")
            fresh += 1
    return dict(status=state["status"], actual_sample_runs_per_host=23, actual_replays_per_host=23,
                complete_event_gate_pairs_per_pass=53019143, actual_checks_per_host=18,
                exact_JAR_checked=jar is not None, private_files_checked=checked, fresh_primary_XML_checked=fresh,
                source_and_saved_evidence_checked=True, scientific_analysis_reexecuted=False,
                catalog_admitted=False, new_family_credit=0, new_reviews=0, whole_goal_complete=False)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    for option in ("jar", "private-root", "cache"):
        parser.add_argument("--" + option, type=Path)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.root, args.jar, args.private_root, args.cache), indent=2))
    except Exception as error:
        raise SystemExit("FAIL2: FlowJo workspace checkpoint invalid: " + repr(error) + ". Preserve evidence, restore the exact saved source/producer/inputs, and keep skipped analyses explicit; do not refresh historical hashes.") from error
