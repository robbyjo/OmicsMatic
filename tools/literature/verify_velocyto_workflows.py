"""Verify archived velocyto source context and optionally all retained private evidence."""
import argparse,hashlib,json,zipfile
from pathlib import Path
CASES={'default-selected','default-inferred','default-multimap','default-storage-uint16','BD-Rhapsody-preparation','multiple-disjoint-BAMs','API64-selected','API64-overflow','API64-inferred'}
def read(path):return json.loads(Path(path).read_bytes())
def pin(path):
 path=Path(path);digest=hashlib.sha256();size=0
 with path.open('rb')as stream:
  for block in iter(lambda:stream.read(1048576),b''):size+=len(block);digest.update(block)
 assert size==path.stat().st_size
 return dict(bytes=size,sha256=digest.hexdigest())
def verify(repo,evidence=None):
 folder=repo/'docs/literature/velocyto-validation';q=read(folder/'QUALIFICATION.json');assert q['schema_version']==1 and q['status']=='QUALIFIED_SCOPED_VELOCYTO_ORIGINAL_COUNT_WORKFLOWS'
 assert(q['native_tests_per_os'],q['full_workflow_captures'],q['fresh_workflow_replays'],q['independent_original_comparison_cases'],q['packaged_negative_controls'])==(20,9,9,9,17)
 assert q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0 and not q['whole_goal_complete']and not q['exact_author_recipes_qualified']and not q['RNA_velocities_computed'];assert pin(folder/'source-context.zip')==q['source_archive']
 with zipfile.ZipFile(folder/'source-context.zip')as archive:
  assert len(archive.namelist())==len(q['sources'])and set(archive.namelist())==set(q['sources'])
  for name,expected in q['sources'].items():
   raw=archive.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==expected;assert pin(repo/name)==expected,('Restore archived context for historical verification',name)
 profiles=read(repo/'src/main/resources/org/jlinalg/omics/velocyto01717/profiles.json')['profiles'];assert set(profiles)=={'velocyto-0.17.17-pypi-1ad65fc5-samtools-1.19.2'}and len(next(iter(profiles.values()))['source_files'])==41
 oracles=read(repo/'src/test/resources/org/jlinalg/singlecell/velocyto01717/original-oracles.json');assert len(oracles['cases'])==6 and oracles['velocyto_version']=='0.17.17'
 assert{row['case']for row in q['cases']}==CASES and len(q['cases'])==9
 if evidence is not None:
  proof=read(evidence/'QUALIFICATION-PROOF.json');assert proof['public_qualification']==pin(folder/'QUALIFICATION.json')
  for name,expected in proof['retained_files'].items():assert pin(evidence/name)==expected,('Changed retained private velocyto evidence',name)
  assert pin(evidence/q['producer_relative'])==q['producer'];build=read(evidence/'project-build-Windows-5/COMPLETED.json');assert build['producer']==q['producer']and build['totals']==dict(tests=20,failures=0,errors=0,skipped=0)
  for name,expected in read(evidence/'project-build-Windows-5/SOURCES.json')['files'].items():assert pin(repo/name)==expected and pin(evidence/'project-build-Windows-5/source'/name)==expected,('Producing build source differs',name)
  native=read(evidence/'native-tests-Linux-2/COMPLETED.json');assert native['tests']==20 and native['producer']==q['producer']and native['new_family_credit']==0
  rows=[]
  for directory in ['full-workflows-Linux-2','full-workflows-Linux-3','full-workflows-Linux-4']:
   full=read(evidence/directory/'COMPLETED.json');assert full['producer']==q['producer']and len(full['cases'])==3 and full['new_family_credit']==0
   for row in full['cases']:
    case=row['case'];assert row['independent_complete_original_comparison']and row['independent_native_preparation_comparison']and row['exact_native_MatrixMarket_values'];rows.append(dict(folder=directory,case=case));capture=evidence/directory/(case+'-capture');replay=evidence/directory/(case+'-replay')
    for saved in [capture,replay]:assert read(saved/'COMPLETED.json')['status']=='COMPLETE_SCOPED_ORIGINAL_VELOCYTO_COUNT_WORKFLOW'and read(saved/'manifest.json')['producer']==q['producer']and not read(saved/'OUTPUT-AUDIT.json')['RNA_velocities_computed']
    assert read(capture/'canonical.json')==read(replay/'canonical.json')and read(replay/'REPLAY.json')['all_canonical_populations_equal']
    comparison=evidence/directory/(case+('-API-COMPARISON.json'if case.startswith('API64')else'-HDF-COMPARISON.json'));assert read(comparison)['status'].startswith('PASS_')
  assert rows==q['cases']
  negative=read(evidence/'packaged-failures-Linux-1/COMPLETED.json');assert negative['producer']==q['producer']and negative['controls']==len(negative['records'])==17 and all(r['exit_code']==r['expected']and not r['complete_result_admitted']for r in negative['records'])
  assert read(evidence/'full-workflows-Linux-1/FAILURE.json')['new_family_credit']==0 and read(evidence/'full-workflows-Linux-1-HDF-DIFFERENCES.json')
 return dict(status='PASS_SOURCE_BOUND_VELOCYTO_QUALIFICATION',native_tests_per_os=20,full_original_captures=9,fresh_replays=9,independent_original_comparison_cases=9,packaged_negative_controls=17,private_evidence_checked=evidence is not None,new_upstream_family_credit=1,new_native_family_credit=0,RNA_velocities_computed=False,whole_goal_complete=False)
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2]);parser.add_argument('--evidence',type=Path);args=parser.parse_args();print(json.dumps(verify(args.repo.resolve(),args.evidence.resolve()if args.evidence else None),indent=2))
