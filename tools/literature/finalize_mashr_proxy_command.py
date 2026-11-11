#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the extended public proxy command and original/portable evidence."""
import json,sys,zipfile
from datetime import datetime,timezone
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import census,pin,write
ROOT=Path(__file__).resolve().parents[2]
RUN=ROOT/'build/literature-review/mashr-runtime/proxy-command-development-1'
DEST=ROOT/'docs/literature/mashr-proxy-command'
CHANGED_SOURCE='src/main/java/org/jlinalg/cli/MashSummaryCli.java'
SOURCES=[CHANGED_SOURCE,'src/test/java/org/jlinalg/cli/MashProxySummaryCliTest.java',
 'tools/literature/validate_mashr_proxy_command.py','tools/literature/validate_mashr_proxy_protocols.py',
 'tools/literature/mashr-proxy-command-controls.R','tools/literature/finalize_mashr_proxy_command.py',
 'tools/literature/validate_mashr_proxy_checkpoint.py','docs/literature/MASHR_PROXY_COMMAND.md']
CLAIMS=dict(standalone_public_proxy_command_available=True,explicit_independent_marginal_proxy_assumption=True,
           complete_native_source_replayed=True,source_score_separate_from_stable_log=True,
           unmodified_original_function_executed=True,prior_v1_summary_bundles_verified=True,
           full_dreamlet_package_validated=False,paper_binary_edition_identified=False,
           joint_posterior_probability_computed=False,root_dispatcher_changed=False,
           study_route_available=False,catalog_promoted=False,whole_paper_reproduced=False,
           biological_accuracy_certified=False,released_or_deployed=False)


