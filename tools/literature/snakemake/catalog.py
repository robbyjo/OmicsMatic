#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit/recheck one source-bound Snakemake family only after its actual public-command controls."""
from pathlib import Path
import argparse,ast,concurrent.futures,hashlib,json,shutil,subprocess,sys,traceback,zipfile
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from snakemake.primary import ROOT,audit
from snakemake.model import META,GUIDE,registry,transition,check,encoded
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta
if not __debug__:raise RuntimeError('Scientific/catalog assertions must remain enabled')
BASE='bb651d2beba614d129cba9708e81adf715e73a64'
EVIDENCE='docs/literature/snakemake-catalog-integration'
PROOF='docs/literature/omics-2023-2026/snakemake-catalog-validation.json'
COMPATIBILITY='docs/literature/omics-2023-2026/snakemake-reviews-shared-source-compatibility.json'
STATUS='PASS_SCOPED_ORIGINAL_SNAKEMAKE_AND_NATIVE_BENCHMARK_CATALOG_ADMISSION'
ACTION='Build current source. Missing/forbidden prerequisites mean SKIP3 and dependent analysis skipped. Other failures mean FAIL2 and admit no complete result. Retain inputs, raw outputs, source diagnostics, invalidated markers and failed attempts; resolve the named cause and retry NEW paths. No edition/method substitution, installed-Conda-prefix relocation or historical fingerprint refresh. Selected local software is not an author environment or whole-paper reproduction. The full 250-paper implementation goal remains active.'
RECORDS={
 'build_validation':'public-build-records-1/VALIDATION.json',
 'build_package':'public-build-records-1/PACKAGE-REVIEW.json',
 'preinstalled':'public-preinstalled-controls-1/COMPLETED.json',
 'preinstalled_start':'public-preinstalled-controls-1/STARTED.json',
 'preinstalled_replay':'public-preinstalled-replay-1/COMPLETED.json',
 'managed':'public-managed-controls-1/NATIVE-COMPLETED.json',
 'managed_review':'public-managed-native-review-1/COMPLETED.json',
 'managed_archive':'public-managed-capture-archive-1/ARCHIVED.json',
 'archive_review':'public-managed-capture-archive-review-1/COMPLETED.json',
 'portable_damage':'public-managed-portable-1/COMPLETED.json',
 'portable_matrix':'public-managed-portable-matrix-1/COMPLETED.json',
 'copied_reader':'public-reader-1/COMPLETED.json',
 'terminal_failures':'public-failures-1/COMPLETED.json',
 'example':'public-example-2/COMPLETED.json',
 'managed_replay':'public-managed-replay-1/COMPLETED.json',
}
def pin(path):
    sha=hashlib.sha256();size=0
    with Path(path).open('rb') as stream:
        for raw in iter(lambda:stream.read(1048576),b''):size+=len(raw);sha.update(raw)
    return dict(bytes=size,sha256=sha.hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def write(path,value):
    with Path(path).open('xb') as stream:stream.write(encoded(value))
def git(*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=ROOT)
def owned(name):
    return name in [PROOF,COMPATIBILITY] or name.startswith(('tools/literature/snakemake/','tools/literature/snakemake_original/','examples/snakemake-workflow/',EVIDENCE+'/')) or name.startswith('docs/literature/SNAKEMAKE_')
def own_sources():
    names={row.decode() for row in git('ls-files','--cached','--others','--exclude-standard','-z').split(b'\0') if row}
    return sorted(name for name in names if owned(name) and name not in [PROOF,COMPATIBILITY] and not name.startswith(EVIDENCE+'/'))
def source_inventory():
    baseline={}
    for row in git('ls-tree','-rz','--full-tree',BASE).split(b'\0'):
        if not row:continue
        header,name=row.split(b'\t',1);mode,kind,oid=header.decode().split();name=name.decode();assert kind=='blob' and mode in ['100644','100755']
        if name not in META and not owned(name):baseline[name]=dict(mode=mode,git_object=oid)
    actual={row.decode() for row in git('ls-files','--cached','--others','--exclude-standard','-z').split(b'\0') if row};assert {n for n in actual if n not in META and not owned(n)}==set(baseline)
    def inspect(entry):
        name,row=entry
        path=ROOT/name;assert path.is_file() and not path.is_symlink();raw=path.read_bytes();candidates=[raw]+([raw.replace(b'\r\n',b'\n')] if b'\0' not in raw else []);assert any(hashlib.sha1(b'blob '+str(len(value)).encode()+b'\0'+value).hexdigest()==row['git_object'] for value in candidates),name
        return name,dict(**row,**rawpin(raw))
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:result=dict(pool.map(inspect,baseline.items()))
    return dict(baseline_commit=BASE,existing_non_catalog_files_checked=len(result),files=result,non_snakemake_scientific_sources_unchanged=True,all_java_sources_unchanged_from_qualified_public_build=True,old_verifiers_or_Git_overlaid=False)
def warning_only_example_change(before,current):
    def normalized(raw):
        tree=ast.parse(raw);found=[]
        for node in tree.body:
            if isinstance(node,ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0],ast.Name) and node.targets[0].id=='WARNING':
                assert isinstance(node.value,ast.Constant) and isinstance(node.value.value,str);found.append(node);node.value=ast.Constant(value='<retained user-facing warning>')
        assert len(found)==1;return ast.dump(tree,include_attributes=False)
    assert normalized(before)==normalized(current),'Example generation changed beyond its standalone warning text'
    return dict(only_standalone_warning_literal_changed=before!=current,qualified_source=rawpin(before),current_source=rawpin(current),workflow_data_recipe_generation_unchanged=True,historical_receipts_refreshed=False)
