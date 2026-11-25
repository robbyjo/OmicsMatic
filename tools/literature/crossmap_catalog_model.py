"""Deterministic CrossMap catalog transition; preserve raw evidence and prior reviews."""
from collections import Counter
from pathlib import Path
import copy
import html
import json
import re

from crossmap_primary import encoded, pin, require

ROOT = Path(__file__).resolve().parents[2]
DATA = "docs/literature/omics-2023-2026"
META = [DATA + "/" + n for n in ("papers.json", "tool-inventory.json", "summary.json", "README.md", "WARNINGS.md")]
META += ["tools/literature/reviewed-uses.json", "site/literature-review.html", "site/literature-warnings.html"]
REVIEWED = "usage-summary-reviewed; paper-wide-completeness-review-open"
PROGRAM = "src/main/java/org/jlinalg/cli/CrossmapToolsCli.java"
GUIDE = "docs/literature/CROSSMAP_WORKFLOWS.md"
COVERAGE = DATA + "/crossmap-source-role-coverage.json"
PROOF = DATA + "/crossmap-catalog-validation.json"
SCOPE = (
    "Original CrossMap 0.5.2/0.6.3 BED, VCF and BigWig coordinate conversion through crossmap-tools, "
    "with a separate pinned private Linux CPython3.12.3 runtime. Original sources perform mapping, "
    "allele handling and signal merging; Java captures complete inputs and source outcomes, checks "
    "every record witness, independently regenerates the target FASTA index/checks mapped REF bases, "
    "and reads every raw BigWig interval. Exact-producer saved verification works on Windows and Linux. "
    "This is one scoped upstream family; the related native basic chain mapper receives no additional credit. "
    "No BAM/GFF/MAF support, unreported author edition/format/flags, author Python3.7 environment, "
    "phasing, imputation, downstream QC/statistical workflow or complete paper reproduction is claimed."
)
NOTE = (
    "P003: BigWig hg19-to-hg38, version0.5.2. P072: GWAS harmonization to hg19, edition/format "
    "unreported and Python3.7 reported. P108: typed genotypes to GRCh38, version0.6.3, format unreported; "
    "availability of VCF conversion does not establish author VCF use. P242: DHS intervals hg38-to-hg19 "
    "with a UCSC chain; subsequent GenomicRanges SNP overlap is separate. The UCSC directory link is "
    "a chain-data source, not evidence of UCSC liftOver execution."
)
ACTION = (
    "SKIP3: DEPENDENT ANALYSIS SKIPPED when the exact permitted platform, interpreter, runtime, "
    "Linux helper or named capability is unavailable. Download/private installation is allowed where "
    "the environment permits; otherwise retain the skip and supply the named exact prerequisite. "
    "FAIL2: NO COMPLETE RESULT for changed/invalid input/runtime, process/capacity, source output or "
    "finalization failure. Read WARNINGS.md, FAILURE.json/BRIDGE-FAILURE.json, source stderr and the "
    "complete OUTPUT-LEDGER.jsonl. Preserve originals and failed/partial results; correct the cause "
    "and use NEW paths. Original indel, BED20, reverse BED12, signal-discard/summation and empty-track "
    "limitations remain visible. Keep the exact producer JAR and private runtime/evidence; never refresh "
    "historical seals. Released0.4.1 assets predate this source-built command. Cell Ranger remains excluded. "
    "The unfinished literature reports remain withheld from Pages."
)
CURRENT = (
    "250 papers, 446 labels, 485 provider definitions and 2,724 observations; 1,772 reviewed/952 pending uses. "
    "162 scoped families (30 native + 132 upstream), 165 including three existing adapters; "
    "253 missing and 16 method-overlap. This admission adds one CrossMap upstream family and three reviews, "
    "refining the existing P242 review without duplicate credit. The full implementation goal remains unfinished."
)
NOTICE = (
    '<aside id="crossmap-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);'
    'margin:1rem;padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
    '<h2>CrossMap: original conversions and four paper uses</h2><p>' + html.escape(CURRENT) + '</p>'
    '<p><strong>Warnings and required actions:</strong> ' + html.escape(ACTION) + '</p><p>' + html.escape(NOTE)
    + '</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/' + GUIDE
    + '">Commands, source roles and remaining work</a></p></aside>'
)


