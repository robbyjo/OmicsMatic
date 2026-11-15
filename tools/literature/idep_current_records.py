#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze/read back current iDEP source/runtime/smoke records; no workflow credit."""
from pathlib import Path
import argparse,csv,hashlib,io,json,os,zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT.parent/'literature-review/monocle-runtime'
EVIDENCE=ROOT/'docs/literature/idep244-current-development'
GUIDE='docs/literature/IDEP_CURRENT_DEVELOPMENT.md'
COMMIT='60479c4375ea07bc930dc40b3c85230856f4ad50'
if not __debug__:raise RuntimeError('Do not disable evidence checks')

def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):
    with path.open('rb') as handle:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(handle,'sha256').hexdigest())
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')

def freeze():
    intake=BASE/'idep-current-source-intake-20261115-1'
    source=json.loads((intake/'CURRENT-SOURCE.json').read_bytes())
    assert source['commit']==COMMIT and source['DESCRIPTION_version_literal']=='2.4.4' and len(source['members'])==336
    setup=json.loads((BASE/'idep244-windows-runtime-setup-2/COMPLETED.json').read_bytes())
    assert setup['process_exit_code']==0 and setup['existing_selected_runtime_files_unchanged']
    smoke=json.loads((BASE/'idep244-original-smoke-4/COMPLETED.json').read_bytes())
    assert smoke['status']=='PASS_ACTUAL_IDEP244_ORIGINAL_COUNT_PIPELINE_SMOKE_NOT_WORKFLOW_QUALIFICATION'
    assert smoke['input_dimensions']==[200,9] and len(smoke['comparisons'])==3 and not smoke['analysis_admitted']
    EVIDENCE.mkdir(exist_ok=False);(EVIDENCE/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    private={};public={}
    roots=['idep-current-source-intake-20261115-1','idep244-windows-runtime-setup-1','idep244-windows-runtime-setup-2',
           'idep244-runtime-supplement-1']+['idep244-original-smoke-'+str(i) for i in [1,2,3,4]]
    for name in roots:
        folder=BASE/name
        for directory,dirs,files in os.walk(folder,followlinks=False):
            dirs[:]=[d for d in dirs if d not in ['R-lib','tmp','empty-user-library','empty-library']]
            for basename in files:
                path=Path(directory)/basename;relative=path.relative_to(BASE).as_posix();private[relative]=pin(path)
        # Public receipts contain metadata, complete consoles and project-owned producers, not original source/data.
        for path in folder.iterdir():
            if not path.is_file() or path.name in ['BASES-BEFORE.json','BASES-AFTER.json']:continue
            if path.suffix in ['.json','.txt','.tsv','.R','.py','.log','.stdout','.stderr']:
                public[name+'/'+path.name]=path
    record_index={}
    with zipfile.ZipFile(EVIDENCE/'complete-public-runtime-smoke-records.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,path in sorted(public.items()):
            raw=path.read_bytes();archive.writestr(name,raw);record_index[name]=rawpin(raw)
    write(EVIDENCE/'PUBLIC-RECORDS.json',record_index);write(EVIDENCE/'PRIVATE-RECORDS.json',private)
    proof=dict(status='PASS_CURRENT_IDEP244_SOURCE_PRIVATE_RUNTIME_AND_ORIGINAL_SMOKE_ONLY',source_commit=COMMIT,version='2.4.4',
        source_files={GUIDE:pin(ROOT/GUIDE),Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},
        evidence_files={p.name:pin(p) for p in EVIDENCE.iterdir() if p.is_file()},
        original_source_files=336,source_definition_checks=260,active_function_names=259,original_smoke_genes=200,original_smoke_samples=9,original_smoke_comparisons=3,
        complete_original_returns_and_RNG_retained_privately=True,original_source_algorithms_modified=False,
        original_conditions_retained=True,private_index_files=len(private),private_index_excludes_installed_R_libraries=True,
        installed_dependency_file_lock_complete=False,original_gene_ID_mapping_workflow_qualified=False,
        project_runner_qualified=False,project_saved_input_replay_qualified=False,catalog_credit_added=0,native_credit_added=0,
        whole_tool_qualified=False,paper_reproduced=False,whole_goal_complete=False)
    write(EVIDENCE/'QUALIFICATION.json',proof)

def verify(private_base=None):
    proof=json.loads((EVIDENCE/'QUALIFICATION.json').read_bytes())
    assert proof['status']=='PASS_CURRENT_IDEP244_SOURCE_PRIVATE_RUNTIME_AND_ORIGINAL_SMOKE_ONLY' and proof['source_commit']==COMMIT
    for kind,base in [('source_files',ROOT),('evidence_files',EVIDENCE)]:
        for name,identity in proof[kind].items():assert pin(base/name)==identity,(kind,name)
    with zipfile.ZipFile(EVIDENCE/'complete-public-runtime-smoke-records.zip') as archive:
        index=json.loads((EVIDENCE/'PUBLIC-RECORDS.json').read_bytes())
        assert len(archive.namelist())==len(set(archive.namelist()))==len(index) and set(archive.namelist())==set(index)
        for name,identity in index.items():assert rawpin(archive.read(name))==identity,name
        read=lambda name:json.loads(archive.read(name))
        source=read('idep-current-source-intake-20261115-1/CURRENT-SOURCE.json')
        assert source['commit']==COMMIT and source['DESCRIPTION_version_literal']=='2.4.4' and len(source['members'])==336
        before=read('idep244-windows-runtime-setup-1/BASE-PRESERVATION.json')
        assert before['all_existing_library_file_bytes_unchanged'] and before['files']==30438
        setup=read('idep244-windows-runtime-setup-2/COMPLETED.json')
        assert setup['process_exit_code']==0 and setup['existing_selected_runtime_files_unchanged']
        smoke=read('idep244-original-smoke-4/COMPLETED.json')
        assert smoke['source_functions_checked']==260 and smoke['input_dimensions']==[200,9] and len(smoke['comparisons'])==3
        assert not smoke['analysis_admitted'] and smoke['catalog_credit_added']==0
        bindings=list(csv.DictReader(io.StringIO(archive.read('idep244-original-smoke-4/LOADED-ORIGINAL-FUNCTIONS.tsv').decode()),delimiter='\t'))
        by_name={}
        for row in bindings:by_name.setdefault(row['function_name'],[]).append(row['body_and_formals_match']=='TRUE')
        assert len(bindings)==260 and len(by_name)==259 and all(any(values) for values in by_name.values())
        assert read('idep244-original-smoke-3/FAILURE.json')['original_return_class']=='character'
        for i in [1,2,3]:assert read('idep244-original-smoke-'+str(i)+'/PROCESS.json')['exit_code']!=0
        assert read('idep244-original-smoke-4/PROCESS.json')['exit_code']==0
    checked=0
    if private_base:
        index=json.loads((EVIDENCE/'PRIVATE-RECORDS.json').read_bytes())
        for name,identity in index.items():assert pin(private_base/name)==identity,name
        checked=len(index);assert checked==proof['private_index_files']
    return dict(status=proof['status'],version='2.4.4',original_smoke_genes=200,original_smoke_samples=9,original_smoke_comparisons=3,
        complete_indexed_private_files_checked=checked,private_index_excludes_installed_R_libraries=True,public_only=private_base is None,
        project_workflow_qualified=False,catalog_credit_added=0,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--freeze',action='store_true');parser.add_argument('--private-base',type=Path);args=parser.parse_args()
    if args.freeze:freeze()
    print(json.dumps(verify(args.private_base),indent=2))
