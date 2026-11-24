# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read scoped nf-core source setup evidence; do not execute scientific analyses."""
from pathlib import Path
import argparse,hashlib,json,sys

def require(v,message):
    if not v:raise ValueError(message)
def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb')as f:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def verify(root,cache=None,jar=None):
    root=Path(root).absolute();folder=root/'docs/literature/nfcore-source-checkpoint';state=read(folder/'STATUS.json')
    require(state['status']=='PASS_NFCORE_SOURCE_SETUP_ONLY_SEVEN_EDITIONS'and state['new_family_credit']==0 and not state['catalog_admitted']and not state['analysis_admitted']and not state['whole_goal_complete'],'Expanded source-only scope')
    require(pin(folder/'FILES.json')==state['public_manifest'],'Changed public manifest');files=read(folder/'FILES.json')
    for name,identity in files.items():
        p=Path(name);require(not p.is_absolute()and '..'not in p.parts and '\\'not in name and ':'not in name,'Unsafe public source member');file=root/p
        require(file.is_file()and file.resolve()==file.absolute()and pin(file)==identity,'Changed saved source/evidence: '+name)
    for name,identity in state['canonical_unchanged'].items():require(pin(root/name)==identity,'Catalog changed; use the preserved complete source context for this checkpoint')
    lock=read(root/'src/main/resources/org/jlinalg/cli/nfcore-source-setup/source-lock.json');require(len(lock['profiles'])==7 and sum(len(p['files'])for p in lock['profiles'].values())==998,'Changed exact original source population')
    proof=read(folder/'VALIDATION.json');require(proof['status']=='PASS_PACKAGED_ORIGINAL_SOURCE_SETUP_WINDOWS_AND_LINUX'and proof['Java_tests']==dict(windows=12,linux=12),'Missing source and existing-route regression checks')
    for platform in('windows','linux'):
        result=proof[platform]
        require((result['profiles'],result['source_files'],result['offline_installations'],result['source_rechecks'],result['java_original_source_inspections'])==(7,998,7,7,7),'Incomplete full-source original archive qualification')
        require(len(result['unsafe_archive_refusals'])==10 and len(result['calls'])>=40 and all(r['exit_code']==r['expected']for r in result['calls']),'Incomplete actual setup/refusal controls')
        require(not result['analysis_admitted']and result['new_family_credit']==0,'Expanded validation claim')
    require(proof['windows']['online_installation']and not proof['linux']['online_installation'],'Changed explicit download test coverage')
    if jar is not None:require(pin(jar)==state['producer'],'Restore the exact complete producer; never refresh old hashes')
    fresh=0
    if cache is not None:
        sys.path.insert(0,str(root/'tools/literature'));from nfcore_workflows.primary import audit
        result=audit((root/'docs/literature/omics-2023-2026/papers.json').read_bytes(),cache,root)
        require(result==proof['primary_audit'],'Changed full primary source audit');fresh=result['primary_XML_checked']
    return dict(status='PASS_NFCORE_SOURCE_ONLY_CHECKPOINT',public_files=len(files),source_profiles=7,source_files=998,primary_XML_checked_now=fresh,new_family_credit=0,analysis_admitted=False,whole_goal_complete=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2]);p.add_argument('--cache',type=Path);p.add_argument('--jar',type=Path);a=p.parse_args()
    try:print(json.dumps(verify(a.root,a.cache,a.jar),indent=2));return 0
    except(FileNotFoundError,PermissionError)as e:print('SKIP3: dependent checkpoint verification skipped. '+str(e)+'. Restore exact permitted sources/inputs or retain this skip.',file=sys.stderr);return 3
    except Exception as e:print('FAIL2: no complete source checkpoint. '+repr(e)+'. Preserve evidence and correct the mismatch; never refresh old hashes.',file=sys.stderr);return 2
if __name__=='__main__':sys.exit(main())
