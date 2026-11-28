"""Prepare a NEW private VEP105 core from exact existing dependencies; preserve all old runtime profiles."""
import argparse,concurrent.futures,hashlib,json,os,re,subprocess,sys
from pathlib import Path
from setup_loftee_workflow_runtime import replace_checked
ROOT=Path(__file__).resolve().parents[2]

def pin(path):
 h=hashlib.sha256();n=0
 with Path(path).open('rb')as stream:
  for raw in iter(lambda:stream.read(1048576),b''):h.update(raw);n+=len(raw)
 return dict(bytes=n,sha256=h.hexdigest())
def save(path,value):
 with Path(path).open('x',encoding='utf-8')as stream:json.dump(value,stream,indent=2,allow_nan=False);stream.write('\n')
def prepare(environment,bioperl,which,out):
 profile_path=ROOT/'src/main/resources/org/jlinalg/omics/vep105/RUNTIME.json';profile=json.loads(profile_path.read_bytes())
 for path,expected in profile['host_files'].items():
  if pin(path)!=expected:raise RuntimeError('Qualified VEP core host bytes differ: '+path+'; use the exact isolated qualified host or retain skip. Do not change old LOFTEE guards or shared libraries.')
 records={};identities=[]
 for group,source in [('environment',environment),('bioperl',bioperl)]:
  destination=out/group;destination.mkdir();files=profile[group]['files'];old=profile[group].get('baseline_root');root=source.resolve();jobs=[];links=[]
  for name,item in files.items():
   leaf=Path(name)
   if leaf.is_absolute()or'..'in leaf.parts:raise RuntimeError('Invalid relative source-profile member')
   if item['kind']=='symlink':links.append((name,item));continue
   jobs.append((name,item))
  def copy(job):
   name,item=job;original=root/name;target=destination/name
   if not original.is_file()or original.is_symlink():raise FileNotFoundError('Required exact regular VEP source: '+str(original))
   before=pin(original);raw=original.read_bytes()
   if pin(original)!=before or len(raw)!=before['bytes']:raise RuntimeError('Original VEP input changed: '+name)
   value=replace_checked(raw,item,root,destination);target.parent.mkdir(parents=True,exist_ok=True)
   with target.open('xb')as stream:stream.write(value)
   os.chmod(target,item['mode']);replace_checked(target.read_bytes(),item,destination)
   return name,dict(kind='file',**pin(target)),(original,before)
  copied={}
  with concurrent.futures.ThreadPoolExecutor(max_workers=6)as pool:
   for name,item,source_identity in pool.map(copy,jobs):copied[name]=item;identities.append(source_identity)
  for name,item in links:
   original=root/name;target=destination/name;expected=item['target'];baseline=old
   if baseline is None:
    baseline=next((x['prefix_relocation']['old_prefix']for x in files.values()if'prefix_relocation'in x),'__NO_ALIAS__')
   source_target=expected.replace(baseline,str(root));new_target=expected.replace(baseline,str(destination))
   if not original.is_symlink()or os.readlink(original)!=source_target or not original.resolve().is_relative_to(root):raise RuntimeError('Original runtime link differs/escapes: '+name)
   target.parent.mkdir(parents=True,exist_ok=True);target.symlink_to(new_target)
   if not target.resolve().is_relative_to(destination):raise RuntimeError('Prepared runtime link escaped: '+name)
   copied[name]=dict(kind='symlink',target=new_target)
  records[group]=copied
 helper=out/'helpers';helper.mkdir();expected=profile['helpers']['files']['which']
 if not which.is_file()or which.is_symlink()or pin(which)!={k:expected[k]for k in ['bytes','sha256']}:raise RuntimeError('Exact original which.debianutils helper unavailable/different')
 with(helper/'which').open('xb')as stream:stream.write(which.read_bytes())
 os.chmod(helper/'which',expected['mode']);records['helpers']={'which':dict(kind='file',**pin(helper/'which'))}
 for original,expected in identities:
  if pin(original)!=expected:raise RuntimeError('Original runtime changed during preparation: '+str(original))
 save(out/'runtime-file-identities.json',records);envroot=out/'environment';env=dict(PATH=str(helper)+':'+str(envroot/'bin'),PERL5LIB=str(envroot/'share/ensembl-vep-105.0-1')+':'+str(out/'bioperl'),LD_LIBRARY_PATH=str(envroot/'lib'),HOME=str(out),TMPDIR=str(out),LC_ALL='C',LANG='C',TZ='UTC',PERL_HASH_SEED='0',PERL_PERTURB_KEYS='0');command=[str(envroot/'bin/perl'),str(envroot/'share/ensembl-vep-105.0-1/vep'),'--help']
 p=subprocess.run(command,env=env,cwd=out,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120);(out/'vep-help.stdout').write_bytes(p.stdout);(out/'vep-help.stderr').write_bytes(p.stderr);save(out/'vep-help-process.json',dict(command=command,exit_code=p.returncode))
 if p.returncode or b'105.0'not in p.stdout:raise RuntimeError('Prepared original VEP105 help failed; inspect retained logs')
 return dict(status='PASS_EXACT_VEP105_CORE_PREPARED_HEALTH_ONLY',profile=profile['profile'],profile_identity=pin(profile_path),environment=str(envroot),bioperl=str(out/'bioperl'),helpers=str(helper),runtime_files=pin(out/'runtime-file-identities.json'),old_LOFTEE_profile_changed=False,variant_analysis_executed=False,implementation_credit=False)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--environment',required=True,type=Path);p.add_argument('--bioperl',required=True,type=Path);p.add_argument('--which',type=Path,default=Path('/usr/bin/which.debianutils'));p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-prepare',action='store_true');a=p.parse_args();out=a.out.absolute()
 action='Preserve this attempt and its diagnostics. Where permitted, supply the exact qualified VEP105/BioPerl dependencies and original helper on the qualified Linux x86_64 host, then retry NEW short private output. Preparation runs no variant analysis; do not substitute versions, repin drifted files, change older LOFTEE guards, or replace shared host libraries.'
 if not a.allow_prepare or not sys.platform.startswith('linux')or not re.fullmatch('/[A-Za-z0-9_./-]+',str(out))or out!=out.resolve():print('SKIP3_DEPENDENT_ANALYSIS_SKIPPED: require --allow-prepare, Linux and a new canonical ASCII output. '+action);return 3
 if out.exists():print('FAIL2_NO_COMPLETE_RESULT: preserve existing output; select NEW directory. '+action);return 2
 out.mkdir(parents=True);state={};code=0
 try:state=prepare(a.environment,a.bioperl,a.which,out)
 except (FileNotFoundError,PermissionError)as error:code=3;state=dict(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED',cause=repr(error))
 except Exception as error:code=2;state=dict(status='FAIL2_NO_COMPLETE_RESULT',cause=repr(error))
 state['required_action']=action;save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','Health/preparation only; require a separately successful vep-tools variant run.')+'\n\nREQUIRED ACTION: '+action+'\n',encoding='utf-8');print(json.dumps(state,indent=2));return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Do not disable original byte/prefix checks')
 raise SystemExit(main())
