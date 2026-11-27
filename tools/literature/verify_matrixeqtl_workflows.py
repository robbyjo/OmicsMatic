"""Check scoped MatrixEQTL qualification and optionally the retained private evidence.

This verifies saved evidence; fresh numerical comparison uses matrixeqtl23/qualify.py.
Original papers, software source, binaries and raw R traces remain private.
"""
import argparse,hashlib,json,sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/matrixeqtl-validation'
CASES={'all','cis-trans','cis-only','gls','no-covariates','female-stratum','male-stratum','tiled','underflow'}
def read(p):return json.loads(Path(p).read_bytes())
def pin(p):
 h=hashlib.sha256();n=0
 with Path(p).open('rb')as f:
  for b in iter(lambda:f.read(1048576),b''):n+=len(b);h.update(b)
 assert n==Path(p).stat().st_size
 return dict(bytes=n,sha256=h.hexdigest())
def verify(root,private=None):
 q=read(root/FOLDER/'QUALIFICATION.json');assert q['status']=='QUALIFIED_SCOPED_NATIVE_MATRIXEQTL23_ADDITIVE'and q['source_version']=='2.3'and q['new_native_family_credit']==1 and not q['whole_package_qualified']and not q['whole_goal_complete']
 assert q['component_tests']==dict(tests=19,failures=0,errors=0,skipped=0)
 assert set(q['platforms'])=={'Windows','Linux'}and q['source_roles']==4 and q['main_text_contexts']==5 and q['other_reference_structures']==3
 for name,identity in q['sources'].items():assert pin(root/name)==identity,('Public qualification source changed',name)
 for platform,row in q['platforms'].items():
  assert row['producer']==q['producer']and{r['case']for r in row['cases']}==CASES and len(row['cases'])==9
  assert len(row['refusals'])==10 and sum(r['exit_code']==3 for r in row['refusals'])==2
  assert all(not r['result_admitted']and r['exit_code']in [2,3]for r in row['refusals'])
  assert sum(r['counts']['associations']for r in row['cases'])==19622 and sum(r['counts']['independent_lm']for r in row['cases'])==19458
 if private is not None:
  private=Path(private);manifest=private/'final-qualification-1/EVIDENCE.json';assert pin(manifest)==q['private_evidence_manifest'];m=read(manifest)
  assert len(m['files'])==q['private_files']
  for name,identity in m['files'].items():
   assert not Path(name).is_absolute()and'..'not in Path(name).parts and not(private/name).is_symlink();assert pin(private/name)==identity,('Private evidence changed',name)
  assert pin(private/'project-build-Windows-2/project-build/cli/omicsmatic-0.4.1.jar')==q['producer']
  for platform in ['Windows','Linux']:
   full=read(private/('packaged-'+platform+'-1/COMPLETED.json'));assert full['producer']==q['producer']and full['cases']==q['platforms'][platform]['cases']and full['refusals']==q['platforms'][platform]['refusals']
 return dict(status='PASS_SAVED_MATRIXEQTL_QUALIFICATION_CONTEXT',native_family_credit=1,platforms=2,cases_per_platform=9,refusals_per_platform=10,private_evidence_checked=private is not None,whole_goal_complete=False)
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--private',type=Path);args=parser.parse_args()
 try:print(json.dumps(verify(args.root.resolve(),args.private),indent=2))
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3: Missing permitted evidence; dependent verification skipped. 'if code==3 else'FAIL2: No complete qualification verification. ')+str(error)+'. Restore the exact saved evidence and retry.',file=sys.stderr);raise SystemExit(code)