def main():
    assert __debug__, 'Do not disable evidence assertions'
    assert not DEST.exists(), 'Never overwrite a checkpoint'
    records={n:json.loads((RUN/n/'COMPLETED.json').read_text(encoding='utf-8')) for n in ['matrix-3','protocols-1','repository-example-1']}
    matrix,protocols,example=records.values()
    assert matrix['status']=='passed-three-JVM-native-proxy-summary-command-matrix' and matrix['tests_per_JVM']==477 and matrix['new_command_tests']==37 and len(matrix['runs'])==3
    assert protocols['status']=='passed-public-proxy-original-and-portability-protocols' and protocols['original_R_score_decisions']==360 and len(protocols['cross_produced_bundles'])==6 and all(len(r['commands'])==10 for r in protocols['runs'])
    assert example['status']=='passed-repository-synthetic-proxy-example' and len(example['commands'])==4 and all(c['exit_code']==0 for c in example['commands'])
    assert all(pin(ROOT/n)==v for record in records.values() for n,v in record['source_pins'].items())
    assert matrix['artifact']==protocols['artifact'] and pin(ROOT/matrix['artifact']['path'])=={k:matrix['artifact'][k] for k in ['bytes','sha256']}
    prior_path=ROOT/'docs/literature/mashr-composite/VALIDATION.json';prior=json.loads(prior_path.read_text(encoding='utf-8'))
    old_sources={**prior['protected_prior_source_pins'],**prior['source_pins']};protected={n:v for n,v in old_sources.items() if n!=CHANGED_SOURCE}
    assert all(pin(ROOT/n)==v for n,v in protected.items())
    change=matrix['changed_prior_source'];assert change['path']==CHANGED_SOURCE and change['before']==old_sources[CHANGED_SOURCE] and pin(ROOT/change['snapshot'])==change['before'] and pin(ROOT/CHANGED_SOURCE)==change['after']
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==matrix['canonical_executable_unchanged']==prior['canonical_executable_unchanged']
    DEST.mkdir();(DEST/'.gitattributes').write_text('* -text\n',encoding='utf-8',newline='\n')
    sources=[*SOURCES,'docs/literature/mashr-proxy-command/.gitattributes',*[p.relative_to(ROOT).as_posix() for p in sorted((ROOT/'examples/mashr-proxy').rglob('*')) if p.is_file()]]
    bound={n:pin(ROOT/n) for n in sources};items={};graphs={}
    def tree(path,prefix):
        graph=census(path,1_073_741_824,100000);graphs[path]=graph
        for n,v in graph.items():assert prefix+'/'+n not in items;items[prefix+'/'+n]=(path/n,v)
        return graph
    for name,record in records.items():
        graph=tree(RUN/name,'evidence/'+name)
        if name!='repository-example-1':assert {n:v for n,v in graph.items() if n!='COMPLETED.json'}==record['files']
    for name in ['matrix-1','matrix-2','before-extension']:tree(RUN/name,'history/'+name)
    for name in ['build-1.log','matrix-1.log','matrix-2.log','matrix-3.log','protocols-1.log','repository-example-1.log']:
        path=RUN/name;items['logs/'+name]=(path,pin(path))
    # Catalog counts are historical context, not an input to this scientific method.
    catalog=ROOT/'docs/literature/omics-2023-2026/summary.json';items['context/catalog-summary-at-validation.json']=(catalog,pin(catalog))
    for n,v in bound.items():items['source/'+n]=(ROOT/n,v)
    with zipfile.ZipFile(DEST/'controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for n,(path,v) in sorted(items.items()):
            raw=path.read_bytes();assert pin(path)==v,n
            entry=zipfile.ZipInfo(n,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;archive.writestr(entry,raw)
    assert all(census(p,1_073_741_824,100000)==g for p,g in graphs.items()) and all(pin(ROOT/n)==v for n,v in bound.items()) and all(pin(ROOT/n)==v for n,v in protected.items())
    dependencies={**prior['baseline_dependencies']}
    for name in ['VALIDATION.json','controls.zip']:
        path='docs/literature/mashr-composite/'+name;dependencies[path]=pin(ROOT/path)
    proof=dict(schema_version=1,status='passed-public-native-proxy-summary-command-development-checkpoint',
        recorded_utc=datetime.now(timezone.utc).isoformat(),scope='Explicit independent marginal proxy mode in the public native-summary command, full native/summary replay, every score/decision/diagnostic retained and guarded admission; prior v1 bundles remain verifiable.',
        artifact=matrix['artifact'],archive=dict(path='controls.zip',**pin(DEST/'controls.zip'),entries=len(items)),
        tests_per_JVM=477,new_command_tests_per_JVM=37,original_R_library_cases=30,heap_mib=128,
        platforms=[r['name'] for r in matrix['runs']],public_protocol_commands=30,cross_produced_bundle_checks=6,
        fresh_original_R_score_decisions=360,repository_example_commands=4,
        source_pins=bound,unchanged_prior_source_pins=protected,changed_prior_source=change,
        protected_packaged_classes_unchanged=matrix['protected_packaged_classes_unchanged'],changed_packaged_classes=matrix['changed_packaged_classes'],new_packaged_classes=matrix['new_packaged_classes'],owned_classes=matrix['owned_classes'],
        baseline_dependencies=dependencies,canonical_executable_unchanged=matrix['canonical_executable_unchanged'],
        catalog_at_validation=dict(**pin(catalog),archive_path='context/catalog-summary-at-validation.json',binding='Historical context only; future catalog metadata changes do not change these scientific calculations.'),
        archive_files={n:v for n,(_,v) in sorted(items.items())},claims=CLAIMS,
        retained_attempts=dict(matrix_1='Pre-test verifier expected only three changed nested classes; all nine classes from the extended source contain changed line/nest metadata. No scientific code correction was required.',matrix_2='Linux477 passed, Windows completion unproven after user-pause interruption. Sessions were missing and matching Windows processes were confirmed absent before matrix3 started.'),
        required_action='Use the identified candidate and MASHR_PROXY_COMMAND.md. Require exit0, valid COMPLETED.json and --verify; inspect every score and diagnostic. Declare exact selectors, independence, threshold and boundary. Unsupported formal/joint/sample methods SKIP3 without proxy fallback; invalid input, integrity, capacity, cancellation and finalization FAIL2 without admission. Retain prior archives for baseline closure. Use this proof for the intentionally extended summary source; earlier current-source pins describe historical snapshots.')
    write(DEST/'VALIDATION.json',proof);print(json.dumps(dict(status=proof['status'],tests_per_JVM=477,public_protocol_commands=30,cross_produced_bundle_checks=6,fresh_original_R_score_decisions=360,archive=proof['archive'])))


if __name__=='__main__':
    try:main()
    except Exception as failure:print('FAIL2_PROXY_CHECKPOINT_FREEZE: '+str(failure)+'\nRequired action: retain failed evidence; inspect the named mismatch and never overwrite a checkpoint.',file=sys.stderr);sys.exit(2)