def qualification(folder,jar):
    folder=Path(folder);jar=Path(jar);jar_pin=pin(jar);assert jar_pin==dict(bytes=193492199,sha256='d4b04417edc0f39844edde1475a269a3dc07918c32e09ee3433e6f9440aa6d63')
    records={key:json.loads((folder/name).read_bytes()) for key,name in RECORDS.items()}
    for key,value in records.items():
        if key not in ['preinstalled_start','managed_archive']:assert value['status'].startswith('PASS_'),key
    build=records['build_validation'];assert build['jar']==jar_pin and build['tests']==dict(tests=331,failures=0,errors=0,skipped=0) and build['python_tests']==22 and records['build_package']['jar']==jar_pin
    publication=json.loads((folder/'public-build-records-1/PUBLISHED.json').read_bytes());assert publication['head']==BASE and publication['website_check_passed'] and publication['java_tests']==build['tests']
    for name,value in json.loads((folder/'public-build-records-1/SOURCE-FINAL.json').read_bytes()).items():assert rawpin(git('show',BASE+':'+name))==value,name
    pre=records['preinstalled'];assert pre['public_cli'] and len(pre['cases'])==12 and all(row['exit_code']==0 for row in pre['processes']) and records['preinstalled_start']['public_jar']==jar_pin
    for key,size in [('preinstalled_replay',2),('managed_replay',12)]:
        record=records[key];assert record['jar']==jar_pin and len(record['cases'])==size and record['original_callers_restored'] and record['original_software_unchanged'];assert all(row['process']['exit_code']==0 and row['supplied_as_zip']==(row['generation']==2) for row in record['cases'])
    negative=records['managed_replay']['negative_controls'];assert len(negative)==3 and sorted(row['expected_exit'] for row in negative)==[2,2,3] and all(row['process']['exit_code']==row['expected_exit'] for row in negative)
    managed=records['managed'];assert managed['public_cli'] and managed['jar']==jar_pin and len(managed['cases'])==21 and managed['original_software_unchanged']
    for row in managed['cases']:assert row['source_process']['exit_code']==(0 if row['expected_success'] else 3 if row['scenario'] in ['missing','profile-skip'] else 2)
    review=records['managed_review'];assert (review['positive'],review['skipped'],review['failed'])==(9,6,6) and review['source_record']==pin(folder/RECORDS['managed']) and review['original_software_matches_reference']
    archive=folder/'public-managed-capture-archive-1/managed-capture-evidence.zip';assert pin(archive)==records['managed_archive']['archive']==records['archive_review']['archive'];assert records['archive_review']['managed_bundles_checked']==9 and records['archive_review']['unique_blobs']==review['unique_case_blobs']
    for key,count in [('portable_damage',25),('portable_matrix',6)]:
        record=records[key];assert record['jar']==jar_pin and len(record['cases'])==count and record['path_empty'] and not record['engine_executed'] and not record['producer_paths_available']
    assert all(row['exit_code']==(0 if row['expected_acceptance'] else 2) for row in records['portable_damage']['cases']);assert all(row['exit_code']==0 for row in records['portable_matrix']['cases']);assert records['portable_matrix']['source_archive']==records['managed_archive']['archive']
    reader=records['copied_reader'];assert reader['jar']==jar_pin and reader['different_working_directory'] and reader['path_empty'] and not reader['engine_executed'] and not reader['producer_paths_available'] and reader['source_jar_unchanged'] and reader['source_archive_unchanged']
    failures=records['terminal_failures'];assert failures['jar']==jar_pin and len(failures['cases'])==15 and all(row['exit_code']==2 for row in failures['cases']) and failures['source_inputs_unchanged']
    example=records['example'];assert example['jar']==jar_pin and len(example['cases'])==4 and {row['mode'] for row in example['cases']}=={'preinstalled','conda','apptainer','conda-apptainer'} and all(row['run']['exit_code']==row['verify']['exit_code']==0 for row in example['cases'])
    old_example=(folder/'public-example-2/as-run-example.py').read_bytes();assert rawpin(old_example)==example['example_source'];warning=warning_only_example_change(old_example,(ROOT/'examples/snakemake-workflow/prepare.py').read_bytes())
    return dict(status='PASS_SCOPED_PUBLIC_ORIGINAL_ENGINE_BUNDLES_REPLAY_PORTABLE_REJECTION_AND_EXAMPLE',jar=jar_pin,records={key:dict(path=name,pin=pin(folder/name)) for key,name in RECORDS.items()},recorded_java_tests=331,recorded_linux_python_tests=22,preinstalled_controls=12,managed_controls=21,genuine_replay_generations=14,negative_replay_controls=3,portable_damage_controls=25,portable_mode_permission_controls=6,terminal_failure_controls=15,example_deployment_modes=4,example_warning_compatibility=warning,complete_vendor_feature_or_author_pipeline_qualification=False,whole_paper_reproduced=False)