def registry():
    raw = (ROOT / "docs/literature/prsice-catalog-admission/REGISTRY.json").read_bytes()
    require(pin(raw) == dict(bytes=240127, sha256="fc9a01358b3879b33cc113afc19084601b9ee3f30434e542b24be59658806a44"),
            "Changed exact preceding provider registry; do not refresh historical hashes")
    rows = json.loads(raw)
    target = next(row for row in rows if row["id"] == "crossmap")
    require(len(rows) == 485 and target["current_coverage"] == "missing", "Changed predecessor provider population")
    target.update(current_coverage="upstream-adapter-validated", existing_source=PROGRAM, coverage_note=SCOPE + " " + NOTE)
    return rows


def applied(role):
    result = copy.deepcopy(role)
    result.update(source_role_review_applied=True, catalog_admitted=True,
                  reported_version=role["reported_edition"] or "unreported",
                  historical_analysis_status="Original selected source workflows qualified; full author commands/data and paper analyses were not rerun.",
                  paper_edition_qualified=False)
    return result


def validate(before, roles, qualification):
    require(roles == json.loads((ROOT / "docs/literature/crossmap-source-audit/ROLES.json").read_bytes()),
            "Changed frozen source-role interpretation")
    require(set(before) == set(META), "Changed predecessor metadata population")
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in (0, 1, 2, 5)]
    require(len(papers) == 250 and len(inventory) == 446 and sum(len(p["tools"]) for p in papers) == 2724,
            "Changed frozen paper/observation/label population")
    require(len(ledger["reviews"]) == 1769 and summary["integration_family_counts"] ==
            dict(scoped=161, native=30, upstream=131, including_existing_adapters=164,
                 validated_alias_labels_excluded_from_extra_credit=3), "Changed preceding admission counts")
    target = next(row for row in inventory if row["id"] == "crossmap")
    require(target["implementation_status"] == "missing" and target["papers"] == ["P003", "P072", "P108", "P242"],
            "CrossMap was already admitted or its source population changed")
    prior = [r for r in ledger["reviews"] if r["tool_id"] == "crossmap"]
    require(len(prior) == 1 and prior[0]["pmcid"] == "PMC12461027", "Changed existing P242 review")
    require([r["paper_id"] for r in roles["roles"]] == target["papers"], "Changed source-role population")
    require(qualification["status"] == "PASS_ORIGINAL_CROSSMAP_SELECTED_FORMATS_AND_PORTABLE_CAPTURE"
            and qualification["source_editions"] == ["0.5.2", "0.6.3"]
            and qualification["selected_formats"] == ["bed", "vcf", "bigwig"]
            and qualification["admitted_captures"] == 26 and qualification["intentional_conversion_refusals"] == 17
            and qualification["Java_tests_windows"] == qualification["Java_tests_linux"] == 26
            and qualification["full_primary_XML_checked"] == 250
            and qualification["whole_paper_reproduced"] is False, "Missing complete scoped workflow qualification")
    for role in roles["roles"]:
        use = next(u for p in papers if p["paper_id"] == role["paper_id"] for u in p["tools"] if u["tool_id"] == "crossmap")
        require({k: use.get(k) for k in role["raw_source_fields"]} == role["raw_source_fields"], "Changed raw version/source fields")


