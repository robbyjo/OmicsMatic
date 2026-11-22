"""Verify the scoped Monocle admission and retained original controls; run no models."""
from pathlib import Path, PurePosixPath
import argparse,json,os,shlex,sys,traceback,zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from monocle_catalog import primary as p,evidence as e,catalog as c

FOLDER='docs/literature/monocle-admission-checkpoint'

def safe(name):
    value=PurePosixPath(name)
    p.require(name and not value.is_absolute() and all(part not in {'.','..'} and ':' not in part and '\\' not in part for part in value.parts),'Unsafe packet member')
    return value.parts

def profile_scope(record):
    p.require(record['schema_version']==1 and record['status']=='PASS_MONOCLE_SCOPED_FAMILY_ADMISSION' and record['new_catalog_credit']==1 and record['upstream_family_credit']==1 and record['native_helpers_additional_credit']==0,'Different admission credit/scope')
    p.require(record['catalog_applied'] is True and record['whole_goal_complete'] is False and record['whole_paper_reproduced'] is False,'False unapplied/whole-paper/whole-goal claim')
    p.require(record['catalog']==dict(papers=250,observations=2722,labels=446,registry_definitions=485,scoped_families=145,native=26,upstream=119,reviewed=1630,pending=1092,missing=258,method_overlap=29,manifest_members=841),'Different admitted catalog population')

def verify(out,cache=None,restore=False):
    folder=ROOT/FOLDER;record=json.loads((folder/'PROFILE.json').read_bytes());profile_scope(record)
    for name,wanted in record['source_inputs'].items():p.require(p.pin(ROOT.joinpath(*safe(name)).read_bytes())==wanted,'Changed admission packet source: '+name)
    for name,wanted in record['files'].items():p.require(p.pin(folder.joinpath(*safe(name)).read_bytes())==wanted,'Changed admission packet file: '+name)
    for name,wanted in record['reference_evidence'].items():p.require(p.pin(ROOT.joinpath(*safe(name)).read_bytes())==wanted,'Changed retained original-evidence archive: '+name)
    installed=out/'catalog';installed.mkdir();result=c.verify(installed,cache)
    proof=json.loads((ROOT/e.PROOF).read_bytes());original=proof['qualification']['current_original_results']
    old=ROOT/record['original_archive_folder'];index=json.loads((old/'INVENTORY.json').read_bytes())
    with zipfile.ZipFile(old/'evidence.zip') as archive:
        def retained(name):
            wanted=index[name];value=archive.read('blobs/'+wanted['sha256']);p.require(p.pin(value)==wanted,'Changed retained original control: '+name);return value
        p.require(json.loads(retained('current/current-original-3/RESULT.json'))==original,'Original execution record differs from admitted qualification')
        count=0
        for action,row in original['results'].items():
            prefix='current/current-original-3/'+action+'/'
            p.require(json.loads(retained(prefix+'PROCESS.json'))['exit_code']==0,'Failed original action promoted')
            for name,wanted in row['scientific_outputs_byte_identical_to_retained_original'].items():p.require(p.pin(retained(prefix+'capture/'+name))==wanted,'Changed complete original scientific output');count+=1
        p.require(count==29,'Different original scientific output population')
    members=json.loads((folder/'INDEX.json').read_bytes());p.require(len(members)<=5000,'Too many checkpoint members')
    with zipfile.ZipFile(folder/'checks.zip') as archive:
        p.require(len(archive.namelist())==len(members) and set(archive.namelist())==set(members),'Different checkpoint archive population')
        p.require(all(item.file_size<=64*1024*1024 for item in archive.infolist()) and sum(item.file_size for item in archive.infolist())<=256*1024*1024,'Checkpoint exceeds capacity')
        for name,wanted in members.items():
            value=archive.read(name);p.require(p.pin(value)==wanted,'Changed checkpoint member: '+name);parts=safe(name)
            if restore:
                target=(out/'captured').joinpath(*parts);target.parent.mkdir(parents=True,exist_ok=True)
                with target.open('xb') as stream:stream.write(value)
        def read(name):return json.loads(archive.read(name))
        for host,attempt in [('windows',1),('linux',2)]:
            prefix='verify-'+host+'-'+str(attempt)+'/'
            value=read(prefix+'checks/COMPLETED.json');p.require(read(prefix+'PROCESS.json')['exit_code']==0 and value['status']==c.STATUS and value['result']==result['result'] and value['fresh_primary_audit'] is True,'Missing installed fresh-source verification')
        ui=read('installed-ui-1/browser/COMPLETED.json');p.require(ui['integrations']==145 and len(ui['paper_roles_checked'])==9 and read('installed-ui-1/PROCESS.json')['exit_code']==0 and read('installed-ui-1/SERVER-CLOSED.json')['owned_helper_stopped'] is True,'Missing installed nine-role desktop/mobile validation')
        readback=read('independent-readback-1/RESULT.json');p.require(readback['previous_manifest_members_retained']==838 and readback['current_manifest_members']==841 and readback['changed_review_scope_records']==10 and readback['unchanged_reviews']==1620 and readback['historical_monocle_proof_and_inverse_preserved'] is True,'Missing independent transition readback')
        controls=read('control-integrity-2/RESULT.json');p.require(len(controls['controls'])==4 and all(row['refused'] for row in controls['controls'].values()),'Missing control-evidence refusals')
    value=dict(status='PASS_MONOCLE_ADMISSION_PACKET_BYTES_SCOPE_AND_RETAINED_CONTROLS',catalog=record['catalog'],existing_monocle_scopes_updated=9,related_policy_notes_updated=1,new_review_records=0,retained_original_scientific_files_verified=29,checkpoint_files_verified=len(members),fresh_primary_audit=cache is not None,scientific_models_executed=False,new_catalog_credit=1,whole_paper_reproduced=False,whole_goal_complete=False,restored=restore)
    (out/'RESULT.json').write_bytes(p.encoded(value));return value

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--primary-cache',type=Path);parser.add_argument('--git');parser.add_argument('--restore',action='store_true');args=parser.parse_args();owned=False
    try:
        p.require(__debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O');out=args.out.resolve();p.require(out.is_relative_to(ROOT/'build') and out!=ROOT/'build','Use NEW workspace build output');out.mkdir(parents=True,exist_ok=False);owned=True
        if args.git:
            os.environ['MONOCLE_CATALOG_GIT']=args.git
            if os.name=='posix':
                shim=out/'git-shim';shim.mkdir();target=shim/'git';target.write_text('#!/bin/sh\nexec '+shlex.quote(args.git)+' "$@"\n',encoding='utf-8',newline='\n');target.chmod(0o755);os.environ['PATH']=str(shim)+os.pathsep+os.environ.get('PATH','')
        value=verify(out,args.primary_cache,args.restore);print(json.dumps(value));return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if owned:(args.out.resolve()/'FAILURE.json').write_bytes(p.encoded(dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),traceback=traceback.format_exc(),complete_result=False,required_action=c.m.ACTION)))
        print(('SKIP3' if code==3 else 'FAIL2')+': '+str(error)+'\n'+c.m.ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
