# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal a scoped development execution/audit checkpoint; no catalog credit or whole-goal completion."""
import argparse,json,os,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out==ROOT/'docs/literature/cellphonedb-workflow-development' and not out.exists();base=ROOT/'build/literature-review/cellphonedb-runtime';build=json.loads((base/'workflow-build-6/COMPLETED.json').read_bytes());assert build['status']=='PASS_COMPILED_CELLPHONEDB_WORKFLOW_DEVELOPMENT_ONLY';jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==build['canonical_executable_unchanged'];assert all(pin(ROOT/n)==want for n,want in build['source_pins'].items())
    expected={
      'safe-worker-controls-3':'PASS_PAIRED_SAFE_CELLPHONEDB_WORKER_CONTROLS_ONLY',
      'workflow-native-audit-6':'PASS_THREE_JVM_NATIVE_CELLPHONEDB_WORKFLOW_CONTROL_AUDITS_ONLY',
      'workflow-audit-corruption-1':'PASS_SEVEN_COHERENT_CELLPHONEDB_AUDIT_CORRUPTION_CONTROLS_ONLY',
      'safe-protocol-controls-2':'PASS_TWELVE_SAFE_CELLPHONEDB_PROTOCOL_CONTROLS_ONLY',
      'workflow-packaged-validation-2':'PASS_SIX_PACKAGED_CELLPHONEDB_WORKFLOW_CONTROLS_ONLY',
      'workflow-windows-protocols-2':'PASS_SIX_CELLPHONEDB_EXPORT_PLATFORM_SETUP_PROTOCOLS_ONLY'}
    reports={}
    for name,status in expected.items():report=json.loads((base/name/'COMPLETED.json').read_bytes());assert report['status']==status,(name,report.get('error'));reports[name]=report
    safe=reports['safe-worker-controls-3'];assert safe['cases']==19 and safe['serial_complete_byte_and_rng_comparisons']==17 and safe['runtime_unchanged'] and safe['source_inputs_unchanged'] and safe['pickle_deserialized'] is False;native=reports['workflow-native-audit-6'];assert len(native['processes'])==3 and all(len(p['audit']['cases'])==19 for p in native['processes']);assert len(reports['workflow-audit-corruption-1']['checks'])==7 and len(reports['safe-protocol-controls-2']['protocols'])==12 and len(reports['workflow-packaged-validation-2']['processes'])==6 and len(reports['workflow-windows-protocols-2']['processes'])==6
    for name in ['safe-protocol-controls-2','workflow-packaged-validation-2']:assert reports[name]['runtime_unchanged'] and reports[name]['source_inputs_unchanged']
    setup=ROOT/'build/cellphonedb-workflow-case-sensitive-1/setup-1';installed=json.loads((setup/'COMPLETED.json').read_bytes());assert installed['status']=='PASS_BOUND_ORIGINAL_CELLPHONEDB_WORKFLOW_RUNTIME_ONLY' and installed['runtime_trees_unchanged'] and installed['shared_inputs_unchanged'];kit=ROOT/'src/main/resources/org/jlinalg/omics/cellphonedb-workflow';profile=json.loads((kit/'profiles.json').read_bytes());worker=json.loads((kit/'worker-profile.json').read_bytes());assert all(pin(kit/n)==want for n,want in {**profile['files'],**worker['files']}.items())
    roots=[base/name for name in expected];roots += [base/name for name in ['workflow-seal-correction-1','workflow-packaged-validation-1','packaged-protocol-correction-1','safe-worker-controls-1','safe-worker-controls-2','safe-protocol-controls-1','safe-protocol-correction-1','workflow-native-audit-1','workflow-native-audit-2','workflow-native-audit-3','workflow-native-audit-4','workflow-native-audit-5','workflow-windows-protocols-1',*[f'workflow-build-{i}' for i in range(1,7)]]];roots.append(setup);roots.append(base/'java-version-controls-1');versions=json.loads((base/'java-version-controls-1/COMPLETED.json').read_bytes());assert versions['status']=='PASS_THREE_ACTUAL_JVM_VERSIONS_ONLY' and [v['major'] for v in versions['versions']]==[21,25,21]
    source_paths=set(build['source_pins']);source_paths.update(p.relative_to(ROOT).as_posix() for p in kit.rglob('*') if p.is_file());source_paths.update('tools/literature/'+name for name in ['cellphonedb_direct_reference_worker.py','collect_cellphonedb_safe_worker_controls_linux.py','compare_cellphonedb_safe_worker.py','prepare_cellphonedb_workflow_profile.py','validate_cellphonedb_seeded_sampling.py','validate_cellphonedb_safe_protocols_linux.py','validate_cellphonedb_workflow_audit.py','validate_cellphonedb_workflow_linux.py','validate_cellphonedb_workflow_windows.py','test_cellphonedb_workflow_audit.py','seal_cellphonedb_workflow.py','cellphonedb_workflow_evidence.py','inventory_hail_runtime_linux.py','setup_hail_reference_linux.py','setup_deseq2_reference.py','validate_mashr_native.py']);source_paths.update(['docs/literature/CELLPHONEDB_WORKFLOW_DEVELOPMENT.md','docs/literature/cellphonedb-workflow-example.json']);source_pins={n:pin(ROOT/n) for n in sorted(source_paths)};members={}
    for root in roots:
        assert root.is_dir()
        excluded={'archives','wheels','private-cache','private-temp','private-home','private-config','classes','__pycache__'}
        for current,directories,files in os.walk(root,topdown=True,followlinks=False):
            directories[:]=[name for name in directories if name not in excluded and not(Path(current)/name).is_symlink()]
            for name in files:
                p=Path(current)/name
                if p.suffix in ['.jar','.class','.pyc'] or p.is_symlink() or not p.is_file():continue
                members[p.relative_to(ROOT).as_posix()]=p
    for n in source_paths:members['bound-source/'+n]=ROOT/n
    out.mkdir();(out/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');archive=out/'controls.zip';manifest={n:pin(p) for n,p in sorted(members.items())}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for n,p in sorted(members.items()):z.writestr(n,p.read_bytes())
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None and len(z.namelist())==len(manifest)
    assert all(pin(ROOT/n)==want for n,want in source_pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;save(out/'VALIDATION.json',dict(status='passed-scoped-original-CellPhoneDB-execution-and-native-audits-development-only',implementation_credit=False,catalog_applied=False,whole_goal_complete=False,artifact=build['artifact'],parent_artifact=build['parent_artifact'],unchanged_parent_entries=build['unchanged_parent_entries'],canonical_executable_unchanged=canonical,source_pins=source_pins,controls=dict(actual_java_versions=versions['versions'],paired_cases=19,deterministic_serial_complete_bytes_and_rng=17,entropy_selection_cases=1,seeded_original_sampling_kernel=1,parallel_actual_null_case=1,native_jvms=[dict(label=p['label'],cases=len(p['audit']['cases']),operations=p['audit']['operations'],numeric_cells=p['audit']['numeric_cells'],identity_states=p['audit']['identity_states'],tsv_cells=p['audit']['tsv_cells']) for p in native['processes']],coherent_corruptions=7,producer_protocols=12,public_command_protocols=6,export_platform_setup_protocols=6),archive=dict(path=archive.relative_to(ROOT).as_posix(),**pin(archive)),archive_members=manifest,public_curated_database_included=False,pickle_deserialized=False,independent_random_generator=False,upstream_scoring_cellsign_geometry=True,current_catalog=dict(papers=250,new_scoped_integrations=84,including_existing_adapters=87,missing=247),required_action='Read CELLPHONEDB_WORKFLOW_DEVELOPMENT.md, exported SETUP.md and every run WARNINGS.md. SKIP3 skips dependent analysis; FAIL2 admits no case from the whole batch. Catalog and source-role/registry application remain unfinished.'))
    print('PASS_SEALED_CELLPHONEDB_WORKFLOW_DEVELOPMENT_ONLY',len(manifest),'members',pin(archive))


if __name__=='__main__':main()
