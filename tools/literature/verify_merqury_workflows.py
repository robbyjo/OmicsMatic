"""Verify the frozen Merqury source record and optional private evidence/full saved replays."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/merqury-validation'
def require(ok,message):
 if not ok:raise ValueError(message)
def pin(path):
 digest=hashlib.sha256();size=0
 with Path(path).open('rb')as stream:
  for block in iter(lambda:stream.read(1048576),b''):size+=len(block);digest.update(block)
 return dict(bytes=size,sha256=digest.hexdigest())
def read(path):return json.loads(Path(path).read_bytes())
def member(value):
 require(isinstance(value,str)and value and not value.startswith('/')and':'not in value and'\\'not in value and all(p not in('','.','..')for p in value.split('/')),'Unsafe frozen relative member');return value
def source_record(root):
 root=Path(root);q=read(root/FOLDER/'QUALIFICATION.json');require(q['status']=='PASS_SCOPED_MERQURY_COUNT_ASSESSMENT_AND_PARENTAL_DATABASES'and q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0,'Different scoped Merqury qualification')
 require(q['Windows_JUnit']==q['Linux_JUnit']==31 and q['JUnit_skips']==0 and q['complete_captures']==q['fresh_binary_numerical_saved_replays']==9,'Incomplete tests/workflow replay population')
 require(all(q[k]is False for k in('whole_package_qualified','full_author_roles_implemented','whole_paper_reproduced','whole_goal_complete')),'Expanded source/paper admission')
 for name,key in [('SOURCES.json','source_manifest'),('EVIDENCE.json','evidence_manifest')]:require(pin(root/FOLDER/name)==q[key],'Frozen source/evidence manifest changed')
 sources=read(root/FOLDER/'SOURCES.json')
 for name,identity in sources['files'].items():require(pin(root/member(name))==identity,'Frozen public source changed: '+name)
 evidence=read(root/FOLDER/'EVIDENCE.json');require(len(evidence['captures'])==len(evidence['replays'])==9 and len(evidence['source_failures'])==2,'Different complete source evidence population')
 return q,evidence
def private_evidence(root,private):
 q,evidence=source_record(root);private=Path(private)
 for name,identity in evidence['files'].items():
  path=private/member(name);require(not path.is_symlink()and pin(path)==identity,'Frozen private evidence changed: '+name)
 for name,target in evidence['symlinks'].items():
  path=private/member(name);require(path.is_symlink()and os.readlink(path)==target,'Frozen owned link changed: '+name)
 producer=private/member(evidence['producer']['path']);require(pin(producer)==evidence['producer']['pin'],'Restore the exact retained producing JAR')
 for name in evidence['captures']:
  capture=private/member(name);done=read(capture/'COMPLETED.json');require(done['status']=='COMPLETE_MERQURY_SCOPED_WORKFLOW'and done['analysis_admitted']and not done['whole_package_admitted']and done['manifest']==pin(capture/'manifest.json'),'Incomplete or expanded capture admission')
  manifest=read(capture/'manifest.json');require(manifest['producer']==evidence['producer']['pin'],'Different scientific producing JAR')
 for name in evidence['replays']:require(read(private/member(name))['status']=='PASS_COMPLETE_MERQURY_BINARY_AND_NUMERICAL_REPLAY','Incomplete saved binary/numerical replay')
 return q,evidence
def replay(root,private,out):
 require(sys.platform=='linux','Full original binary replay requires Linux; use Linux Java inside WSL')
 q,evidence=private_evidence(root,private);private=Path(private);out=Path(out);require(not out.exists()and out.parent.is_dir(),'Use a NEW replay receipt directory');out.mkdir();producer=private/member(evidence['producer']['path'])
 for index,name in enumerate(evidence['captures']):
  command=['/usr/bin/java','-Xmx2g','-XX:ActiveProcessorCount=2','-Djava.awt.headless=true','-jar',str(producer),'merqury-tools','--verify',str(private/member(name)),'--scratch',str(out/('replay-'+str(index)))];prefix=out/('capture-'+str(index));prefix.with_suffix('.command.json').write_text(json.dumps(command,indent=2)+'\n')
  with prefix.with_suffix('.stdout').open('xb')as stdout,prefix.with_suffix('.stderr').open('xb')as stderr:
   process=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);prefix.with_suffix('.process.json').write_text(json.dumps(dict(pid=process.pid))+'\n');code=process.wait()
  prefix.with_suffix('.result.json').write_text(json.dumps(dict(exit_code=code))+'\n');require(code==0,'Complete saved replay failed: '+name);print('PASS_COMPLETE_REPLAY',name,flush=True)
 private_evidence(root,private);(out/'COMPLETED.json').write_text(json.dumps(dict(status='PASS_ALL_NINE_FROZEN_MERQURY_BINARY_NUMERICAL_REPLAYS',producer=evidence['producer']['pin'],whole_goal_complete=False),indent=2)+'\n')
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--private-root',type=Path);parser.add_argument('--replay',action='store_true');parser.add_argument('--out',type=Path);args=parser.parse_args()
 try:
  if args.replay:require(args.private_root is not None and args.out is not None,'Full replay requires explicit private root and NEW output');replay(args.root,args.private_root,args.out);status='PASS_ALL_NINE_FROZEN_MERQURY_BINARY_NUMERICAL_REPLAYS'
  elif args.private_root is not None:private_evidence(args.root,args.private_root);status='PASS_FROZEN_PRIVATE_EVIDENCE_BYTES_ONLY_NO_NEW_SCIENTIFIC_EXECUTION'
  else:source_record(args.root);status='PASS_FROZEN_SOURCE_RECORD_ONLY_NO_LIVE_RUNTIME_OR_SCIENTIFIC_REPLAY'
  print(json.dumps(dict(status=status,whole_goal_complete=False),sort_keys=True));return 0
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT MERQURY VERIFICATION SKIPPED: 'if code==3 else'FAIL2 NO MERQURY VERIFICATION ADMITTED: ')+str(error)+'. Required action: restore exact frozen sources, producer, permitted private evidence/runtime and original paths; preserve failed attempts and historical seals. Full replay needs Linux and NEW scratch paths.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