def compatibility(before,after):
    changes=[delta(name,before[name],after[name]) for name in META]
    assert all(reverse_delta(after[row['path']],row)==before[row['path']] for row in changes)
    return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='snakemake',scope='Exact inverse of the Snakemake-only eight-file metadata transition. No scientific-source, old-verifier or Git overlay.',paths=changes)
def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);mode=parser.add_mutually_exclusive_group(required=True);mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true');parser.add_argument('--primary-cache',type=Path,required=True);parser.add_argument('--qualification-root',type=Path,required=True);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=None;before=after=None;metadata_started=False;proof_written=False
    try:
        target=args.out.absolute();assert target.resolve().is_relative_to(ROOT/'build');target.mkdir(parents=True,exist_ok=False);out=target;write(out/'STARTED.json',dict(required_action=ACTION,applying=args.apply));sources={name:pin(ROOT/name) for name in own_sources()};source=source_inventory();science=qualification(args.qualification_root,args.jar)
        if args.apply:
            assert not (ROOT/EVIDENCE).exists() and not (ROOT/PROOF).exists() and not (ROOT/COMPATIBILITY).exists();before={name:(ROOT/name).read_bytes() for name in META};previous=json.loads((ROOT/'docs/literature/omics-2023-2026/pops-catalog-validation.json').read_bytes());assert {name:rawpin(raw) for name,raw in before.items()}==previous['after'],'Predecessor metadata changed; do not overlay it'
        else:
            proof=json.loads((ROOT/PROOF).read_bytes());assert proof['status']==STATUS and proof['sources']==sources
            for name,value in proof['evidence'].items():assert pin(ROOT/name)==value,name
            with zipfile.ZipFile(ROOT/EVIDENCE/'catalog-before.zip') as saved:assert len(saved.namelist())==8 and set(saved.namelist())==set(META);before={name:saved.read(name) for name in META}
            assert source==json.loads((ROOT/EVIDENCE/'SOURCE-TREE.json').read_bytes()) and science==json.loads((ROOT/EVIDENCE/'QUALIFICATION.json').read_bytes())
        primary=audit(args.primary_cache,json.loads(before[META[0]]),json.loads(before[META[1]]));after=transition(before,primary);result=check(before,after,primary);journal=compatibility(before,after);assert len(registry())==485
        if args.apply:
            assert all((ROOT/name).read_bytes()==raw for name,raw in before.items());evidence=ROOT/EVIDENCE;evidence.mkdir();(evidence/'.gitattributes').write_bytes(b'* -text\n')
            with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as saved:
                for name,raw in before.items():saved.writestr(name,raw)
            for name,value in [('SOURCE-TREE.json',source),('QUALIFICATION.json',science),('CENSUS.json',primary),('REGISTRY.json',registry())]:write(evidence/name,value)
            (evidence/'WARNINGS.md').write_text('# Scope and required actions\n\n'+ACTION+'\n',encoding='utf-8')
            for key,name in RECORDS.items():shutil.copyfile(args.qualification_root/name,evidence/(key+'.json'))
            shutil.copyfile(args.qualification_root/'public-example-2/as-run-example.py',evidence/'qualified-example-before-warning-update.py');write(ROOT/COMPATIBILITY,journal)
            metadata_started=True
            for name,raw in after.items():(ROOT/name).write_bytes(raw)
            assert source_inventory()==source and sources=={name:pin(ROOT/name) for name in own_sources()}
            files={path.relative_to(ROOT).as_posix():pin(path) for path in evidence.rglob('*') if path.is_file()};files[COMPATIBILITY]=pin(ROOT/COMPATIBILITY)
            proof=dict(status=STATUS,**result,sources=sources,evidence=files,before={name:rawpin(raw) for name,raw in before.items()},after={name:pin(ROOT/name) for name in META},qualification=science,unrelated_metadata_preserved=True,non_snakemake_scientific_sources_unchanged=True,predecessor_catalog_metadata_preserved_exactly=True,old_scientific_executions_freshly_repeated=False,old_verifiers_or_Git_overlaid=False,source_build_required=True,released_binary_contains_workflow_adapter=False,whole_paper_reproduced=False);write(ROOT/PROOF,proof);proof_written=True
        else:
            assert all((ROOT/name).read_bytes()==raw for name,raw in after.items());assert proof['after']=={name:pin(ROOT/name) for name in META};assert journal==json.loads((ROOT/COMPATIBILITY).read_bytes());assert primary==json.loads((ROOT/EVIDENCE/'CENSUS.json').read_bytes()) and registry()==json.loads((ROOT/EVIDENCE/'REGISTRY.json').read_bytes())
        write(out/'COMPLETED.json',dict(status=STATUS,**result,qualification=science,current_source_guard_passed=True));print(STATUS,flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        rollback=[]
        if args.apply and metadata_started:
            for name,original in before.items():
                try:
                    current=(ROOT/name).read_bytes()
                    if current==after[name]:(ROOT/name).write_bytes(original);rollback.append(dict(path=name,status='restored-original-from-retained-snapshot'))
                    elif current==original:rollback.append(dict(path=name,status='original-already-retained'))
                    else:rollback.append(dict(path=name,status='concurrent-content-preserved-manual-review-required'))
                except Exception as failed:rollback.append(dict(path=name,status='restore-failed-manual-review-required',error=repr(failed)))
            if proof_written:
                try:
                    if (ROOT/PROOF).read_bytes()==encoded(proof):(ROOT/PROOF).rename(ROOT/EVIDENCE/'INVALIDATED-CATALOG-PROOF.json')
                    else:rollback.append(dict(path=PROOF,status='concurrent-proof-preserved-manual-review-required'))
                except Exception as failed:rollback.append(dict(path=PROOF,status='proof-invalidation-failed-manual-review-required',error=repr(failed)))
        if out is not None:write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),guarded_metadata_rollback=rollback,required_action=ACTION+' Review any rollback entry requiring manual action before retrying; failed evidence remains retained.'))
        print(('SKIP3' if code==3 else 'FAIL2')+'_SNAKEMAKE_CATALOG_NOT_ADMITTED',repr(error),ACTION);return code
if __name__=='__main__':raise SystemExit(main())
