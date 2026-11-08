# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal lossless native/original evidence in globally deduplicated parts below GitHub file limits."""
import json,os,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin

def main():
    base=ROOT/'build/literature-review/scfates-runtime';out=ROOT/'docs/literature/simpleppt-core-development';assert not out.exists();build=json.loads((base/'math-build-6/COMPLETED.json').read_bytes());assert build['status']=='PASS_COMPILED_NATIVE_SIMPLEPPT_CPU_ARITHMETIC_ONLY';sources=set(build['source_pins']);sources.update('tools/literature/'+n for n in ['intake_scfates_source.py','review_scfates_source.py','prepare_scfates_reference_linux.py','collect_scfates_reference.py','run_scfates_reference_linux.py','validate_simpleppt_math.py','test_simpleppt_math_comparison.py','validate_simpleppt_math_example.py','validate_scfates_setup_permissions.py','simpleppt_math_evidence.py','seal_simpleppt_math.py','setup_deseq2_reference.py','setup_hail_reference_linux.py','inventory_hail_runtime_linux.py','validate_mashr_native.py','signac_scan_evidence.py']);sources.add('docs/literature/SIMPLEPPT_NATIVE_DEVELOPMENT.md');pins={n:pin(ROOT/n) for n in sorted(sources)};assert all(pins[n]==v for n,v in build['source_pins'].items());files={};blobs={}
    def add(name,path):
        assert name not in files;raw=path.read_bytes();digest=byte_pin(raw)['sha256']
        if digest in blobs:assert blobs[digest]==raw;raw=blobs[digest]
        else:blobs[digest]=raw
        files[name]=raw
    for name in pins:add('current-source/'+name,ROOT/name)
    excluded={'dependencies','wheels','classes','__pycache__','family'}
    for current,dirs,names in os.walk(base,topdown=True,followlinks=False):
        dirs[:]=[n for n in dirs if n not in excluded and not n.startswith('private-') and not(Path(current)/n).is_symlink()]
        for name in names:
            path=Path(current)/name
            if path.suffix in ['.jar','.class','.whl','.pyc'] or path.is_symlink() or not path.is_file():continue
            add(path.relative_to(ROOT).as_posix(),path)
    corruption=json.loads((base/'math-corruptions-1/COMPLETED.json').read_bytes());assert corruption['status']=='PASS_NINE_SIMPLEPPT_COMPARISON_CORRUPTIONS_REJECTED_ONLY'
    for case in corruption['checks']:
        for name,want in case['fixture_overlay'].items():path=base/'math-corruptions-1'/case['mutation']/'family'/name;assert pin(path)==want;add(path.relative_to(ROOT).as_posix(),path)
    parent_names=['docs/literature/palantir-workflow-development/VALIDATION.json','docs/literature/palantir-workflow-development/controls.zip','docs/literature/omics-2023-2026/palantir-catalog-validation.json','docs/literature/omics-2023-2026/palantir-catalog-support.zip'];parents={n:pin(ROOT/n) for n in parent_names};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==build['canonical_executable_unchanged'];old=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-palantir-catalog-1/selection.json').read_bytes())['preserved'];held={n:pin(ROOT/n) for n in old};assert held==old and len(held)==18;files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode()
    # Route every occurrence of identical bytes to the same part. Raw-byte bounds
    # leave space for ZIP metadata even for incompressible numeric arrays.
    buckets=[];bucket={};seen={};size=0
    for name,raw in sorted(files.items()):
        digest=byte_pin(raw)['sha256']
        if digest in seen:buckets[seen[digest]][name]=raw;continue
        if not bucket or size+len(raw)>85000000:
            bucket={};buckets.append(bucket);size=0
        assert len(raw)<=85000000;bucket[name]=raw;seen[digest]=len(buckets)-1;size+=len(raw)
    out.mkdir();(out/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');parts=[]
    for i,members in enumerate(buckets):
        path=out/('controls-'+str(i+1).zfill(3)+'.zip');packing=pack(path,members);assert path.stat().st_size<95000000;parts.append(dict(name=path.name,pin=pin(path),packing=packing));print('Packed',path.name,len(members),'logical files',path.stat().st_size,'bytes',flush=True)
    save(out/'VALIDATION.json',dict(schema_version=1,status='passed-native-SimplePPT-arithmetic-and-original-scfates-controls-only',artifact=build['artifact'],parent_artifact=build['parent_artifact'],unchanged_parent_entries=21164,source_pins=pins,parent_proofs=parents,archive_parts=parts,logical_files=len(files),logical_file_pins={n:byte_pin(v) for n,v in files.items()},canonical_executable_unchanged=canonical,actual_jvms=['windows21','windows25','linux21'],arithmetic_cases_per_jvm=40,actual_original_fit_observations_per_jvm=10,numeric_states_per_jvm=18203693,nonfinite_states_per_jvm=6,native_contract_rejections_per_jvm=14,correlation_comparator_rejections_per_jvm=5,whole_family_comparison_corruptions=9,setup_connected_skips=4,original_complete_workflows=9,original_expected_errors=3,official_dependency_archives=50,full_scfates_adapter_admitted=False,implementation_credit=False,current_catalog_integrations=86,including_existing_adapters=89,missing_cataloged_tools=255,whole_paper_reproduced=False,whole_goal_complete=False))
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in held.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    from simpleppt_math_evidence import verify
    verify();print('Sealed',len(files),'logical files in',len(parts),'parts;scFates adapter/catalog credit remain open')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact native/original source gates')
    main()
