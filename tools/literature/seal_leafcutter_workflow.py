# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal complete original/native LeafCutter families, private setup, immutable source bindings and all local failures; admit one scoped adapter."""
import json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin

BASE=ROOT/'build/literature-review/leafcutter-runtime'
DEST=ROOT/'docs/literature/leafcutter-workflow-development'


def main():
    assert not DEST.exists();build=json.loads((BASE/'workflow-build-3/COMPLETED.json').read_bytes());validation=json.loads((BASE/'workflow-validation-2/COMPLETED.json').read_bytes());assert validation['status']=='PASS_COMPLETE_PUBLIC_LEAFCUTTER_WORKFLOW_REFERENCE_AND_NATIVE_AUDITS_ONLY' and validation['artifact']==build['artifact'];jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};sources=dict(build['source_pins'])
    for name in ['collect_leafcutter_workflow_controls_linux.py','generate_leafcutter_workflow_fixtures.py','prepare_leafcutter_workflow_profile.py','prepare_leafcutter_workflow_archives_linux.py','validate_leafcutter_workflow.py','validate_leafcutter_workflow_protocols.py','validate_leafcutter_setup_filesystem.py','validate_leafcutter_installed_chain.py','audit_leafcutter_workflow_outputs.py','leafcutter_workflow_evidence.py','seal_leafcutter_workflow.py','audit_leafcutter_pca.py','validate_leafcutter_phenotypes.py','validate_leafcutter_models.py','signac_scan_evidence.py']:sources['tools/literature/'+name]=pin(ROOT/'tools/literature'/name)
    sources['tools/literature/validate_leafcutter_input_contracts.py']=pin(ROOT/'tools/literature/validate_leafcutter_input_contracts.py');sources['docs/literature/LEAFCUTTER_WORKFLOWS.md']=pin(ROOT/'docs/literature/LEAFCUTTER_WORKFLOWS.md');assert all(pin(ROOT/name)==want for name,want in sources.items());files={'current-source/'+name:(ROOT/name).read_bytes() for name in sources}
    def tree(directory,label,top=False):
        assert directory.is_dir(),directory
        for file in sorted(directory.iterdir() if top else directory.rglob('*')):
            relative=file.relative_to(directory)
            if file.is_file() and file.suffix!='.jar' and 'classes' not in relative.parts and '__pycache__' not in relative.parts:
                assert not file.is_symlink();name=label+'/'+relative.as_posix();assert name not in files;files[name]=file.read_bytes()
    trees=['workflow-build-1','workflow-build-2','workflow-build-3','workflow-exported-setup-1','workflow-exported-setup-2','workflow-runtime-capture-1','workflow-runtime-capture-2','workflow-runtime-capture-3','workflow-fixtures-1','workflow-fixtures-2','workflow-original-1','workflow-validation-1','workflow-validation-2','workflow-protocols-1','workflow-protocols-2','workflow-filesystem-skip-1','workflow-installed-validation-chain-1',*[f'workflow-output-audit-{i}' for i in range(1,5)],'workflow-install-launch-1']
    trees.extend(['workflow-input-contracts-1','workflow-seal-attempt-1'])
    for name in trees:tree(BASE/name,name)
    tree(BASE/'workflow-install-1','workflow-install-1',top=True)
    if (BASE/'workflow-install-1/kit-source').is_dir():tree(BASE/'workflow-install-1/kit-source','workflow-install-1/kit-source')
    # Cached archives and installed prefixes are intentionally separate from
    # this proof: locks, complete inventories, logs and actual science bind them.
    for path in sorted((BASE/'workflow-archives-1').iterdir()):
        if path.is_file() and path.suffix in ['.json','.md','.py']:files['workflow-archives-1/'+path.name]=path.read_bytes()
    setup=ROOT/'build/leafcutter-case-sensitive-1/setup-1';tree(setup,'installed-setup',top=True);tree(setup/'kit-source','installed-setup/kit-source');profile=json.loads((setup/'kit-source/profiles.json').read_bytes())
    for name,want in profile['files'].items():
        if name.startswith('original/leafcutter/'):
            member=name.removeprefix('original/leafcutter/');path=setup/'source/leafcutter'/member;assert pin(path)==want;files['installed-setup/original-source-stage/'+member]=path.read_bytes()
    files['installed-setup/CASE-SENSITIVITY.json']=(setup.parent/'CASE-SENSITIVITY.json').read_bytes();entries={}
    with zipfile.ZipFile(jar) as bundle:
        for name in build['added_entries']:raw=bundle.read(name);files['candidate-added-entries/'+name]=raw;entries[name]=byte_pin(raw)
    previous=json.loads((BASE/'workflow-build-2/COMPLETED.json').read_bytes());previous_jar=ROOT/previous['artifact']['path'];assert pin(previous_jar)=={k:previous['artifact'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(previous_jar) as before,zipfile.ZipFile(jar) as after:
        assert before.namelist()==after.namelist();changed={n:dict(before=byte_pin(before.read(n)),after=byte_pin(after.read(n))) for n in before.namelist() if before.read(n)!=after.read(n)};assert set(changed)=={'org/jlinalg/omics/leafcutter-workflow/profiles.json','org/jlinalg/omics/leafcutter-workflow/setup/prepare.py'};unchanged=len(before.namelist())-len(changed)
    files['setup-evolution.json']=(json.dumps(dict(status='PASS_EXACT_TWO_SETUP_MEMBER_EVOLUTION_ALL_ANALYSIS_BYTES_PRESERVED',before=previous['artifact'],after=build['artifact'],changed_entries=changed,unchanged_members=unchanged),indent=2)+'\n').encode()
    history=dict(status='all-local-failures-and-source-editions-retained-no-partial-adapter-admission',build_1='Java var compound-declaration syntax caused compilation failure. Build2 corrected Java syntax only and retained the failed source/log snapshot.',capture_1='The original completed runtime capture used the first whole-kit profile. Execution metadata was subsequently refined. Its exact old worker.R was reconstructed and retained only after bytes and SHA matched the saved profile; SNAPSHOT-RETENTION.json records this post-completion retention. Original binding/scientific results were not changed.',capture_2='Whole source/setup/worker profile was split into separately pinned runtime and execution-worker profiles. Prior kit and completed binding remain retained; neither old binding was relabeled current.',build_2='All8 actual public original/native cases and9 failure/skip protocols passed using existing exact dependency/library builds. Complete original reference TSVs match byte for byte.',installation_1='A fresh packaged install on case-insensitive NTFS reported collisions for distinct-case dependency files. Only the verified owned installer group was stopped; its partial prefix and all errors remain unadmitted. No shared cache or original source was repaired.',installation_2='New empty workspace parent was explicitly made case-sensitive. The packaged helper now checks actual output/cache and prefix-parent behavior before installation, preserving prior setup source/binding. A separate real skip control proves an unsuitable filesystem invokes no installer. New offline exact-cache installation and complete public science/protocol gates establish this runtime.',output_audit_1='Independent retained-output reader initially required every typed record field in the TSV, overlooking auxiliary R data-frame _row metadata. Original output/native code unchanged.',output_audit_2='Reader then assumed reported intron IDs equal original R row names. Original add_chr modifies the reported column while retaining raw row names. Reader3 checks this explicit mapping and full table columns; no original/scientific values, tolerances or cases were altered.',seal_1='The new retained reader expected the word corrupted in the historical installer log; its literal diagnostic is Invalid package cache / has incorrect size. Seal1 failed before creating a proof folder. Both exact reader/sealer sources and complete failed logs were retained before correction. Seal2 checks the actual diagnostic, the absence of those failures in the fresh install and the distinct-case installed header identities. All scientific code/results and tolerances remain unchanged.',scope='One exact current-source Linux original adapter plus separately validated native components. No historical release parity, fresh-run claim for unspecified paper steps, whole-paper reproduction, biological calibration, independent refit or canonical release/deployment claim.')
    files['history/CLASSIFICATION.json']=(json.dumps(history,indent=2)+'\n').encode();proof=dict(schema_version=1,status='passed-original-LeafCutter-workflows-and-private-setup-with-complete-audits',artifact=build['artifact'],source_pins=sources,packaged_entries=entries,canonical_executable_unchanged=build['canonical_executable_unchanged'],adapter_admitted=True,implementation_credit=True,admitted_named_tools=['leafcutter'],whole_goal_complete=False,whole_paper_reproduced=False,released_or_deployed=False)
    from leafcutter_workflow_evidence import verify_files,verify
    result=verify_files(proof,files);assert all(pin(ROOT/name)==want for name,want in sources.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==build['canonical_executable_unchanged'];unique={byte_pin(raw)['sha256']:raw for raw in files.values()};assert sum(map(len,unique.values()))<536870912
    DEST.mkdir();(DEST/'.gitattributes').write_bytes(b'* -text\n');packing=pack(DEST/'controls.zip',files);assert (DEST/'controls.zip').stat().st_size<95000000;proof.update(archive=pin(DEST/'controls.zip'),packing=packing,checks=result['checks']);save(DEST/'VALIDATION.json',proof);verify();print('Sealed',len(files),'logical files;',pin(DEST/'controls.zip')['bytes'],'bytes; one scoped LeafCutter adapter only')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete retained source/workflow checks')
    main()
