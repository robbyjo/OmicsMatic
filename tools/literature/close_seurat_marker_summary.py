# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze a native marker-summary foundation without granting whole-tool credit."""
import json,xml.etree.ElementTree as ET,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,unpack
BASE=ROOT/'build/literature-review/signac-runtime'
def read(p):return json.loads(p.read_bytes())

def main():
    folder=ROOT/'docs/literature/seurat-marker-summary-development';assert not folder.exists();folder.mkdir()
    matrix=read(BASE/'differential-summary-native-matrix-1/COMPLETED.json');assert matrix['tests_per_JVM']==165 and len(matrix['JVMs'])==3 and matrix['source_summary_cases']==162 and matrix['source_postprocessing_matches']==156 and matrix['explicit_missing_identity_refusals']==6 and matrix['source_rounding_values']==2638 and not matrix['changed_parent_entries']
    assert all(pin(ROOT/n)==v for n,v in matrix['source_pins'].items());junit=ET.fromstring((ROOT/'build/test-results/test/TEST-org.jlinalg.cli.SeuratMarkerSummaryTest.xml').read_bytes());assert all(int(junit.get(k))==v for k,v in dict(tests=165,skipped=0,failures=0,errors=0).items())
    capture=read(ROOT/'src/test/resources/org/jlinalg/sequence/seurat-marker-summary/CAPTURE.json');assert pin(ROOT/'src/test/resources/org/jlinalg/sequence/seurat-marker-summary/original-controls.zip')==capture['archive'] and all(pin(ROOT/n)==v for n,v in capture['source_pins'].items())
    probe=read(BASE/'differential-runtime-probe-2/probe/RESULT.json');assert len(probe['source_functions'])==42 and all(all(v.values()) for v in probe['source_functions'].values()) and not probe['presto_present'] and probe['versions']['limma']=='UNAVAILABLE'
    reference=read(BASE/'differential-original-reference-3/original/RESULT.json');assert len(reference['cases'])==6 and all(c['status']=='original-completed' for c in reference['cases'].values());assert [c['rows'] for c in reference['cases'].values()]==[100,100,100,120,141,142] and [len(c['warnings']) for c in reference['cases'].values()]==[0,12,26,0,0,16]
    assert read(BASE/'differential-original-reference-1/PROCESS.json')['exit_code']==1 and read(BASE/'differential-original-reference-2/SUPERSEDED.json')['status']=='superseded-reference-transport-not-source-statistical-failure' and read(BASE/'differential-original-transport-probe-1/PROCESS.json')['exit_code']==0
    assert (BASE/'differential-original-transport-probe-1/probe/encoded-values.txt').read_text().splitlines()[1:5]==['NaN','NA','Inf','-Inf']
    for name in ['differential-runtime-probe-2','differential-summary-reference-1','differential-original-reference-1','differential-original-reference-2','differential-original-reference-3']:
        before=read(BASE/name/'BASES-BEFORE.json');assert before==read(BASE/name/'BASES-AFTER.json') and sum(len(v) for v in before.values())==27958
        for root,items in before.items():
            for n,v in items.items():assert pin(Path(root)/n)==v,'Existing R-library file changed'
    assert read(BASE/'differential-summary-example-1/COMPLETED.json')['JVMs']==3 and read(BASE/'differential-summary-website-1/PROCESS.json')['exit_code']==0 and b'BUILD SUCCESSFUL' in (BASE/'differential-summary-website-1/stdout.log').read_bytes()
    catalog=ROOT/'docs/literature/omics-2023-2026';summary=read(catalog/'summary.json');assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],summary['implementation_status_counts']['native-subset-validated'],summary['implementation_status_counts']['upstream-adapter-validated'],summary['implementation_status_counts']['missing'])==(250,387,2444,16,54,258)
    inventory=read(catalog/'tool-inventory.json');assert next(t for t in inventory if t['id']=='signac')['implementation_status']=='missing' and next(t for t in inventory if t['id']=='seurat')['implementation_status']=='method-overlap'
    names=['src/main/java/org/jlinalg/sequence/SeuratMarkerSummary.java','src/test/java/org/jlinalg/cli/SeuratMarkerSummaryTest.java','docs/literature/SEURAT_MARKER_SUMMARY_DEVELOPMENT.md','examples/seurat-marker-summary/MarkerSummaryExample.java','examples/seurat-marker-summary/README.md']+['tools/literature/'+n for n in ['close_seurat_marker_summary.py','verify_seurat_marker_summary.py','validate_seurat_marker_summary.py','seurat-marker-summary-development.init.gradle','collect_seurat_marker_summaries.R','prepare_seurat_marker_summary_cases.py','freeze_seurat_marker_summary_controls.py','probe_signac_differential_runtime.R','collect_seurat_differential_reference.R','validate_seurat_differential_transport.R']]
    names+=[p.relative_to(ROOT).as_posix() for p in (ROOT/'src/test/resources/org/jlinalg/sequence/seurat-marker-summary').iterdir() if p.is_file()];sources={n:pin(ROOT/n) for n in names};files={}
    def add(p,name=None):
        assert not p.is_symlink();name=name or p.relative_to(ROOT).as_posix();raw=p.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for n in sources:add(ROOT/n,'current-source/'+n)
    for sub in ['differential-source-intake-1','differential-historical-source-intake-1','differential-runtime-probe-1','differential-runtime-probe-2','differential-summary-cases-1','differential-summary-reference-1','differential-summary-native-matrix-1','differential-summary-example-1','differential-original-reference-1','differential-original-reference-2','differential-original-reference-3','differential-original-transport-probe-1','differential-summary-website-1']:
        for p in (BASE/sub).rglob('*'):
            if p.is_file():add(p)
    add(ROOT/'build/test-results/test/TEST-org.jlinalg.cli.SeuratMarkerSummaryTest.xml','normal-Gradle-tests.xml')
    with zipfile.ZipFile(ROOT/matrix['artifact']['path']) as z:
        for name in matrix['new_class_entries']:files['actual-new-classes/'+name]=z.read(name)
    warning='''# DEVELOPMENT: MARKER SUMMARIES ONLY; NO WHOLE-TOOL CREDIT

No Wilcoxon/logistic test or biological significance is produced by the native API. Supplied probabilities are inputs. Summary/correction/ordering controls use a declared fixed-p callback and do not validate statistical inference. Current original statistical controls are separate expected references, not a public Java adapter.

Select explicit source edition, summary/testing slots, normalization provenance, groups and pseudocount. Historical whole packages are not installed. Preserve derived NaN/Inf and review warnings. Native only.pos refuses six source cases producing missing-identity R placeholder rows; do not repair/drop/rename them. Malformed identities/groups/settings, exhausted capacity and cancellation raise exceptions and admit no statistical result.

The corrected original-reference3 exporter retains 17-digit finite values, distinct NA/NaN/Inf and original RDS objects. Reference2's transport is superseded; all earlier attempts are retained. Actual LR controls retain convergence/fitted-probability warnings. The source-selected base-R backend intentionally has no Presto/limma; optional installation would change the profile and requires validation. No packages were installed or existing library files changed. Future unavailable/forbidden upstream requirements must leave dependent analysis SKIP3.

Read SEURAT_MARKER_SUMMARY_DEVELOPMENT.md and examples/seurat-marker-summary/README.md. Signac remains missing; Seurat remains method-overlap. The full250-paper goal remains active with70new scoped integrations,73including existing adapters and258missing. No full-paper/biological/historical-environment validation, release, push or deployment is claimed. Cell Ranger remains excluded.
'''
    (folder/'WARNINGS.md').write_text(warning,encoding='utf8',newline='\n');layout=pack(folder/'controls.zip',files)
    result=dict(schema_version=1,status='passed-native-six-edition-marker-summary-foundation-not-Signac-or-Seurat-whole-tool',source_pins=sources,archive=pin(folder/'controls.zip'),warnings_document=pin(folder/'WARNINGS.md'),**layout,artifact=matrix['artifact'],canonical_executable_preserved=matrix['canonical_executable_preserved'],normal_Gradle_tests=165,actual_JAR_tests_per_JVM=165,JVMs=matrix['JVMs'],new_class_entries=matrix['new_class_entries'],changed_parent_entries=matrix['changed_parent_entries'],source_summary_comparisons=162,controlled_p_callback_postprocessing_matches=156,explicit_missing_identity_refusals=6,exact_detection_rounding_values=2638,source_profiles=['4.1.0','4.3.0','5.0.0','5.1.0','5.2.1','5.6.0'],current_installed_source_function_body_and_formals_matches=42,source_statistical_reference_requests=6,source_statistical_reference_rows=[100,100,100,120,141,142],source_LR_warning_counts=[12,26,16],prior_R_library_files_unchanged=27958,synthetic_example_JVMs=3,website_check_passed=True,retained_failed_and_superseded_attempts=True,distinct_original_nonfinite_transport_probe_passed=True,catalog_counts=dict(papers=250,tools=387,observations=2444,new_scoped_integrations=70,including_existing=73,missing=258),catalog_snapshot_pins={n:pin(catalog/n) for n in ['summary.json','tool-inventory.json','papers.json','SHA256SUMS']},Signac_catalog_status='missing',Seurat_catalog_status='method-overlap',native_hypothesis_test_implementation=False,public_original_runtime_request_interface=False,root_dispatch_added=False,catalog_implementation_credit=False,historical_whole_packages_executed=False,Linux_R_fitting_executed=False,installed_DLL_source_authentication=False,biological_accuracy_certified=False,full_paper_reproduction=False,full_project_test_suite_run=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==result['canonical_executable_preserved'] and all(pin(ROOT/n)==v for n,v in sources.items());save(folder/'VALIDATION.json',result);unpack(folder/'controls.zip',result);print(json.dumps(dict(status=result['status'],sources=len(sources),archive=result['archive'],logical_files=layout['logical_files'],catalog_implementation_credit=False)))

if __name__=='__main__':main()