def transition(before, roles, qualification):
    validate(before, roles, qualification)
    papers, inventory, summary, ledger = [json.loads(before[META[i]]) for i in (0, 1, 2, 5)]
    for role in roles["roles"]:
        paper = next(p for p in papers if p["paper_id"] == role["paper_id"])
        use = next(u for u in paper["tools"] if u["tool_id"] == "crossmap")
        pointers = copy.deepcopy(use.get("supporting_evidence", []))
        for anchor in role["primary_anchors"]:
            if anchor["paragraph_index"] != use["evidence_paragraph_index"] and not any(
                    p.get("paragraph_index") == anchor["paragraph_index"] and p.get("paragraph_sha256") == anchor["paragraph_sha256"] for p in pointers):
                pointers.append(copy.deepcopy(anchor))
        scope = role["remaining_scope"] + " Available scoped implementation: " + SCOPE
        use.update(use_summary=role["use_summary"], implementation_scope_note=scope, review_status=REVIEWED,
                   evidence_selection="individually-reviewed-source-role", supporting_evidence=pointers,
                   source_role_qualification=applied(role))
        entry = dict(pmcid=paper["pmcid"], doi=paper["doi"], tool_id="crossmap",
                     evidence_paragraph_sha256=use["evidence_paragraph_sha256"], use_summary=role["use_summary"],
                     implementation_scope_note=scope, usage_context=use["usage_context"],
                     version_reported_near_name=use["version_reported_near_name"], supporting_evidence=copy.deepcopy(pointers),
                     reported_settings=copy.deepcopy(role["reported_settings"]), source_role_qualification=applied(role))
        old = next((r for r in ledger["reviews"] if (r["pmcid"], r["tool_id"]) == (paper["pmcid"], "crossmap")), None)
        if old is None:
            ledger["reviews"].append(entry)
        else:
            entry["prior_review"] = copy.deepcopy(old)
            old.update(entry)
    target = next(r for r in inventory if r["id"] == "crossmap")
    target.update(current_coverage="upstream-adapter-validated", implementation_status="upstream-adapter-validated",
                  existing_source=PROGRAM, implementation_source=PROGRAM, coverage_note=SCOPE + " " + NOTE,
                  implementation_scope=SCOPE + " " + NOTE, current_documentation=GUIDE,
                  current_qualification="../crossmap-workflow-checkpoint/STATUS.json",
                  upstream_runtime_adapter_required=True, native_component_available=True, native_component_family_credit=0,
                  native_component_source="src/main/java/org/jlinalg/genetics/ChainLiftover.java",
                  public_CLI_available=True, global_CLI_dispatch_available=True, source_build_required=True,
                  released_binary_contains_workflow=False, whole_tool_qualified=False, whole_paper_reproduced=False,
                  whole_goal_complete=False, new_native_family_credit=0, new_upstream_family_credit=1,
                  source_roles_reviewed=4, new_source_reviews=3, refined_existing_reviews=1,
                  source_role_coverage=COVERAGE.rsplit("/", 1)[-1],
                  paper_role_qualification={r["paper_id"]: applied(r) for r in roles["roles"]},
                  current_paper_role_limits=NOTE,
                  validation_note="26 admitted captures verified on Windows/Linux; 17 intentional conversion refusals; 26 Java tests per platform. Eight human-chain cases agree with independent native HTSJDK coordinates for five full-block queries each. No original study was reexecuted.")
    summary["implementation_status_counts"] = dict(Counter(r["implementation_status"] for r in inventory))
    summary["integration_family_counts"] = dict(scoped=162, native=30, upstream=132, including_existing_adapters=165,
                                               validated_alias_labels_excluded_from_extra_credit=3)
    after = dict(before)
    for name, value in ((META[0], papers), (META[1], inventory), (META[2], summary), (META[5], ledger)):
        raw = encoded(value)
        after[name] = raw.replace(b"\n", b"\r\n") if before[name].count(b"\n") == before[name].count(b"\r\n") else raw
    for name in META[6:]:
        page = before[name].decode()
        require('id="crossmap-catalog-notice"' not in page and 'id="prsice-catalog-notice"' in page, "Changed prior notice")
        if name == META[6]:
            match = re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)', page, re.S)
            require(match and json.loads(match[2]) == dict(papers=json.loads(before[META[0]]), tools=json.loads(before[META[1]]), summary=json.loads(before[META[2]])), "Changed embedded predecessor")
            payload = json.dumps(dict(papers=papers, tools=inventory, summary=summary), ensure_ascii=False).replace("<", "\\u003c")
            page = page[:match.start(2)] + payload + page[match.end(2):]
            require(page.count("${prsiceVersion(t)}") == 1 and page.count("function renderContent(){") == 1, "Changed source-role renderer")
            helper = "function crossmapVersion(t){if(t.tool_id!=='crossmap'||!t.source_role_qualification)return prsiceVersion(t);const q=t.source_role_qualification;return '<br><span class=\"crossmap-version\" style=\"overflow-wrap:anywhere\">Role: '+esc(q.role_kind)+'; Reported edition: '+esc(q.reported_version)+'; '+esc(q.remaining_scope)+'</span>';}\n"
            page = page.replace("${prsiceVersion(t)}", "${crossmapVersion(t)}", 1).replace("function renderContent(){", helper + "function renderContent(){", 1)
        body = re.search(r"<body[^>]*>", page, re.I)
        require(body is not None, "Missing report body")
        marker = page.find("<site-header ", body.end())
        require(marker >= body.end() and page.count("<site-header ") == 1, "Changed historical notice boundary")
        history = '<details id="crossmap-prior-catalog"><summary>Before CrossMap admission (historical)</summary>' + page[body.end():marker] + '</details>'
        after[name] = (page[:body.end()] + NOTICE + history + page[marker:]).encode()
    prefix = ("# Current CrossMap catalog checkpoint\n\n" + CURRENT + "\n\n" + SCOPE + "\n\n" + NOTE
              + "\n\n**Warnings and required actions:** " + ACTION + "\n\nSee [workflow and source-role guide](../CROSSMAP_WORKFLOWS.md).\n\n---\n\nEarlier checkpoint text is historical.\n\n").encode()
    for name in META[3:5]:
        after[name] = prefix + before[name]
    return after


