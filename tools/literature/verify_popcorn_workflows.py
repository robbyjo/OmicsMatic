"""Verify source-bound Popcorn qualification; optionally verify retained private evidence bytes."""
import argparse,gzip,hashlib,json,zipfile
from pathlib import Path
def read(path):return json.loads(Path(path).read_bytes())
def pin(path):
 path=Path(path);digest=hashlib.sha256();size=0
 with path.open('rb')as stream:
  for block in iter(lambda:stream.read(1048576),b''):size+=len(block);digest.update(block)
 assert size==path.stat().st_size
 return dict(bytes=size,sha256=digest.hexdigest())
def verify(repo,evidence=None):
 folder=repo/'docs/literature/popcorn-validation';q=read(folder/'QUALIFICATION.json');assert q['schema_version']==1 and q['status']=='QUALIFIED_SCOPED_POPCORN_TWO_POPULATION_WORKFLOWS'
 assert(q['native_tests_per_os'],q['full_workflow_captures'],q['fresh_workflow_replays'],q['independent_original_comparison_cases'],q['independent_original_CLI_invocations'],q['packaged_negative_controls'])==(31,9,9,9,13,13)
 assert q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0 and not q['whole_goal_complete']and not q['exact_author_recipes_qualified'];assert pin(folder/'source-context.zip')==q['source_archive']
 with zipfile.ZipFile(folder/'source-context.zip')as archive:
  assert len(archive.namelist())==len(q['sources'])and set(archive.namelist())==set(q['sources'])
  for name,expected in q['sources'].items():
   raw=archive.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==expected;assert pin(repo/name)==expected,('Restore archived context for historical verification',name)
 profiles=read(repo/'src/main/resources/org/jlinalg/omics/popcorn/profiles.json')['profiles'];assert set(profiles)=={'Popcorn-1.0-3c21c41f','Popcorn-1.1-20a29e8c'}and all(len(p['source_files'])==12 for p in profiles.values())
 oracles=list((repo/'src/test/resources/org/jlinalg/genetics/popcorn').glob('*.json'));assert len(oracles)==11
 for path in oracles:assert set(read(path)['versions'])=={'1.0','1.1'}
 if evidence is not None:
  proof=read(evidence/'QUALIFICATION-PROOF.json');assert proof['public_qualification']==pin(folder/'QUALIFICATION.json')
  for name,expected in proof['retained_files'].items():assert pin(evidence/name)==expected,('Changed retained private Popcorn evidence',name)
  assert pin(evidence/q['producer_relative'])==q['producer'];build=read(evidence/'project-build-Windows-2/COMPLETED.json');assert build['producer']==q['producer']and build['totals']==dict(tests=31,failures=0,errors=0,skipped=0)
  native=read(evidence/'native-tests-Linux-1/COMPLETED.json');assert native['tests']==31 and native['producer']==q['producer']and native['catalog_credit']==0
  full=read(evidence/'qualification-Linux-1/COMPLETED.json');extra=read(evidence/'extra-workflows-Linux-1/COMPLETED.json');assert full['producer']==extra['producer']==q['producer']and len(full['cases'])==6 and len(extra['cases'])==3
  captures=[]
  for case in full['cases']:
   assert case.get('independent_original_fit');captures.append(('qualification-Linux-1',case['case']))
  for case in extra['cases']:assert case['independent_original_compute']and case['byte_equal_scores'];captures.append(('extra-workflows-Linux-1',case['case']))
  assert captures==[(x['folder'],x['case'])for x in q['cases']]
  for directory,case in captures:
   capture=evidence/directory/(case+'-capture');replay=evidence/directory/(case+'-replay');assert read(capture/'COMPLETED.json')['status']==read(replay/'COMPLETED.json')['status']=='COMPLETE_SCOPED_ORIGINAL_POPCORN_WORKFLOW';assert read(capture/'manifest.json')['producer']==q['producer'];assert read(capture/'canonical.json')==read(replay/'canonical.json');assert read(replay/'REPLAY.json')['all_canonical_rows_equal']and not read(replay/'REPLAY.json')['paper_recipe_admitted']
   if case.endswith('compute-fit'):assert read(capture/'NATIVE-REGRESSION-CHECK.json')['point_delete_pseudo_SE_checked']
  negative=read(evidence/'packaged-failures-Linux-1/COMPLETED.json');assert negative['producer']==q['producer']and negative['controls']==len(negative['records'])==13 and all(r['exit_code']==r['expected']and not r['complete_result_admitted']for r in negative['records'])
  assert read(evidence/'fixtures-1/unit-normalization-1.0.json')['same']is False and read(evidence/'fixtures-1/unit-normalization-1.1.json')['same']is False
 return dict(status='PASS_SOURCE_BOUND_POPCORN_QUALIFICATION',native_tests_per_os=31,full_original_captures=9,fresh_replays=9,independent_original_comparison_cases=9,packaged_negative_controls=13,private_evidence_checked=evidence is not None,new_upstream_family_credit=1,new_native_family_credit=0,whole_goal_complete=False)
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2]);parser.add_argument('--evidence',type=Path);args=parser.parse_args();print(json.dumps(verify(args.repo.resolve(),args.evidence.resolve()if args.evidence else None),indent=2))
