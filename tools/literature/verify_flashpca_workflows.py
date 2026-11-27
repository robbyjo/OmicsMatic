"""Check scoped FlashPCA qualification and optional retained private evidence.

Fresh scientific reruns use the producing JAR's flashpca-tools --verify.
Saved checks alone do not authenticate a producer or reproduce a paper cohort.
"""
import argparse,hashlib,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];FOLDER='docs/literature/flashpca-validation'
def read(p):return json.loads(Path(p).read_bytes())
def pin(p):
 h=hashlib.sha256();n=0
 with Path(p).open('rb')as f:
  for b in iter(lambda:f.read(1048576),b''):n+=len(b);h.update(b)
 assert n==Path(p).stat().st_size
 return dict(bytes=n,sha256=h.hexdigest())
def verify(root,private=None):
 q=read(root/FOLDER/'QUALIFICATION.json');assert q['status']=='QUALIFIED_SCOPED_ORIGINAL_FLASH_PCA_GENETIC_COMPONENTS'and q['new_upstream_family_credit']==1 and not q['new_native_family_credit']and not q['whole_goal_complete']and not q['whole_paper_reproduced']
 assert q['component_tests']==dict(tests=14,failures=0,errors=0,skipped=0)
 assert set(q['source_editions'])=={'1.2.6','2.0'}and len(q['cases'])==7 and len(q['independent_complete_svd_comparisons'])==20 and len(q['refusals'])==10
 assert sum(r['exit_code']==3 for r in q['refusals'])==3 and all(not r['result_admitted']for r in q['refusals'])
 assert len(q['primary_roles'])==4 and q['full250_source_audit']['main_text_contexts']==4 and q['full250_source_audit']['method_family_citations']==4
 for name,identity in q['sources'].items():assert pin(root/name)==identity,('Public qualification source changed',name)
 for row in q['cases']:
  assert row['genotype_cells']==15360 and row['components']==20 and row['full_fresh_replay']and row['all_six_original_numeric_tables_sealed']and not row['author_recipe_qualified']
 if private is not None:
  private=Path(private);manifest=private/'final-qualification-1/EVIDENCE.json';assert pin(manifest)==q['private_evidence_manifest'];m=read(manifest);assert len(m['files'])==q['private_files']
  for name,identity in m['files'].items():assert not Path(name).is_absolute()and'..'not in Path(name).parts and not(private/name).is_symlink()and pin(private/name)==identity,('Private evidence changed',name)
  assert pin(private/'project-build-Windows-1/project-build/cli/omicsmatic-0.4.1.jar')==q['producer']
  for row in q['cases']:
   capture=private/row['capture'];replay=private/row['replay'];a=read(capture/'COMPLETED.json');b=read(replay/'REPLAY.json');assert a['result_admitted']and a['status']=='COMPLETE_SCOPED_ORIGINAL_FLASHPCA_PCA'and b['all_source_tables_equal'];assert(capture/'canonical.json').read_bytes()==(replay/'canonical.json').read_bytes()
 return dict(status='PASS_SAVED_FLASH_PCA_QUALIFICATION_CONTEXT',source_editions=2,captures_and_fresh_replays=7,component_tests=14,independent_complete_svd_quantities=20,refusals=10,new_upstream_family_credit=1,private_evidence_checked=private is not None,whole_goal_complete=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private),indent=2))
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3: Missing permitted saved evidence; dependent verification skipped. 'if code==3 else'FAIL2: No complete qualification verification. ')+str(error)+'. Restore the named source/evidence and retry.',file=sys.stderr);raise SystemExit(code)
