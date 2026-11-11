# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal exact original/native whole-kernel controls, dependency/source provenance and explicit unresolved source/runner findings."""
import json,os
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack


def main():
    base=ROOT/'build/literature-review/spyrrow-runtime';out=ROOT/'docs/literature/spyrrow-communication-development';assert not out.exists();built=json.loads((base/'communication-build-1/COMPLETED.json').read_bytes());native=json.loads((base/'communication-native-validation-1/COMPLETED.json').read_bytes());original=json.loads((base/'communication-original-1/COMPLETED.json').read_bytes());assert built['status']=='PASS_COMPILED_SPYRROW_COMMUNICATION_COMPONENT_ONLY' and native['status']=='PASS_THREE_JVM_EXACT_SPYRROW_COMMUNICATION_COMPONENT_ONLY' and original['status']=='PASS_EXACT_SPYRROW_ORIGINAL_COMPONENT_REFERENCES_ONLY';assert all(pin(ROOT/n)==want for n,want in built['source_pins'].items());canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==built['canonical_executable_unchanged'];sources=set(built['source_pins']);sources.update('tools/literature/'+n for n in ['prepare_spyrrow_component_reference_linux.py','collect_spyrrow_communication_reference.py','run_spyrrow_communication_reference_linux.py','validate_spyrrow_communication.py','test_spyrrow_communication_comparison.py','audit_python_hash_seed_launch.py','spyrrow-use-review-expansion.json','spyrrow_communication_evidence.py','seal_spyrrow_communication.py','inventory_hail_runtime_linux.py','setup_hail_reference_linux.py','setup_deseq2_reference.py','validate_mashr_native.py']);sources.update(['docs/literature/SPYRROW_COMMUNICATION_DEVELOPMENT.md','docs/literature/PYTHON_HASH_SEED_KNOWN_ISSUE.md']);pins={n:pin(ROOT/n) for n in sorted(sources)};files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins};excluded={'site','wheels','private-cache','private-temp','private-home','private-config','classes','__pycache__'}
    for directory in sorted(base.iterdir()):
        if not directory.is_dir():continue
        for current,dirs,names in os.walk(directory,topdown=True,followlinks=False):
            dirs[:]=[name for name in dirs if name not in excluded and not(ROOT/current/name).is_symlink()]
            for name in names:
                from pathlib import Path
                path=Path(current)/name
                if path.suffix in ['.jar','.class','.whl','.pyc'] or path.is_symlink() or not path.is_file():continue
                files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    parents={n:pin(ROOT/n) for n in ['docs/literature/cellphonedb-workflow-development/VALIDATION.json','docs/literature/omics-2023-2026/cellphonedb-catalog-validation.json','docs/literature/omics-2023-2026/cellphonedb-catalog-support.zip']};held_before=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-cellphonedb-catalog-1/selection.json').read_bytes())['preserved'];held={n:pin(ROOT/n) for n in held_before};assert held==held_before and len(held)==18;files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode();out.mkdir();(out/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');archive=out/'controls.zip';packing=pack(archive,files);assert archive.stat().st_size<95000000;save(out/'VALIDATION.json',dict(schema_version=1,status='passed-exact-native-Spyrrow-communication-component-only',artifact=built['artifact'],parent_artifact=built['parent_artifact'],unchanged_parent_entries=21022,source_pins=pins,parent_proofs=parents,archive=pin(archive),canonical_executable_unchanged=canonical,cases=21,numeric_comparisons_per_jvm=333478,exact_identity_state_comparisons_per_jvm=100040,actual_jvms=['windows21','windows25','linux21'],native_rejections_per_jvm=14,comparison_corruptions=9,hash_cases=5008,set_groups=9,largest_source_set=85000,distance_cases=2006,original_source_errors=3,source_cn_import_gap='NameError: nam; namespace-parent diagnostic; no root initializer or pickle file execution',shared_runtime_and_overlay_unchanged=True,database_pickle_loaded=False,full_spyrrow_tool_admitted=False,implementation_credit=False,new_catalog_integration_credit=False,cellphonedb_python_hash_seed_defect_open=True,current_catalog_integrations=85,including_existing_adapters=88,missing_cataloged_tools=251,whole_paper_reproduced=False,whole_goal_complete=False,**packing));assert all(pin(ROOT/n)==want for n,want in pins.items()) and all(pin(ROOT/n)==want for n,want in held.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    from spyrrow_communication_evidence import verify
    verify();print('Sealed',len(files),'component evidence files;',archive.stat().st_size,'bytes; full-tool/hash-option followups remain open')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/component evidence checks')
    main()
