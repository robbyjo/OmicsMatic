from pathlib import Path
import hashlib,json,subprocess,zipfile
from prepare_genomicranges_20261122_114 import R,T,B,O,BASE
from handover_inventory_20261122_111 import pin,save,snapshot,git

def run(*args):return git(*args,cwd=T)

def main():
 d=T/'docs/literature/genomicranges-handover-20261122'
 status=json.loads((d/'STATUS.json').read_bytes());assert status['build_failures']==0 and status['build_tests']==747
 for name,value in status['file_pins'].items():assert pin(T/name)==value,name
 old=R/'build/sk-2/build/skat-runtime-1/normal-gradle-8/project-build/cli/omicsmatic-0.4.0.jar'
 new=B/'normal-gradle-handover-1/project-build/cli/omicsmatic-0.4.0.jar'
 with zipfile.ZipFile(old) as a,zipfile.ZipFile(new) as z:
  oldnames=set(a.namelist());newnames=set(z.namelist());assert oldnames<=newnames
  changed=[n for n in oldnames if not n.endswith('/') and a.read(n)!=z.read(n)]
  added=sorted(n for n in newnames-oldnames if not n.endswith('/'))
  assert not changed,changed
  assert len(added)==3 and all(n.startswith('org/jlinalg/genetics/GenomicRangeGeometry') for n in added),added
  save(O/'JAR-PAYLOAD-COMPARISON.json',{'all_previous_payloads_exact':True,'previous_files':sum(not n.endswith('/') for n in oldnames),'new_files':added,'old_producer':pin(old),'development_producer':pin(new),'scope':'No original-R refit or whole-adapter qualification claimed'})
 assert run('rev-parse','HEAD').decode().strip()==BASE
 assert run('ls-remote','origin','refs/heads/main').decode().split()[0]==BASE
 before=json.loads((O/'WORKTREES-BEFORE.json').read_bytes());now=snapshot();by={x['worktree']:x for x in now};assert all(by[x['worktree']]==x for x in before)
 # Only this task's reviewed source, artificial reference data and handover.
 files=['docs/literature/HANDOVER_CURRENT.md','docs/literature/HANDOVER_2026-11-22_GENOMICRANGES.md','src/main/java/org/jlinalg/genetics/GenomicRangeGeometry.java','src/test/java/org/jlinalg/genetics/GenomicRangeGeometryReferenceTest.java']
 files += [p.relative_to(T).as_posix() for root in [d,T/'src/test/resources/org/jlinalg/genetics/genomicranges-geometry/1.64.0'] for p in sorted(root.rglob('*')) if p.is_file()]
 assert len(set(files))==len(files)
 assert not run('diff','--cached','--name-only').strip()
 expected={n:pin(T/n) for n in files};save(O/'PUBLICATION-FILES.json',expected)
 run('add','--',*files)
 assert set(run('diff','--cached','--name-only').decode().splitlines())==set(files)
 for n in files:
  raw=run('cat-file','blob',':'+n);assert {'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}==expected[n],n
 run('diff','--cached','--check')
 save(O/'COMMIT-STARTED.json',{'files':len(files),'scope':'Current-task handover/component source and artificial references;not broad historical backups/private outputs','base':BASE})
 text=run('commit','-m','Save tested GenomicRanges geometry and toolbox handover').decode(errors='replace');(O/'commit.stdout').write_text(text,encoding='utf-8')
 commit=run('rev-parse','HEAD').decode().strip();tree=run('rev-parse','HEAD^{tree}').decode().strip()
 assert not run('status','--porcelain=v1').strip()
 assert run('ls-remote','origin','refs/heads/main').decode().split()[0]==BASE
 command=['git','push','--atomic','origin','HEAD:refs/heads/main','HEAD:refs/heads/codex/genomicranges-workflows-20261122']
 save(O/'PUSH-STARTED.json',{'command':command,'commit':commit,'tree':tree,'force':False,'historical_payloads_included':False})
 with (O/'push.stdout').open('xb') as out,(O/'push.stderr').open('xb') as err:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err,timeout=240)
 save(O/'PUSH-PROCESS.json',{'exit_code':p.returncode});assert p.returncode==0,(O/'push.stderr').read_text(errors='replace')
 refs=run('ls-remote','origin','refs/heads/main','refs/heads/codex/genomicranges-workflows-20261122').decode().splitlines();assert len(refs)==2 and all(x.split()[0]==commit for x in refs)
 after=snapshot();by={x['worktree']:x for x in after};assert len(after)==70 and all(by[x['worktree']]==x for x in before)
 assert not run('status','--porcelain=v1').strip()
 save(O/'WORKTREES-AFTER.json',after)
 result={'status':'CURRENT_TASK_HANDOVER_AND_COMPONENT_PUSHED_MAIN_AND_BRANCH_REMOTE_SHA_VERIFIED','commit':commit,'tree':tree,'base':BASE,'remote_refs':refs,'files':len(files),'tests':747,'failures':0,'all69_prior_owners_exact':True,'all_previous_JAR_payloads_exact':True,'scoped_families':148,'reviewed_uses':1661,'pending_uses':1062,'new_family_credit':0,'new_reviews':0,'historical_backup_payloads_pushed':False,'private_outputs_pushed':False,'whole_goal_complete':False}
 save(O/'PUBLISHED.json',result);print(json.dumps(result),flush=True)

if __name__=='__main__':main()
