#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze admitted summary-command evidence without rewriting historical checkpoints."""
import json,sys,zipfile
from datetime import datetime,timezone
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import census,pin,write
ROOT=Path(__file__).resolve().parents[2]
RUN=ROOT/'build/literature-review/mashr-runtime/summary-command-development-1'
DEST=ROOT/'docs/literature/mashr-summary'
SOURCES=['src/main/java/org/jlinalg/cli/MashSummaryCli.java','src/main/java/org/jlinalg/cli/MashSummaryLimits.java','src/test/java/org/jlinalg/cli/MashSummaryCliTest.java','tools/literature/validate_mashr_summary.py','tools/literature/validate_mashr_summary_commands.py','tools/literature/finalize_mashr_summary.py','tools/literature/validate_mashr_summary_checkpoint.py','docs/literature/MASHR_SUMMARY_COMMAND.md']


def main():
    records={n:json.loads((RUN/n/'COMPLETED.json').read_text(encoding='utf-8')) for n in ['matrix-2','commands-2']}
    matrix,commands=records['matrix-2'],records['commands-2']
    assert matrix['status']=='passed-three-JVM-native-mash-summary-command-matrix' and matrix['tests_per_JVM']==396 and len(matrix['runs'])==3
    assert commands['status']=='passed-public-summary-native-source-and-portability-commands' and len(commands['cross_produced_bundles'])==6 and all(len(r['commands'])==13 for r in commands['runs']) and commands['artifact']==matrix['artifact']
    for record in records.values():assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items())
    sharing=json.loads((ROOT/'docs/literature/mashr-sharing/VALIDATION.json').read_text(encoding='utf-8'));native=json.loads((ROOT/'docs/literature/mashr-native-workflow/VALIDATION.json').read_text(encoding='utf-8'));preparation=json.loads((ROOT/'docs/literature/mashr-original-preparation/VALIDATION.json').read_text(encoding='utf-8'))
    protected={**native['source_pins'],**preparation['source_pins'],**sharing['source_pins']};assert all(pin(ROOT/n)==v for n,v in protected.items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==matrix['canonical_executable_unchanged']==preparation['canonical_executable_unchanged']
    catalog=pin(ROOT/'docs/literature/omics-2023-2026/summary.json');assert catalog==preparation['catalog_summary_unchanged']
    assert pin(ROOT/matrix['artifact']['path'])=={k:matrix['artifact'][k] for k in ['bytes','sha256']}
    assert not DEST.exists();DEST.mkdir();(DEST/'.gitattributes').write_text('* -text\n',encoding='utf-8',newline='\n')
    sources=[*SOURCES,'docs/literature/mashr-summary/.gitattributes',*[p.relative_to(ROOT).as_posix() for p in sorted((ROOT/'examples/mashr-summary').rglob('*')) if p.is_file()]];bound={n:pin(ROOT/n) for n in sources}
    items={};graphs={}
    def tree(path,prefix):
        graph=census(path,1_073_741_824,100000);graphs[path]=graph
        for name,value in graph.items():assert prefix+'/'+name not in items;items[prefix+'/'+name]=(path/name,value)
    for name in records:
        tree(RUN/name,'evidence/'+name);assert {n:v for n,v in graphs[RUN/name].items() if n!='COMPLETED.json'}==records[name]['files']
    for name in ['matrix-1','commands-1','before-condition-path']:tree(RUN/name,'history/'+name)
    tree(RUN/'repository-example-request-1','evidence/repository-example-request');tree(RUN/'repository-example-result-1','evidence/repository-example-result')
    assert (RUN/'repository-example-result-1/COMPLETED.json').exists() and 'verified by native-source and complete summary replay' in (RUN/'repository-example-verify-1.log').read_text(encoding='utf-8')
    for path in RUN.glob('*.log'):items['logs/'+path.name]=(path,pin(path))
    for n,value in bound.items():items['source/'+n]=(ROOT/n,value)
    with zipfile.ZipFile(DEST/'controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,(path,value) in sorted(items.items()):
            raw=path.read_bytes();assert pin(path)==value;entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;archive.writestr(entry,raw)
    assert all(census(path,1_073_741_824,100000)==graph for path,graph in graphs.items()) and all(pin(ROOT/n)==v for n,v in bound.items()) and all(pin(ROOT/n)==v for n,v in protected.items())
    dependencies={}
    for stem in ['mashr-native-workflow','mashr-original-preparation','mashr-sharing']:
        for name in ['VALIDATION.json','controls.zip']:path='docs/literature/'+stem+'/'+name;dependencies[path]=pin(ROOT/path)
    for name in ['MASHR_NATIVE_EXECUTION_VALIDATION.json','mashr-native-execution-controls.zip']:path='docs/literature/'+name;dependencies[path]=pin(ROOT/path)
    proof=dict(schema_version=1,status='passed-public-native-mash-summary-command-development-checkpoint',recorded_utc=datetime.now(timezone.utc).isoformat(),scope='Standalone public Java command: complete captured native-source replay, explicit significance/sharing and optional nominal-gated marginal sign products, full portable summary replay and guarded admission.',artifact=matrix['artifact'],archive=dict(path='controls.zip',**pin(DEST/'controls.zip'),entries=len(items)),tests_per_JVM=396,new_command_tests_per_JVM=25,original_R_library_cases=32,heap_mib=128,platforms=[r['name'] for r in matrix['runs']],real_commands=39,cross_produced_summary_bundles=6,repository_example_with_standalone_verify=True,source_pins=bound,protected_prior_source_pins=protected,protected_packaged_classes_unchanged=matrix['protected_packaged_classes_unchanged'],owned_classes=matrix['owned_classes'],baseline_dependencies=dependencies,canonical_executable_unchanged=matrix['canonical_executable_unchanged'],catalog_summary_unchanged=catalog,archive_files={n:v for n,(_,v) in sorted(items.items())},claims=dict(standalone_public_summary_command_available=True,complete_native_source_replayed=True,source_posterior_tables_captured=True,nominal_P_values_inferred=False,joint_probability_inferred=False,root_dispatcher_changed=False,study_route_available=False,paper_role_coverage_complete=False,catalog_promoted=False,whole_paper_reproduced=False,biological_accuracy_certified=False,released_or_deployed=False),required_action='Use MashSummaryCli directly with the identified artifact. Require exit0, top-level COMPLETED.json and --verify. Preserve NA sharing and exact-zero/underflow states. Marginal sign products are not joint probabilities. Unsupported formal methods are SKIP3; invalid/integrity/resource/cancellation failures are FAIL2. Keep prior archives for full baseline closure and read MASHR_SUMMARY_COMMAND.md.')
    write(DEST/'VALIDATION.json',proof);print(json.dumps(dict(status=proof['status'],tests_per_JVM=396,real_commands=39,cross_produced_summary_bundles=6,archive=proof['archive'])));return 0


if __name__=='__main__':
    try:sys.exit(main())
    except Exception as failure:print('FAIL2_SUMMARY_CHECKPOINT_FREEZE: '+str(failure),file=sys.stderr);sys.exit(2)
