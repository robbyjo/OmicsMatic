# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only integrity and complete-family gates for the original coordinate-workflow development adapter."""
import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

def verify(emit=True):
 from palantir_multiscale_evidence import verify as verify_parent
 verify_parent(False)
 folder=ROOT/'docs/literature/palantir-workflow-development';p=json.loads((folder/'VALIDATION.json').read_bytes());assert p['status']=='passed-original-Palantir-coordinate-workflow-adapter-only' and p['coordinate_workflow_adapter_admitted'] and not p['new_catalog_integration_credit'] and not p['whole_goal_complete'] and not p['independent_trajectory_refit'];assert pin(folder/'controls.zip')==p['archive'];files=unpack(folder/'controls.zip',p)
 for name,want in p['source_pins'].items():assert pin(ROOT/name)==byte_pin(files['current-source/'+name])==want,name
 for name,want in p['parent_proofs'].items():assert pin(ROOT/name)==want,name
 base='build/literature-review/palantir-runtime/';get=lambda name:json.loads(files[base+name]);built=get(p['build_directory']+'/COMPLETED.json');assert built['status']=='PASS_COMPILED_PALANTIR_COORDINATE_WORKFLOW_ONLY' and built['artifact']==p['artifact'] and built['unchanged_parent_entries']==21133;setup=get('workflow-setup-1/COMPLETED.json');assert setup['status']=='PASS_BOUND_ORIGINAL_PALANTIR_WORKFLOW_RUNTIME_ONLY' and setup['runtime_trees_unchanged'] and setup['shared_inputs_unchanged'] and not setup['namespace_patched'] and not setup['analysis_admitted'] and not setup['analysis_installed_software'] and len(setup['processes'])==4 and all(v['exit_code']==0 and not v['owned_live_after_cleanup'] for v in setup['processes']);assert get('workflow-setup-1/base-before.json')==get('workflow-setup-1/base-after.json') and get('workflow-setup-1/runtime-before.json')==get('workflow-setup-1/runtime-after.json')
 for name in ['workflow-install-forbidden-1','workflow-base-missing-1']:assert get(name+'/COMPLETED.json')['status']=='SKIP3_PALANTIR_RUNTIME_UNAVAILABLE' and not get(name+'/COMPLETED.json')['analysis_admitted'] and not get(name+'/COMPLETED.json')['processes']
 controls=get('workflow-input-controls-1/COMPLETED.json');assert controls['status']=='PASS_PALANTIR_INPUT_EXPORT_PERMISSION_AND_TIMEOUT_CONTROLS_ONLY' and controls['artifact']==p['artifact'] and len(controls['checks'])==18 and not controls['analysis_admitted'];timeout=get('workflow-input-controls-1/owned-timeout/original.process.json');assert 'TimeoutError' in timeout['error'] and not timeout['owned_live_after_cleanup']
 assert get('workflow-catalog-preservation-1/COMPLETED.json')['status']=='PASS_UNCHANGED_CURRENT_CATALOG_AND_ROLE_DRAFTS'
 root=p['validation_directory'];validation=get(root+'/COMPLETED.json');assert validation['status']=='PASS_TWO_ORIGINAL_EDITIONS_THREE_JVM_PALANTIR_COORDINATE_WORKFLOWS_ONLY' and validation['artifact']==p['artifact'] and len(validation['runs'])==8 and len(validation['negative_controls'])==15;assert all(run['exit_code']==0 for run in validation['runs']);native=[r for r in validation['runs'] if r['label'].startswith(('windows21-','windows25-','linux21-'))];assert len(native)==6 and {r['label'].split('-')[0] for r in native}=={'windows21','windows25','linux21'}
 for edition,count in [('1.4.4',10),('1.4.6',11)]:
  report=get(root+'/'+edition+'-analysis/analysis-status.json');assert report['status']=='PASS_ORIGINAL_PALANTIR_WORKFLOWS' and report['result_admitted'] and report['atomic_batch'] and not report['paper_reproduced'] and report['edition']==edition and len(report['cases'])==count;worker=root+'/'+edition+'-analysis/worker/';complete=get(worker+'COMPLETED.json');assert complete['status']=='PASS_COMPLETE_ORIGINAL_PALANTIR_WORKFLOWS_ONLY' and not complete['result_admitted'] and not complete['namespace_patched'] and not complete['pickle_deserialized'];process=get(worker+'original.process.json');assert process['exit_code']==0 and not process['owned_live_after_cleanup'];audit=get(root+'/'+edition+'-analysis/native-audit.json');assert len(audit['cases'])==count and not audit['independent_trajectory_refit'] and audit['numeric_states']>50000 and audit['tsv_states']>5000 and audit['python_startup']['status']=='PASS_EFFECTIVE_PYTHON_STARTUP_ONLY'
  manifest=get(root+'/'+edition+'-analysis/manifest.json');assert byte_pin(files[base+root+'/'+edition+'-analysis/manifest.json'])['sha256']==report['manifest_sha256']
  for name,want in manifest['files'].items():
   logical=base+root+'/'+edition+'-analysis/'+name
   assert logical in files and byte_pin(files[logical])==want,logical
  for case in complete['cases']:
   home=worker+case['id']+'/';observer=get(home+'observer.json');assert observer['namespace_functions_unchanged'] and observer['code_objects_unchanged']
   if case['method']!='diffusion':assert home+'before-result-clipping.json' in {n.removeprefix(base) for n in files if n.startswith(base)}
 for run in native:assert run['report']['status']=='PASS_COMPLETE_ORIGINAL_PALANTIR_OBSERVATION_AUDITS_ONLY' and len(run['report']['cases'])==(10 if run['label'].endswith('1.4.4') else 11)
 for label in ['unsupported-edition','missing-runtime','windows-platform']:
  report=get(root+'/'+label+'-analysis/analysis-status.json');assert report['status'].startswith('SKIP3') and not report['result_admitted']
 failed=get(root+'/late-source-failure-analysis/analysis-status.json');source=get(root+'/late-source-failure-analysis/worker/COMPLETED.json');assert failed['status']=='FAIL2_NO_RESULT_ADMITTED' and not failed['result_admitted'] and len(source['earlier_cases_provisional'])==1 and source['earlier_cases_provisional'][0]['id']=='core' and source['status']=='FAIL2_ORIGINAL_PALANTIR_BATCH_UNADMITTED';assert all(v['exit_code']!=0 for v in validation['negative_controls']);assert sum(v['label'].startswith('corruption-') for v in validation['negative_controls'])>=7
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged'];artifact=ROOT/p['artifact']['path']
 if artifact.exists():assert pin(artifact)=={k:p['artifact'][k] for k in ['bytes','sha256']}
 if emit:print('Verified original Palantir coordinate adapter:21 full original workflows, both editions, three JVM native audits, dependency skips, late atomic failure and corrupted result rejection. Catalog application and larger goal remain open.')
 return p
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete original/native evidence gates')
 verify()
