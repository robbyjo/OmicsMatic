# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal separately validated corrected startup, preserving original proofs and failed attempts."""
import json,os
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from prepare_cellphonedb_hash_seed import verify as verify_derivation
from prepare_cellphonedb_hash_seed_validators import verify as verify_validators

def main():
    verify_derivation();verify_validators();base=ROOT/'build/literature-review/cellphonedb-runtime';out=ROOT/'docs/literature/cellphonedb-hash-seed-development';assert not out.exists();built=json.loads((base/'hash-seed-build-3/COMPLETED.json').read_bytes());assert built['status']=='PASS_COMPILED_CELLPHONEDB_HASH_SEED_DEVELOPMENT_ONLY' and built['unchanged_parent_entries']==21068;assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items());canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==built['canonical_executable_unchanged'];sources=set(built['source_pins']);sources.update('tools/literature/'+n for n in ['prepare_cellphonedb_hash_seed_validators.py','collect_cellphonedb_hash_seed_controls_linux.py','validate_cellphonedb_hash_seed.py','validate_cellphonedb_hash_seed_linux.py','validate_cellphonedb_hash_seed_windows.py','validate_cellphonedb_hash_seed_protocols_linux.py','validate_cellphonedb_hash_seed_startup_linux.py','validate_cellphonedb_hash_seed_request.py','quarantine_cellphonedb_startup_cache_linux.py','cellphonedb_hash_seed_evidence.py','seal_cellphonedb_hash_seed.py','cellphonedb_direct_reference_worker.py','compare_cellphonedb_safe_worker.py','validate_cellphonedb_seeded_sampling.py','inventory_hail_runtime_linux.py','setup_hail_reference_linux.py','setup_deseq2_reference.py','validate_mashr_native.py','run_cellphonedb_catalog_checks.py']);sources.add('docs/literature/CELLPHONEDB_HASH_SEED.md');pins={n:pin(ROOT/n) for n in sorted(sources)};files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins};excluded={'private-cache','private-pycache','private-temp','private-home','private-config','classes','__pycache__','quarantined-cache'}
    for directory in sorted(base.glob('hash-seed-*')):
        if not directory.is_dir():continue
        for current,dirs,names in os.walk(directory,topdown=True,followlinks=False):
            dirs[:]=[n for n in dirs if n not in excluded and not(Path(current)/n).is_symlink()]
            for name in names:
                path=Path(current)/name
                if path.suffix in ['.jar','.class','.pyc'] or path.is_symlink() or not path.is_file():continue
                files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    # Every admitted public manifest member is included, even if a private-cache exclusion would otherwise omit it.
    packaged=json.loads((base/'hash-seed-packaged-2/COMPLETED.json').read_bytes());assert packaged['status']=='PASS_EIGHT_PACKAGED_CELLPHONEDB_HASH_SEED_CONTROLS_ONLY';public_count=0
    for process in packaged['processes']:
        if process['exit_code']!=0:continue
        home=base/'hash-seed-packaged-2'/process['name']/'analysis';manifest=json.loads((home/'manifest.json').read_bytes())
        for name,want in manifest['files'].items():path=home/name;assert path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(home.resolve()) and pin(path)==want;files[path.relative_to(ROOT).as_posix()]=path.read_bytes();public_count+=1
    parents={n:pin(ROOT/n) for n in ['docs/literature/spyrrow-communication-development/VALIDATION.json','docs/literature/spyrrow-communication-development/controls.zip','docs/literature/cellphonedb-workflow-development/VALIDATION.json','docs/literature/cellphonedb-workflow-development/controls.zip','docs/literature/omics-2023-2026/cellphonedb-catalog-validation.json','docs/literature/omics-2023-2026/cellphonedb-catalog-support.zip','docs/literature/PYTHON_HASH_SEED_KNOWN_ISSUE.md']};held_before=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-spyrrow-communication-1/selection.json').read_bytes())['preserved'];held={n:pin(ROOT/n) for n in held_before};assert held==held_before and len(held)==18;files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode();out.mkdir();(out/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');archive=out/'controls.zip';packing=pack(archive,files);assert archive.stat().st_size<95000000;save(out/'VALIDATION.json',dict(schema_version=1,status='passed-additive-CellPhoneDB-effective-python-startup-runner-only',runner_revision=2,artifact=built['artifact'],parent_artifact=built['parent_artifact'],unchanged_parent_entries=21068,source_pins=pins,parent_proofs=parents,archive=pin(archive),canonical_executable_unchanged=canonical,fresh_original_cases=19,complete_serial_returned_TSV_RNG_comparisons=17,actual_jvms=['windows21','windows25','linux21'],tested_python_hash_seeds=[0,123,4294967295],receipt_corruptions_per_seed_per_jvm=22,public_command_controls=8,public_manifest_files_verified=public_count,producer_protocol_controls=12,export_platform_setup_controls=6,invalid_request_seed_controls=7,startup_modes_rejected=8,prior_scientific_gates=19,first_generation_runs_relabelled=False,original_scientific_sources_or_verifiers_changed=False,shared_runtime_restored_without_receipt_refresh=True,negative_control_cache_incident_preserved=True,new_catalog_integration_credit=False,current_catalog_integrations=85,including_existing_adapters=88,missing_cataloged_tools=251,whole_paper_reproduced=False,whole_goal_complete=False,**packing));assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in held.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    from cellphonedb_hash_seed_evidence import verify
    verify();print('Sealed',len(files),'logical files;',archive.stat().st_size,'bytes; first-generation proof/catalog unchanged')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable corrected-startup evidence gates')
    main()
