"""Check retained ASCAT qualification records; scientific fresh replay uses ascat-tools --verify.

Private evidence is optional and remains private. This checker never installs software,
executes article code, evaluates R expressions or deserializes an RDS input.
"""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,sys,zipfile

ROOT=Path(__file__).resolve().parents[2]
Q='docs/literature/ascat-validation/QUALIFICATION.json'
CASES=('legacy-full100','current-full100','hts-XX-XY','targeted-panel20','CRAM','joint-weighted','mixed-manual','tumor-only-declared-priors')

def require(value,message):
 if not value:raise ValueError(message)

def pin(path):
 digest=hashlib.sha256();size=0
 with Path(path).open('rb')as stream:
  for block in iter(lambda:stream.read(1048576),b''):digest.update(block);size+=len(block)
 return dict(bytes=size,sha256=digest.hexdigest())

def read(path):return json.loads(Path(path).read_bytes())

def qualified(q):
 require(q['status']=='PASS_SCOPED_ORIGINAL_ASCAT_MATRIX_HTS_TARGETED_AND_COMPLETE_REPLAY'
  and q['direct_public_source_build']and q['Windows_JUnit']==q['Linux_JUnit']==16 and q['JUnit_skips']==0
  and q['complete_qualified_captures']==q['complete_fresh_saved_replays']==8 and tuple(q['cases'])==CASES
  and q['full_original_example_samples_per_version']==100 and q['complete_R_objects_identical_to_direct_original_both_versions']
  and q['independent_allele_count_loci']==138000 and q['operational_refusals']==8
  and q['source_failed_cases_retained']==2 and q['original_legacy_GC_failure_directly_reproduced']
  and q['source_role_reviews_eligible']==4 and q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0
  and q['original_sources_unmodified']and q['all_failed_attempts_and_discrepancies_retained']
  and all(q[k]is False for k in('whole_package_qualified','full_author_roles_implemented','whole_paper_reproduced','whole_goal_complete')),
  'Incomplete or expanded ASCAT qualification')

def source_record(root):
 root=Path(root);q=read(root/Q);qualified(q);archive=root/'docs/literature/ascat-validation/source-context.zip';require(pin(archive)==q['source_context'],'Qualification source archive changed');current=[];historical=[]
 with zipfile.ZipFile(archive)as stream:
  expected=q['protected_source'];require(len(stream.namelist())==len(expected)and set(stream.namelist())==set(expected),'Qualification source population differs')
  for name,identity in expected.items():
   member=PurePosixPath(name);require(not member.is_absolute()and'..'not in member.parts and'\\'not in name and':'not in name,'Unsafe qualification source member');raw=stream.read(name);require(dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==identity,'Captured producing source bytes changed')
   path=root/name
   if path.is_file()and pin(path)==identity:current.append(name)
   else:historical.append(name)
 return dict(status='PASS_RETAINED_ASCAT_QUALIFICATION_SOURCE_CONTEXT',current_matching_sources=len(current),historical_sources_available_from_sealed_archive=historical,archive=pin(archive),current_changed_sources_not_automatically_qualified=bool(historical)),q

def private_evidence(root,private):
 context,q=source_record(root);private=Path(private).resolve(strict=True);proof_file=private/'QUALIFICATION-PROOF.json';require(pin(proof_file)==q['private_proof'],'Private qualification descriptor changed');proof=read(proof_file);require(tuple(proof['cases'])==CASES,'Qualified case population differs')
 def member(name):
  item=PurePosixPath(name);require(not item.is_absolute()and'..'not in item.parts and'\\'not in name and':'not in name,'Unsafe private evidence member');path=private/name;require(path.resolve(strict=True).is_relative_to(private)and not path.is_symlink(),'Private evidence escapes retained root');return path
 for name,identity in proof['files'].items():require(pin(member(name))==identity,'Private source/scientific/failed-attempt evidence changed: '+name)
 for name,identity in q.get('additional_private_evidence',{}).items():require(pin(member(name))==identity,'Additional retained warning/publication-review evidence changed: '+name)
 for name,expected in proof['root_populations'].items():
  folder=member(name);actual=[]
  for path in folder.rglob('*'):
   require(not path.is_symlink(),'Linked private evidence is not a complete captured population')
   if path.is_file():actual.append(path.relative_to(folder).as_posix())
  require(sorted(actual)==expected,'Complete retained evidence population changed: '+name)
 producer=pin(member(proof['producer_file']));require(producer==q['producer'],'Exact producing JAR changed')
 for name in CASES:
  case=proof['cases'][name];capture=member(case['capture']);replay=member(case['replay']);done=read(capture/'COMPLETED.json');again=read(replay/'COMPLETED.json');require(done['status']=='COMPLETE_ASCAT_SCOPED_WORKFLOW'and done['analysis_admitted']and again['status']=='PASS_COMPLETE_ASCAT_BINARY_AND_NUMERICAL_REPLAY','Incomplete original capture/replay');require(not(capture/'FAILURE.json').exists()and not(replay/'FAILURE.json').exists(),'Failed case cannot receive qualification')
  require(read(capture/'manifest.json')['producer']==producer and read(capture/'canonical.json')==read(replay/'canonical.json'),'Producer or complete scientific replay changed')
  if q.get('qualification_metadata_revision',1)>=2:
   stats=next(s for s in q['complete_case_statistics']if s['case']==name);warnings=read(capture/'OUTPUT-AUDIT.json')['source_warnings'];require(len(warnings)==stats['source_warning_count']and sorted({hashlib.sha256(w['message'].encode()).hexdigest()for w in warnings})==stats['source_warning_message_sha256'],'Public warning counts/identities differ from complete private messages')
 return q,dict(status='PASS_ALL_RETAINED_ASCAT_SCIENTIFIC_SOURCE_AND_FAILED_ATTEMPT_BYTES',source_context=context,producer=producer,cases=len(CASES),protected_files=len(proof['files']),additional_private_evidence_files=len(q.get('additional_private_evidence',{})),private_roots=len(proof['root_populations']),fresh_scientific_execution_performed_now=False)

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--private-root',type=Path);args=parser.parse_args()
 try:
  if args.private_root:_,result=private_evidence(args.root,args.private_root)
  else:result,_=source_record(args.root)
  print(json.dumps(result,sort_keys=True));return 0
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT ASCAT EVIDENCE CHECK SKIPPED: 'if code==3 else'FAIL2 NO COMPLETE ASCAT EVIDENCE CHECK: ')+str(error)+'. Required action: restore the exact producing source/JAR, permitted runtime and complete retained evidence. Preserve previous failures and seals. Run ascat-tools --verify for a new scientific replay; do not refresh old hashes.',file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
