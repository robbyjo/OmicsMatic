# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify native SimplePPT arithmetic without granting scFates workflow/tool credit."""
import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

BASE='build/literature-review/scfates-runtime/'

def load_files(folder,proof):
    files={};digests=set()
    for part in proof['archive_parts']:
        path=folder/part['name'];assert pin(path)==part['pin'] and path.stat().st_size<95000000
        members=unpack(path,part['packing']);assert not(set(files)&set(members))
        # A content hash is assigned to only one part, retaining global deduplication.
        unique={byte_pin(v)['sha256'] for v in members.values()};assert not(digests&unique);digests.update(unique);files.update(members)
    assert len(files)==proof['logical_files'] and {n:byte_pin(v) for n,v in files.items()}==proof['logical_file_pins']
    return files

def verify(emit=True):
    folder=ROOT/'docs/literature/simpleppt-core-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-native-SimplePPT-arithmetic-and-original-scfates-controls-only' and not p['implementation_credit'] and not p['full_scfates_adapter_admitted'] and not p['whole_goal_complete'];files=load_files(folder,p)
    for name,want in p['source_pins'].items():assert pin(ROOT/name)==byte_pin(files['current-source/'+name])==want,name
    for name,want in p['parent_proofs'].items():assert pin(ROOT/name)==want,name
    get=lambda n:json.loads(files[BASE+n]);source=get('source-intake-1/SOURCE.json');assert source['status']=='PASS_OFFICIAL_SCFATES_SIMPLEPPT_SOURCE_ONLY' and {k:v['version'] for k,v in source['packages'].items()}==dict(scfates='1.2.5',simpleppt='1.1.7')
    for name,package in source['packages'].items():
        for member,want in package['wheel_members'].items():assert byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/simpleppt-core/original/'+name+'/'+member])==want
        assert package['revision']=={'scfates':'8d8c0e629d7197fcf9801cc595bd23bdbf9f8118','simpleppt':'8af32d65c41191b24f53ea829530cd3e3c5f0255'}[name]
    review=get('source-review-1/COMPLETED.json');assert review['status']=='PASS_SCFATES_SIMPLEPPT_SCIENTIFIC_SOURCES_AND_GENERATED_VERSION_REVIEW_ONLY' and review['python_modules']==58 and len(review['generated_metadata_differences'])==1 and not review['original_function_patched']
    setup=get('reference-setup-1/COMPLETED.json');assert setup['status']=='PASS_COMPLETE_PRIVATE_ORIGINAL_SCFATES_SIMPLEPPT_IMPORTS_ONLY' and setup['shared_runtime_unchanged'] and setup['original_scientific_sources_unchanged'] and not setup['namespace_patched'] and not setup['analysis_admitted'];assert len(get('reference-setup-1/dependency-locks.json')['archives'])==50
    assert get('reference-setup-1/shared-before.json')==get('reference-setup-1/shared-after.json') and get('reference-setup-1/dependencies-before.json')==get('reference-setup-1/dependencies-after.json')
    original=get('original-reference-2/COMPLETED.json');assert original['status']=='PASS_FULL_ORIGINAL_SCFATES_CPU_REFERENCE_FAMILY_ONLY' and original['all_runtime_trees_unchanged'] and len(original['processes'])==1 and original['processes'][0]['exit_code']==0 and not original['processes'][0]['owned_live_after_cleanup'];report=get('original-reference-2/original/COMPLETED.json');assert report['status']=='PASS_COMPLETE_ORIGINAL_SCFATES_CPU_ARITHMETIC_AND_WORKFLOWS_ONLY' and len(report['arithmetic_cases'])==40 and len(report['workflows'])==12 and report['successful_original_workflows']==9 and report['expected_original_errors']==3 and report['original_namespace_functions_unchanged'] and not report['namespace_patched'] and not report['user_pickle_loaded']
    workflows={v['id']:v for v in report['workflows']};errors={v['id']:v['error_type'] for v in report['workflows'] if v['status']=='original-error'};assert errors=={'paper-200-node-settings':'IndexError','source-too-many-nodes':'ValueError','source-zero-iterations':'UnboundLocalError'}
    assert workflows['paper-200-node-curved-coordinate-control']['nsteps']==10 and workflows['paper-3000-node-settings']['nsteps']==1 and all(not v['paper_reproduced'] for v in workflows.values())
    for label in ['shared','dependencies']:assert get('original-reference-2/'+label+'-before.json')==get('original-reference-2/'+label+'-after.json')
    first=get('original-reference-1/COMPLETED.json');assert first['status']=='FAIL2_SCFATES_ORIGINAL_REFERENCE_FAMILY' and first['all_runtime_trees_unchanged'];assert get('math-native-1/COMPLETED.json')['status'].startswith('FAIL2') and get('math-native-2/COMPLETED.json')['status'].startswith('FAIL2')
    build=get('math-build-6/COMPLETED.json');assert build['status']=='PASS_COMPILED_NATIVE_SIMPLEPPT_CPU_ARITHMETIC_ONLY' and build['artifact']==p['artifact'] and build['unchanged_parent_entries']==21164
    for name,want in build['source_pins'].items():assert p['source_pins'][name]==want
    native=get('math-native-4/COMPLETED.json');assert native['status']=='PASS_THREE_JVM_COMPLETE_SIMPLEPPT_CPU_ARITHMETIC_AND_OBSERVED_FIT_EQUATIONS_ONLY' and len(native['runs'])==3 and {r['label'] for r in native['runs']}=={'windows21','windows25','linux21'}
    for run in native['runs']:
        r=run['report'];assert run['exit_code']==0 and (r['cases'],r['actual_original_fit_observations'],r['numeric_states'],r['nonfinite_states'],r['source_zero_mass_rows'],r['source_normalization_NaNs_mapped_to_zero'],r['native_rejections'],r['correlation_comparison_corruptions_rejected'])==(40,10,18203693,6,30,7150,14,5) and not r['complete_tree_fitter'] and not r['implementation_credit'];assert len(r['correlation_cancellation_comparisons'])==14 and all(c['absolute_difference']<=c['correlation_cancellation_absolute_envelope']+c['relative_envelope']*abs(c['original'])+16*__import__('math').ulp(c['original']) for c in r['correlation_cancellation_comparisons'])
    corrupt=get('math-corruptions-1/COMPLETED.json');assert corrupt['status']=='PASS_NINE_SIMPLEPPT_COMPARISON_CORRUPTIONS_REJECTED_ONLY' and corrupt['original_fixtures_unchanged'] and len(corrupt['checks'])==9
    for case in corrupt['checks']:
        assert case['exit_code']!=0 and case['complete_comparison_rejected']
        for name,want in case['fixture_overlay'].items():assert byte_pin(files[BASE+'math-corruptions-1/'+case['mutation']+'/family/'+name])==want
    assert get('math-example-1/COMPLETED.json')['status']=='PASS_SIMPLEPPT_NATIVE_DOCUMENTED_EXAMPLE_ONLY';skip=get('setup-permissions-1/COMPLETED.json');assert skip['status']=='PASS_FOUR_SCFATES_PERMISSION_AND_CONNECTED_SKIP_CONTROLS_ONLY' and len(skip['processes'])==4 and all(v['exit_code']==3 for v in skip['processes']) and not skip['software_installed'] and not skip['original_analysis_executed']
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged'];artifact=ROOT/p['artifact']['path']
    if artifact.exists():assert pin(artifact)=={k:p['artifact'][k] for k in ['bytes','sha256']}
    assert p['current_catalog_integrations']==86 and p['including_existing_adapters']==89 and p['missing_cataloged_tools']==255
    if emit:print('Verified SimplePPT arithmetic:40 cases/10 observed fits/18203693 numeric states per JVM;3 JVMs,9 corruptions,4 setup skips,9 complete original workflows and3 source errors. No scFates adapter/tool credit;250-paper goal remains active.')
    return p

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native/original evidence gates')
    verify()
