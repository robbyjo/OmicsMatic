#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze a completed original-preparation development checkpoint without binaries."""
import argparse
from datetime import datetime,timezone
import json
from pathlib import Path
import sys
import zipfile
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import census,pin,write

ROOT=Path(__file__).resolve().parents[2]
RUN=ROOT/'build/literature-review/mashr-runtime/original-preparation-development-1'
DEST=ROOT/'docs/literature/mashr-original-preparation'
SOURCES=[
 'tools/literature/intake_mashr_runtime_lock.py','tools/literature/mashr-original-runtime-lock.json',
 'tools/literature/install_mashr_runtime.py','tools/literature/mashr_original_runtime.py',
 'tools/literature/mashr-runtime-health.R','tools/literature/mashr_preparation.py',
 'tools/literature/mashr-preparation-oracle.R','tools/literature/collect_mashr_preparation_controls.py',
 'tools/literature/validate_mashr_runtime_controls.py','tools/literature/validate_mashr_preparation_evidence.py',
 'tools/literature/validate_mashr_preparation_admission.py',
 'tools/literature/finalize_mashr_preparation.py','tools/literature/validate_mashr_preparation_checkpoint.py',
 'src/main/resources/org/jlinalg/omics/mashr-covariance-preparation/prepare.R',
 'docs/literature/MASHR_ORIGINAL_PREPARATION.md',
]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--evidence',required=True);args=parser.parse_args()
    evidence=RUN/args.evidence;assert evidence.parent==RUN and evidence.is_dir()
    roots={'controls':RUN/'controls-3','runtime_controls':RUN/'runtime-controls-3','evidence_controls':evidence,'admission_controls':RUN/'admission-controls-1','setup':RUN/'managed-setup-4','earlier_setup':RUN/'managed-setup-3'}
    records={k:json.loads((p/'COMPLETED.json').read_text()) for k,p in roots.items()}
    assert records['controls']['status']=='passed-original-preparation-and-native-inference-controls'
    assert records['runtime_controls']['status']=='passed-mashr-runtime-dependency-and-owned-process-controls'
    assert records['evidence_controls']['status']=='passed-original-preparation-evidence-and-target-separation-controls'
    assert records['admission_controls']['status']=='passed-mashr-preparation-path-and-admitted-metadata-controls' and len(records['admission_controls']['controls'])==5
    assert all(records[n]['status']=='COMPLETED_MASHR_PREPARATION_RUNTIME_SETUP_ONLY' for n in ['setup','earlier_setup'])
    assert len(records['controls']['controls'])==13 and len(records['runtime_controls']['controls'])==8 and len(records['evidence_controls']['controls'])==12
    sources={n:pin(ROOT/n) for n in SOURCES}
    sources.update({p.relative_to(ROOT).as_posix():pin(p) for p in sorted((ROOT/'examples/mashr-preparation').rglob('*')) if p.is_file()})
    for name in ['controls','runtime_controls','evidence_controls','admission_controls']:
        assert all(pin(ROOT/n)==v for n,v in records[name]['source_pins'].items()),name
    native=json.loads((ROOT/'docs/literature/mashr-native-workflow/VALIDATION.json').read_text())
    assert len(native['source_pins'])==81 and all(pin(ROOT/n)==v for n,v in native['source_pins'].items())
    assert pin(ROOT/native['artifact']['path'])=={k:native['artifact'][k] for k in ['bytes','sha256']}
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==dict(bytes=162107844,sha256='bd1a3b2448d7016ae4ded3f30a6c5ae92e05a0078ae688f4b93d046cc355eeb2')
    catalog=pin(ROOT/'docs/literature/omics-2023-2026/summary.json');assert catalog==dict(bytes=1181,sha256='27c284da98c87ba3ed9a80550c9d93e4c97e5955756055378d5b9cc34eb012bd')
    assert not DEST.exists();DEST.mkdir();(DEST/'.gitattributes').write_text('* -text\n',encoding='utf-8',newline='\n')
    items={};root_pins={}
    def add_tree(path,prefix):
        graph=census(path,2_147_483_648,100000);root_pins[str(path.relative_to(ROOT))]=graph
        for name,value in graph.items():
            member=prefix+'/'+name;assert member not in items;items[member]=(path/name,value)
    for name,path in roots.items():
        add_tree(path,'evidence/'+name)
        graph={n:v for n,v in root_pins[str(path.relative_to(ROOT))].items() if n!='COMPLETED.json'}
        assert graph==records[name]['report_files' if name in ['setup','earlier_setup'] else 'files'],name
    history=['controls-1','controls-1-source-code','controls-2','runtime-controls-1','runtime-controls-2','runtime-controls-2-source-code','managed-setup-2','managed-setup-3-source-code','protocol-1','protocol-2','protocol-2-source-code','protocol-3','protocol-4','attempt-source','evidence-controls-1','evidence-controls-1-source-code','evidence-controls-2','evidence-controls-2-source-code','evidence-controls-3','before-path-admission-source-code']
    retained=[]
    for name in history:
        path=RUN/name
        if path.is_dir():add_tree(path,'history/'+name);retained.append(name)
    example=json.loads((RUN/'example-result-1.log').read_text());example_verify=json.loads((RUN/'example-verify-2.log').read_text())
    assert example['status']=='COMPLETED_ORIGINAL_COVARIANCES_NATIVE_FIT' and example_verify['status']=='passed-complete-parent-replay' and example_verify['source_unchanged'] is True
    add_tree(RUN/'example-input-1','evidence/example_input');add_tree(RUN/'example-result-1','evidence/example_result')
    for name,log in [('example_production_verify',example['verification']),('example_standalone_verify',example_verify)]:
        scratch=Path(log['scratch']);assert scratch.parent==RUN and log['comparison']['maximum_absolute_error']==0;add_tree(scratch,'evidence/'+name)
    for name in ['effects.tsv','standard-errors.tsv','populations.tsv','settings.tsv','preparation.tsv']:assert pin(RUN/'example-input-1'/name)==pin(ROOT/'examples/mashr-preparation'/name)
    # Record receipts/metadata and logs, without redistributing installed R or DLLs.
    for path in sorted(RUN.glob('*.log')):
        items['logs/'+path.name]=(path,pin(path))
    for path in sorted((RUN/'runtime-lock-intake-2').glob('*.json')):
        items['official-intake/'+path.name]=(path,pin(path))
    for name,value in sources.items():items['source/'+name]=(ROOT/name,value)
    archive=DEST/'controls.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as target:
        for name,(path,value) in sorted(items.items()):
            raw=path.read_bytes();assert pin(path)==value
            entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;target.writestr(entry,raw)
    assert archive.stat().st_size<99_000_000,'Keep a normal GitHub file below its individual-file limit'
    assert all(census(ROOT/path,2_147_483_648,100000)==graph for path,graph in root_pins.items())
    assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in native['source_pins'].items())
    positive=[c for c in records['controls']['controls'] if c['expected_exit_code']==0]
    numeric=[c['original_numeric_comparison'] for c in positive]
    replay=[json.loads((roots['controls']/c['name']/'controller.stdout').read_text())['verification'] for c in positive]
    assert all(r['status']=='passed-complete-parent-replay' and r['source_unchanged'] is True for r in replay)
    baselines=['docs/literature/mashr-native-workflow/VALIDATION.json','docs/literature/mashr-native-workflow/controls.zip','docs/literature/MASHR_NATIVE_EXECUTION_VALIDATION.json','docs/literature/mashr-native-execution-controls.zip']
    proof=dict(schema_version=1,status='passed-original-mashr-preparation-and-native-fit-development-checkpoint',recorded_utc=datetime.now(timezone.utc).isoformat(),
        scope='Actual original canonical/uncentered-PCA/Bovy-ED covariance preparation with separately verified native plain-EM fitting, full posterior replay, explicit populations and resource admission.',
        artifact=dict(path='controls.zip',**pin(archive),entries=len(items)),source_pins=sources,protected_native_source_pins=native['source_pins'],native_artifact=native['artifact'],
        baseline_dependencies={n:pin(ROOT/n) for n in baselines},canonical_executable_unchanged=canonical,catalog_summary_unchanged=catalog,
        counts=dict(original_native_cases=13,positive_original_native_cases=9,expected_FAIL2_cases=4,runtime_dependency_and_process_controls=8,evidence_target_and_platform_controls=12,path_and_admitted_metadata_controls=5,original_numeric_values_compared=sum(n['count'] for n in numeric),repository_example_with_standalone_parent_replay=1),
        maximum_original_absolute_error=max(n['maximum_absolute_error'] for n in numeric),maximum_independent_original_training_dual_gap=max(n['training_independent_original_dual_gap'] for n in numeric),
        validation=dict(actual_python=records['setup']['python'],actual_R='4.6.1',actual_mashr='0.2.79',private_CRAN_packages=17,private_R_files=1874,private_monitor_files=34,positive_parent_replay_maximum_absolute_error=max(r['comparison']['maximum_absolute_error'] for r in replay),
          java='Windows Java21/25 native children; Java21 Linux copied-child verification',parent_Linux='SKIP3 under this Windows profile; producer preserved',native_resealed_posterior='rejected after child and parent hash recomputation',captured_code_execution='never; altered captured code rejected before execution',target_separation='Every covariance/RDS/name/numeric history and complete native fit/model graph identical after target-only changes; only original ED run timestamp normalized',sampled_monitor_is_OS_sandbox=False),
        reports={k:'evidence/'+k+'/COMPLETED.json' for k in roots},retained_history=retained,archive_files={n:v for n,(_,v) in sorted(items.items())},
        claims=dict(original_covariance_functions_executed=True,native_fitter_executed=True,source_to_binary_reproduction_verified=False,whole_paper_reproduced=False,biological_accuracy_certified=False,sharing_and_composite_hypotheses_complete=False,paper_role_review_complete=False,study_routes_available=False,catalog_promoted=False,released_or_deployed=False),
        required_action='For SKIP3, skip dependent analysis until the exact permitted capability is installed and admitted. For FAIL2, retain all partial evidence, correct the named input/integrity/numerical/convergence/resource failure and retry into NEW paths. Read the parent and child WARNINGS.md. Preserve baseline archives for full dependency closure.')
    write(DEST/'VALIDATION.json',proof);print(json.dumps(dict(status=proof['status'],archive=proof['artifact'],counts=proof['counts'])));return 0


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_PREPARATION_CHECKPOINT: '+str(failure),file=sys.stderr);sys.exit(2)
