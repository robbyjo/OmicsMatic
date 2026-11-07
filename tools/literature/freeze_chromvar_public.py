# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the normal main executable, public workflows and paper-edition correspondence before catalog promotion."""
import json,zipfile
from pathlib import Path
from validate_mashr_native import pin
from signac_scan_evidence import pack
from audit_chromvar_shared_build import CHANGED
from catalog_source_integrity import reverse_delta
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime';TARGET=ROOT/'docs/literature/chromvar-tools-development'
def read(p):return json.loads(p.read_bytes())
def main():
    assert not TARGET.exists();paths=['chromvar-public-workflow-1/BUILD.json','chromvar-public-controls-1/COMPLETED.json','chromvar-public-example-1/COMPLETED.json','chromvar-paper-source-controls-1/COMPLETED.json'];build,public,example,history=records=[read(BASE/p) for p in paths];parent_path=ROOT/'docs/literature/chromvar-background-development/VALIDATION.json';parent=read(parent_path);assert all(pin(ROOT/n)==v for r in records+[parent] for n,v in r['source_pins'].items()) and pin(parent_path.parent/'controls.zip')==parent['archive']
    assert public['artifact']==example['artifact']==build['artifact'] and (public['actual_original_BACKGROUND_fits'],public['actual_native_DEVIATIONS'],public['actual_main_captured_verifications'],public['actual_dependency_skip'],public['failed_capture_rejections'])==(2,3,27,1,3) and public['existing_output_preserved'] and public['malformed_requests_rejected']==2
    assert (example['literal_original_BACKGROUND'],example['literal_native_DEVIATIONS'],example['literal_VERIFY_requests'],example['actual_main_verifications'],example['existing_output_refusals'])==(1,1,2,6,2) and example['inputs_and_requests_unchanged']
    assert (history['numeric_method_comparisons'],history['source_profile_case_protocols'],history['background_source_and_public_protocols'])==(306,102,45) and history['all_numeric_fields_value_kinds_and_background_RNG_states_match'] and history['paper_1_5_0_commit_unknown']
    artifact=ROOT/build['artifact']['path'];canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';assert pin(artifact)=={k:build['artifact'][k] for k in ['bytes','sha256']} and pin(canonical)==build['canonical_executable_preserved'];changed=[]
    with zipfile.ZipFile(ROOT/parent['artifact']['path']) as p,zipfile.ZipFile(canonical) as c,zipfile.ZipFile(artifact) as n:
        for name in p.namelist():
            if name.endswith('/') or name=='org/jlinalg/cli/JLinAlgCli.class':continue
            if p.read(name)!=n.read(name):assert name in CHANGED and n.read(name)==c.read(name);changed.append(name)
        assert set(changed)==CHANGED and n.testzip() is None
    data=ROOT/'docs/literature/omics-2023-2026';bridge=read(data/'chromvar-dispatch-shared-source-compatibility.json')['paths'][0];assert len(bridge['edits'])==2 and reverse_delta((ROOT/bridge['path']).read_bytes(),bridge)==(BASE/'chromvar-public-workflow-1/JLinAlgCli.before.java').read_bytes()
    draft_path=ROOT/'tools/literature/chromvar-use-reviews.json';draft=read(draft_path);assert {r['paper_id'] for r in draft}=={'P039','P051','P092','P099','P225'} and len(read(ROOT/'tools/literature/reviewed-uses.json')['reviews'])==827
    sources={}
    for r in records:
        for n,v in r['source_pins'].items():assert n not in sources or sources[n]==v;sources[n]=v
    for name in ['tools/literature/freeze_chromvar_public.py','tools/literature/verify_chromvar_public.py','tools/literature/audit_chromvar_roles.py','tools/literature/prepare_chromvar_use_reviews.py','tools/literature/chromvar-use-reviews.json']:sources[name]=pin(ROOT/name)
    files={}
    def add(p,name=None):
        assert not p.is_symlink();name=name or p.relative_to(ROOT).as_posix();raw=p.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for n in sources:add(ROOT/n,'current-source/'+n)
    for folder in ['chromvar-public-workflow-1','chromvar-public-controls-1','chromvar-public-example-1','chromvar-paper-source-controls-1']:
        for p in (BASE/folder).rglob('*'):
            if p.is_file() and p.suffix not in {'.jar','.class','.dll','.so'} and 'javadoc' not in p.parts:add(p)
    for run in example['runs']:
        for p in (ROOT/run['analysis_path']).rglob('*'):
            if p.is_file():add(p)
    intake=BASE/'chromvar-historical-source-intake-1'
    for name in ['DOWNLOADS.json','PREPUBLICATION.json','PREPUBLICATION-COMPARISON.json','COMPARISON.json']:add(intake/name)
    for edition in ['source-1.28.0','source-1.5.0-prepublication']:
        for name in ['DESCRIPTION','LICENSE','R/compute_deviations.R','R/background_peaks.R','src/utils.cpp']:add(intake/edition/name)
    audit=read(BASE/'chromvar-primary-role-audit-1/PRIMARY-USES.json');assert audit['papers_scanned']==250 and audit['catalogued_papers']==audit['mentioned_papers']==5
    pointers=dict(papers_scanned=250,catalogued_papers=5,mentioned_papers=5,census=audit['census'],records=[dict(paper_id=r['paper_id'],pmcid=r['pmcid'],doi=r['doi'],xml_sha256=r['xml_sha256'],catalogued=r['catalogued'],matching_paragraphs=[{k:m[k] for k in ['paragraph_index','section','sha256']} for m in r['matches']]) for r in audit['records']],long_primary_text_published=False);files['primary-use-pointers.json']=(json.dumps(pointers,indent=2)+'\n').encode()
    for name in ['VALIDATION.json','controls.zip','WARNINGS.md']:add(parent_path.parent/name,'parent-background/'+name)
    files['before-catalog-summary.json']=(data/'summary.json').read_bytes();files['before-tool-inventory.json']=(data/'tool-inventory.json').read_bytes()
    TARGET.mkdir();warnings='''# WARNINGS / REQUIRED ACTIONS — PUBLIC CHROMVAR

CURRENT_SOURCE_ONLY — Use chromvar-tools --request UTF8_JSON from the exact development executable or a current source build. Older canonical/released JARs lack this route. The separate canonical JAR was preserved. Full normal-project packaging retains the validated chromVAR entries and carries twelve existing study-class changes exactly from that canonical build; this does not certify every study workflow or the entire project test suite.

SKIP3 — BACKGROUND requires R4.6.1, chromVAR1.34.1, Matrix1.7.5, nabor0.5.0, Rcpp1.1.2, RcppArmadillo15.6.2-1, jsonlite2.0.0 and dependencies in declared permitted private paths. Supply the four runtime fields; no automatic installation occurs. If unavailable or installation is forbidden, keep dependent analysis skipped. Native DEVIATIONS/VERIFY need Java and a valid prepared background, not a substitute toy matrix.

FAIL2 — Preserve attempts and partials; inspect WARNINGS, status, manifests, native diagnostics and worker logs. Correct named input/reference/bias/covariance/source/capacity/process errors and retry into NEW paths. Existing outputs are refused; failed/skipped captures admit no result. Timeout cleanup is tested in the unchanged parent background workflow.

VERIFY_LIMIT — Verification audits native preparation, source, protocol and captured consistency; it does not rerun the R sampler or independently generate RNG. A retained adversarial parent fixture demonstrates acceptance of coordinated fabricated worker/published indices after rehashing. Hashes establish integrity, not authenticity or proof of execution. Never claim independent sampler or biological validation from VERIFY.

SOURCE_VERSIONS — The four retained1.28.0 and pre-publication upstream1.5.0 R functions match the validated functions;306 numerical method comparisons and45 source/public background protocols agree on recorded current dependencies. The1.28.0 C++ source is byte-identical; the1.5.0 candidate differs only by one missing final LF, with raw source preserved. P225 does not identify its checkout: commit3236dcc515efa01ce54114aee03b227466168126 is a reference available before publication, not proof of the exact paper commit. Historical whole packages and DLL source authentication remain unproven. Supported execution profile keys remain1.16.0-source/1.34.1-package; record the reported paper edition and chosen current execution explicitly.

PAPER_BOUNDARIES — Five primary uses are retained; P099 explicitly reports1.16.0 despite the initial extractor missing it. P092 is an ArchR addDeviationsMatrix/addBgdPeaks workflow and is not certified by this direct R/native integration. Signac/Seurat wrappers, motif databases/scanning, subsampling, rescale0–10, aggregation, gene-expression/spatial correlation, regulator filtering, BSgenome containers and complete paper pipelines remain separate. Native source NA/NaN/Inf and source failures are not repaired. Cell Ranger remains excluded.

PUBLICATION — This local public-root milestone and complete synthetic example do not alone grant catalog credit, a release, push, deployment, whole-paper reproduction or biological validity. The full250-paper implementation goal remains active. Long primary-text passages remain in ignored research cache; public evidence contains paraphrases, hashes and locators.
''';(TARGET/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n');(TARGET/'.gitattributes').write_text('*.json -text\n*.zip -text\n*.md -text\n',encoding='utf8',newline='\n');archive=TARGET/'controls.zip';layout=pack(archive,files);assert archive.stat().st_size<95000000
    record=dict(schema_version=1,status='passed-public-chromVAR-main-workflows-and-paper-source-correspondence',source_pins=sources,artifact=build['artifact'],parent_scientific_artifact=parent['artifact'],archive=pin(archive),**layout,parent_background_validation=pin(parent_path),parent_background_archive=parent['archive'],chromVAR_and_non_study_parent_file_entries_unchanged=True,carried_existing_canonical_study_class_changes=sorted(changed),dispatcher_only_exact_source_edits=2,normal_Gradle_executableJar=True,root_dispatch_available=True,original_public_main_BACKGROUND_fits=2,native_public_main_DEVIATIONS=3,actual_main_capture_verifications=27,source_report_and_help_protocols=9,actual_main_dependency_skip=1,actual_main_failed_capture_rejections=3,actual_main_existing_output_preserved=True,malformed_public_requests_rejected=2,literal_public_BACKGROUND=1,literal_public_DEVIATIONS=1,literal_public_VERIFY_requests=2,literal_public_capture_verifications=6,literal_public_existing_output_refusals=2,paper_source_numeric_method_comparisons=306,paper_source_background_protocols=45,paper_source_case_profile_protocols=102,source_profiles=['1.28.0-source','1.5.0-source'],paper_1_5_0_commit_unknown=True,prepublication_reference_commit='3236dcc515efa01ce54114aee03b227466168126',reviewed_primary_roles_drafted=5,additional_supporting_pointers=2,P099_explicit_1_16_0_draft_correction=True,long_primary_text_published=False,canonical_executable_preserved=build['canonical_executable_preserved'],historical_whole_packages_executed=False,independent_native_RNG_replay=False,installed_DLL_source_authentication=False,ArchR_pipeline_available=False,full_Signac_workflow_available=False,catalog_implementation_credit=False,complete_project_test_suite_run=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,warnings_document=pin(TARGET/'WARNINGS.md'))
    (TARGET/'VALIDATION.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8',newline='\n');assert all(pin(ROOT/n)==v for n,v in sources.items());print(json.dumps(dict(status=record['status'],archive=record['archive'],physical_members=layout['archive_members'],logical_files=layout['logical_files'],source_pins=len(sources))),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full public milestone gates')
    main()
