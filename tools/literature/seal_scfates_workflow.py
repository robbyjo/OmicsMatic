# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete adapter evidence on NEW paths; inherit exact existing bytes without duplicating them."""
import argparse,json,os,shutil,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/scfates-runtime';final=ROOT/'docs/literature/scfates-workflow-development';assert not out.exists() and out.is_relative_to(base) and not final.exists();out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_WORKFLOW_EVIDENCE_SEAL',whole_goal_complete=False)
    try:
        build=json.loads((base/'workflow-build-5/COMPLETED.json').read_bytes());assert build['status']=='PASS_COMPILED_SCFATES_COORDINATE_WORKFLOW_ONLY';assert json.loads((base/'workflow-runtime-final-1/COMPLETED.json').read_bytes())['status']=='PASS_COMPLETE_SCFATES_BASE_AND_PRIVATE_RUNTIME_UNCHANGED_AFTER_CONTROLS_ONLY';core_folder=ROOT/'docs/literature/simpleppt-core-development';core=json.loads((core_folder/'VALIDATION.json').read_bytes());by_hash={v['sha256']:(n,v) for n,v in core['logical_file_pins'].items()};sources=set(build['source_pins']);sources.update('tools/literature/'+n for n in ['prepare_scfates_workflow_sources.py','prepare_scfates_workflow_cases.py','run_scfates_workflow_control.py','validate_scfates_native.py','audit_scfates_workflow_reference_parity.py','collect_scfates_root_scan_reference_linux.py','review_scfates_root_distance_sources.py','test_scfates_workflow_corruptions.py','test_scfates_input_contracts.py','validate_scfates_workflow_contracts.py','validate_scfates_documented_example.py','validate_scfates_supervision.py','verify_scfates_runtime_unchanged_linux.py','scfates_workflow_evidence.py','seal_scfates_workflow.py','setup_deseq2_reference.py','setup_hail_reference_linux.py','inventory_hail_runtime_linux.py','validate_mashr_native.py','signac_scan_evidence.py','simpleppt_math_evidence.py']);sources.add('docs/literature/SCFATES_WORKFLOW.md');pins={n:pin(ROOT/n) for n in sorted(sources)};assert all(pins[n]==want for n,want in build['source_pins'].items());logical={};own={};references={};blobs={}
        def add(name,path=None,raw=None):
            assert name not in logical;raw=path.read_bytes() if raw is None else raw;want=byte_pin(raw);logical[name]=want;digest=want['sha256']
            if digest in by_hash:parent,w=by_hash[digest];assert w==want;references[name]=dict(parent_logical_file=parent,pin=want)
            else:
                if digest in blobs:assert blobs[digest]==raw;raw=blobs[digest]
                else:blobs[digest]=raw
                own[name]=raw
        for name in pins:add('current-source/'+name,ROOT/name)
        for path in sorted(base.iterdir()):
            if path.is_file() and path.suffix in ['.py','.txt','.md','.json']:add(path.relative_to(ROOT).as_posix(),path)
        overlays=['workflow-corruptions-1','workflow-input-contracts-2'];excluded={'archives','wheels','classes','__pycache__','kit'}
        for directory in sorted(base.iterdir()):
            if not directory.is_dir() or directory==out or directory.name.startswith('workflow-seal-') or not directory.name.startswith(('workflow-','root-scan-','root-distance-')):continue
            for current,dirs,names in os.walk(directory,topdown=True,followlinks=False):
                dirs[:]=[n for n in dirs if n not in excluded and not n.startswith('private-') and not(Path(current)/n).is_symlink() and not(n=='family' and directory.name in overlays)]
                for name in names:
                    path=Path(current)/name
                    if path.suffix in ['.jar','.class','.whl','.pyc'] or path.is_symlink() or not path.is_file():continue
                    add(path.relative_to(ROOT).as_posix(),path)
        for directory in overlays:
            report=json.loads((base/directory/'COMPLETED.json').read_bytes());assert report['status'].startswith('PASS_')
            for case in report['checks']:
                for name,want in case['fixture_overlay'].items():path=base/directory/case['mutation']/'family'/name;assert pin(path)==want;add(path.relative_to(ROOT).as_posix(),path)
        previous=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-simpleppt-math-1/selection.json').read_bytes());held={n:pin(ROOT/n) for n in previous['preserved']};assert held==previous['preserved'] and len(held)==18;add('preserved-unrelated-worktree-pins.json',raw=(json.dumps(held,indent=2)+'\n').encode());canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==build['canonical_executable_unchanged'];parent_names=['docs/literature/simpleppt-core-development/VALIDATION.json',*['docs/literature/simpleppt-core-development/'+p['name'] for p in core['archive_parts']]];parents={n:pin(ROOT/n) for n in parent_names}
        # Every distinct new byte string is assigned once, leaving room for ZIP metadata.
        buckets=[];bucket={};seen={};size=0
        for name,raw in sorted(own.items()):
            digest=byte_pin(raw)['sha256']
            if digest in seen:buckets[seen[digest]][name]=raw;continue
            if not bucket or size+len(raw)>85000000:bucket={};buckets.append(bucket);size=0
            assert len(raw)<=85000000;bucket[name]=raw;seen[digest]=len(buckets)-1;size+=len(raw)
        candidate=out/'candidate';candidate.mkdir();(candidate/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');parts=[]
        for i,members in enumerate(buckets):
            path=candidate/('controls-'+str(i+1).zfill(3)+'.zip');packing=pack(path,members);assert path.stat().st_size<95000000;parts.append(dict(name=path.name,pin=pin(path),packing=packing));print('Packed',path.name,len(members),'logical files',path.stat().st_size,'bytes',flush=True)
        proof=dict(schema_version=1,status='passed-original-scfates-coordinate-workflow-and-native-equation-audits-only',artifact=build['artifact'],parent_artifact=build['parent_artifact'],unchanged_parent_entries=21253,source_pins=pins,parent_proofs=parents,archive_parts=parts,parent_references=references,logical_files=len(logical),logical_file_pins=logical,canonical_executable_unchanged=canonical,full_scfates_coordinate_adapter_admitted=True,implementation_credit=True,whole_package_implemented=False,independent_trajectory_refit=False,new_catalog_credit=False,current_catalog_integrations=86,including_existing_adapters=89,missing_cataloged_tools=255,admitted_original_complete_workflows=11,independent_frozen_original_array_states=83789804,native_workflow_numeric_states_per_jvm=27995425,native_workflow_identity_states_per_jvm=9089806,native_tsv_states_per_jvm=22200,actual_jvms=['windows21','windows25','linux21'],compiled_tree_cases=31,exact_compiled_tree_states_per_jvm=21593,corrupt_observation_families_rejected=15,invalid_inputs_rejected=11,exact_gzip_fixture_passed=True,export_permission_connected_skip_contracts=7,late_source_errors_rejected=2,earlier_completed_cases_left_provisional=3,nonconvergence_policy_rejected=True,actual_timeout_output_and_cancellation_controls=3,documented_example_passed=True,whole_paper_reproduced=False,whole_goal_complete=False)
        save(candidate/'VALIDATION.json',proof)
        from scfates_workflow_evidence import verify
        verify(folder=candidate);assert all(pin(ROOT/n)==want for n,want in pins.items()) and all(pin(ROOT/n)==want for n,want in held.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;final.mkdir()
        for path in candidate.iterdir():assert path.is_file();shutil.copyfile(path,final/path.name)
        verify();state.update(status='PASS_SEALED_COMPLETE_SCFATES_COORDINATE_ADAPTER_ONLY',logical_files=len(logical),parent_references=len(references),new_archive_parts=len(parts),validation=pin(final/'VALIDATION.json'),implementation_credit=True,new_catalog_credit=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/adapter sealing gates')
    raise SystemExit(main())
