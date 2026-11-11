# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal native interval controls, source provenance and retained failed attempts without HTSeq credit."""
import json
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from validate_htseq_interval_native import BASE,JAR


def main():
    folder=ROOT/'docs/literature/htseq-interval-development'
    assert not folder.exists()
    native=json.loads((BASE/'interval-native-matrix-1/COMPLETED.json').read_bytes())
    example=json.loads((BASE/'interval-example-1/COMPLETED.json').read_bytes())
    artifact=pin(JAR)
    assert native['artifact']==example['artifact']==artifact
    assert native['tests_per_JVM']==2224 and len(native['JVMs'])==example['JVMs']==3
    assert native['all_existing_parent_entries_preserved'] and not native['catalog_implementation_credit']
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    assert native['canonical_executable_preserved']==canonical
    sources=[ROOT/'src/main/java/org/jlinalg/sequence/HtseqFeatureAssignment.java',
        ROOT/'src/test/java/org/jlinalg/cli/HtseqFeatureAssignmentTest.java',ROOT/'docs/literature/HTSEQ_INTERVALS_DEVELOPMENT.md']
    for folder_name in ['src/main/resources/org/jlinalg/omics/htseq-intervals',
        'src/test/resources/org/jlinalg/sequence/htseq-intervals','examples/htseq-intervals']:
        sources += [p for p in (ROOT/folder_name).rglob('*') if p.is_file()]
    for name in ['audit_htseq_paper_uses.py','intake_htseq_sources.py','setup_htseq_reference.py',
        'recover_htseq_reference_inventory.py','collect_htseq_interval_controls.py','prepare_htseq_interval_fixture.py',
        'intake_htseq_author_code.py','prepare_htseq_use_reviews.py','htseq-use-reviews.json','htseq-source-additions.json',
        'htseq-core-development.init.gradle','validate_htseq_interval_native.py','validate_htseq_interval_example.py',
        'close_htseq_interval_development.py','verify_htseq_interval_development.py']:
        sources.append(ROOT/'tools/literature'/name)
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
    evidence={'current-source/'+name:(ROOT/name).read_bytes() for name in pins}
    for name in ['interval-original-1','interval-original-2','interval-original-3','interval-native-matrix-1',
        'interval-example-1','reference-inventory-recovery-1','reference-inventory-recovery-2','author-code-intake-1','interval-evidence-attempt-1']:
        for path in (BASE/name).rglob('*'):
            if path.is_file():evidence[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    # Keep setup metadata and failed attempted code, not the installed environment
    # or wheel payloads. Installed/reference dependencies are not bundled runtimes.
    for path in (BASE/'reference-setup-1').iterdir():
        if path.is_file():evidence[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['source-intake-1','source-intake-2']:
        intake=BASE/name
        for path in intake.rglob('*'):
            if path.is_file() and 'source' not in path.relative_to(intake).parts:
                evidence[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    path=BASE/'core-gradle-tests-1.log'
    evidence[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    test=ROOT/'build/test-results/test/TEST-org.jlinalg.cli.HtseqFeatureAssignmentTest.xml'
    evidence['normal-Gradle-tests.xml']=test.read_bytes()
    audit=json.loads((BASE/'primary-use-audit-1/PRIMARY-USES.json').read_bytes())
    sanitized=dict(status=audit['status'],papers_scanned=250,source_papers=8,catalogued_papers=7,
        census=audit['census'],records=[dict(paper_id=r['paper_id'],doi=r['doi'],pmcid=r['pmcid'],xml_sha256=r['xml_sha256'],
            primary_pointers=[dict(paragraph_index=p['paragraph_index'],section=p['section'],paragraph_sha256=p['sha256']) for p in r['matches']]) for r in audit['records']])
    evidence['primary-source-census.json']=(json.dumps(sanitized,indent=2)+'\n').encode('utf8')
    entries=json.loads((BASE/'interval-native-matrix-1/parent-entries.json').read_bytes())
    with zipfile.ZipFile(JAR) as archive:
        for name in entries['added']:
            if name.endswith('/'):
                assert archive.getinfo(name).is_dir() and archive.read(name)==b''
            else:evidence['candidate-owned-entries/'+name]=archive.read(name)
    summary=json.loads((ROOT/'docs/literature/omics-2023-2026/summary.json').read_bytes())
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],summary['implementation_status_counts']['missing'])==(250,387,2445,257)
    catalog={line.split('  ',1)[1]:line.split('  ',1)[0] for line in (ROOT/'docs/literature/omics-2023-2026/SHA256SUMS').read_text().splitlines()}
    assert len(catalog)==422 and all(pin(ROOT/'docs/literature/omics-2023-2026'/n)['sha256']==h for n,h in catalog.items())
    parent=ROOT/'docs/literature/signac-tools-development'
    parent_record=json.loads((parent/'VALIDATION.json').read_bytes())
    assert pin(parent/'controls.zip')==parent_record['archive']
    folder.mkdir()
    warning='''# NATIVE HTSEQ INTERVAL DEVELOPMENT: WARNINGS AND REQUIRED ACTION

This is an immutable Java interval-set assignment core and checked example. It does not expose an HTSeq root counting command or read SAM/GTF. Native execution needs Java17+ only, without Python or installation. Use the separate htseq-core development JAR and examples/htseq-intervals; older canonical/released JARs lack the API.

Require explicit nonnegative, zero-based half-open interval coordinates and exact chromosome/feature identities. A stranded index requires + or -; an unstranded index reports . and combines strands. Original zero-length interval insertion/query is refused. Negative positions, invalid identities/strands, exceeded logical budgets or cancellation admit no assignment, including partial steps. Required action: retain original inputs and the exception, correct the named cause and retry. Never silently discard invalid blocks or relax a scientific threshold. Callers retain failed attempts; this library API does not create a run/status directory. Logical capacity guards are not JVM-memory guarantees.

NO_FEATURE/UNIQUE/AMBIGUOUS describe the assigned set, not a biological finding or a counting policy. Callers must supply CIGAR blocks, pairing/read-versus-fragment units, strand transforms, flags, MAPQ/NH filters, secondary/supplementary policies, multi-overlap weights and cell/UMI logic. The API does not infer these choices, normalize expression or run differential inference. The original htseq-count SAM/GTF command was not executed in this milestone. Mode references resolve the documented definitions over actual original GenomicArrayOfSets geometry.

2224 actual-JAR tests pass without skips on Windows Java21/25 and Linux Java21. Controls retain43 populations,538 successful queries,1214 original spans,1614 mode resolutions,44 query refusals and15 insertion refusals.337 candidate queries following failed feature insertion were not executed and are not counted as passing. The checked example passes on each JVM with six assignments and two explicit refusals. Every pre-existing parent JAR entry and the canonical executable remain preserved. Historical whole packages and biological/paper equivalence are not certified.

Retained failures include the initial Windows inventory of Linux links; recovery1 assumed every link targeted a file and failed on lib64; recovery2 reads file/directory links on Linux, reusing the successfully installed/probed reference without reinstalling. Collector1 was dependency-gated before execution. Collector2 encountered the genuine original zero-length query refusal. Collector3 retains all complete outcomes/refusals. Source bodies, logs, metadata and original controls remain preserved; failed attempts are not reported as successful controls.

The first evidence-packaging attempt rejected a trailing-slash JAR directory name. The corrected pack retains the complete entry inventory and stores only file payloads; empty directory entries are verified separately. The incomplete attempt and its freezer source remain retained. Scientific code and reference results were unchanged.

The original reference is private Linux HTSeq2.1.2/Python3.12.3/NumPy2.2.6/pysam0.23.3. Shared Python was not modified. Five exact HTSeq source archives are retained with publisher SHA256 checks; archive availability does not establish installed historical runtimes. Source/API evidence is GPL3 and retained with its license. Integrity hashes are not signatures or execution authentication. DLL/runtime authenticity and the full mutable HTSeq API are not certified.

Eight source-specific role drafts include P005's HT-seq0.6.0 alias. Full curation/public analysis admission is pending. P099 uses custom binary peak/read-presence matrices; RNA scripts in the inspected EasySci references do not establish its ATAC implementation. P121 requires strict MAPQ>20/properly paired reads but does not resolve the counting unit. P195 states0.13 without an exact patch. Annotation, genome, effective flags, preparation and downstream models must stay explicit. Cell Ranger remains excluded.

For future upstream-dependent workflows, missing/forbidden runtime requirements must produce SKIP3 and skip affected analysis. Where permitted, provide the exact declared runtime and retry into NEW paths; otherwise retain the skip. Input/integrity/process/capacity failures must admit no partial results. No silent release substitution or source translation is allowed. Read ../HTSEQ_INTERVALS_DEVELOPMENT.md for the checked API example, limits and remaining work.

This checkpoint leaves the catalog at71new scoped integrations/74including existing adapters/257missing. It adds no HTSeq tool credit and does not release, push, deploy, run the full project suite, reproduce a full paper or complete the goal.
'''
    (folder/'WARNINGS.md').write_text(warning,encoding='utf8',newline='\n')
    layout=pack(folder/'controls.zip',evidence)
    proof=dict(schema_version=1,status='passed-native-HTSeq-interval-assignment-foundation',
        artifact=dict(path=JAR.relative_to(ROOT).as_posix(),**artifact),source_pins=pins,
        archive=pin(folder/'controls.zip'),warnings_document=pin(folder/'WARNINGS.md'),**layout,
        parent_public_Signac_validation=pin(parent/'VALIDATION.json'),parent_public_Signac_archive=parent_record['archive'],
        parent_artifact=entries['parent'],unchanged_existing_entries=entries['unchanged_existing_entries'],
        all_parent_entries_preserved=True,canonical_executable_preserved=canonical,
        tests_per_JVM=2224,JVMs=3,original_step_populations=43,original_successful_queries=538,
        original_step_spans=1214,documented_mode_resolutions=1614,original_query_refusals=44,
        original_insertion_refusals=15,candidate_queries_unexecuted_after_insertion_failure=337,
        checked_example_JVMs=3,example_assignments_per_JVM=6,example_refusals_per_JVM=2,
        paper_sources_audited=250,HTSeq_software_sources=8,new_HTSeq_alias_pending_curation='P005',
        catalog_checksums_preserved=catalog,catalog_counts=dict(new_scoped_integrations=71,including_existing=74,missing=257),
        Cell_Ranger_excluded=True,original_htseq_count_SAM_GTF_run=False,HTSeq_root_dispatch_available=False,
        full_mutable_HTSeq_API=False,historical_whole_packages=False,execution_authentication=False,
        biological_validity_certified=False,whole_paper_reproduced=False,full_project_test_suite_run=False,
        catalog_implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert all(pin(ROOT/name)==value for name,value in pins.items())
    save(folder/'VALIDATION.json',proof)
    (folder/'SHA256SUMS').write_text(''.join(pin(folder/name)['sha256']+'  '+name+'\n' for name in ['VALIDATION.json','controls.zip','WARNINGS.md']),encoding='ascii',newline='\n')
    print(json.dumps(dict(status=proof['status'],archive=proof['archive'],logical_files=proof['logical_files'],source_pins=len(pins))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native freeze checks')
    main()