def check(before, after, roles, qualification):
    require(after == transition(before, roles, qualification), "Changed deterministic source-bound transition")
    old, new = json.loads(before[META[0]]), json.loads(after[META[0]])
    for a, b in zip(old, new, strict=True):
        require({k: v for k, v in a.items() if k != "tools"} == {k: v for k, v in b.items() if k != "tools"}, "Changed paper identity")
        require([u for u in a["tools"] if u["tool_id"] != "crossmap"] == [u for u in b["tools"] if u["tool_id"] != "crossmap"], "Changed unrelated observation")
        for x in a["tools"]:
            if x["tool_id"] == "crossmap":
                y = next(u for u in b["tools"] if u["tool_id"] == "crossmap")
                for key in next(r for r in roles["roles"] if r["paper_id"] == a["paper_id"])["raw_source_fields"]:
                    require(x.get(key) == y.get(key), "Changed raw source/version field: " + key)
    a, b = json.loads(before[META[1]]), json.loads(after[META[1]])
    require([r for r in a if r["id"] != "crossmap"] == [r for r in b if r["id"] != "crossmap"], "Changed unrelated inventory")
    oldledger, newledger = json.loads(before[META[5]]), json.loads(after[META[5]])
    require([r for r in oldledger["reviews"] if r["tool_id"] != "crossmap"] == [r for r in newledger["reviews"] if r["tool_id"] != "crossmap"], "Changed unrelated prior reviews")
    old242 = next(r for r in oldledger["reviews"] if r["tool_id"] == "crossmap")
    new242 = next(r for r in newledger["reviews"] if r["tool_id"] == "crossmap" and r["pmcid"] == old242["pmcid"])
    require(new242["prior_review"] == old242, "Lost exact prior P242 review")
    require(len(newledger["reviews"]) == len({(r["pmcid"], r["tool_id"]) for r in newledger["reviews"]}) == 1772, "Wrong unique review count")
    markers = {REVIEWED, "individually-reviewed-primary-source-roles; whole-paper-method-review-open"}
    require({(r["pmcid"], u["tool_id"]) for r in new for u in r["tools"] if u["review_status"] in markers}
            == {(r["pmcid"], r["tool_id"]) for r in newledger["reviews"]}, "Review ledger/observation mismatch")
    counts = Counter(r["implementation_status"] for r in b)
    require((counts["native-subset-validated"], counts["upstream-adapter-validated"], counts["method-overlap"], counts["missing"]) == (30, 132, 16, 253), "Wrong family counts")
    return dict(papers=250, labels=446, provider_definitions=485, observations=2724, reviewed_uses=1772,
                pending_uses=952, source_roles_reviewed=4, new_reviews=3, refined_existing_reviews=1,
                unchanged_prior_reviews=1768, unchanged_unrelated_observations=2720, unchanged_unrelated_inventory_rows=445,
                scoped_families=162, native=30, upstream=132, including_existing_adapters=165, method_overlap=16, missing=253,
                new_native_family_credit=0, new_upstream_family_credit=1, added_positive_observations=0,
                removed_positive_observations=0, author_unreported_editions_inferred=False,
                original_paper_analyses_reexecuted=False, whole_paper_reproduced=False, whole_goal_complete=False)
