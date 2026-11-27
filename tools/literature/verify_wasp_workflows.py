"""Check standalone WASP source context and optional retained private workflow evidence."""
from pathlib import Path
import argparse,gzip,hashlib,json,zipfile

def pin(path):
 digest=hashlib.sha256();size=0
 with Path(path).open('rb')as stream:
  for block in iter(lambda:stream.read(1048576),b''):size+=len(block);digest.update(block)
 return dict(bytes=size,sha256=digest.hexdigest())
def read(path):return json.loads(Path(path).read_bytes())
def verify(repo,evidence=None):
 folder=repo/'docs/literature/wasp-validation';q=read(folder/'QUALIFICATION.json');assert q['status']=='PASS_SCOPED_ORIGINAL_WASP_MAPPING_WORKFLOW'and q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0 and not q['whole_goal_complete']
 assert q['native_tests_per_os']==355 and q['WASP_tests_per_os']==14 and q['Hornet_regressions_per_os']==341
 assert q['full_workflow_captures']==q['fresh_workflow_replays']==q['independent_direct_original_comparisons']==11 and q['packaged_negative_controls']==10
 assert pin(folder/'source-context.zip')==q['source_archive']
 with zipfile.ZipFile(folder/'source-context.zip')as archive:
  assert len(archive.namelist())==len(q['sources'])and set(archive.namelist())==set(q['sources'])
  for name,expected in q['sources'].items():
   raw=archive.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==expected;assert pin(repo/name)==expected,('Restore archived source context for historical verification',name)
 profile=read(repo/'src/main/resources/org/jlinalg/omics/wasp-mapping/profiles.json')['profiles']['WASP-0.3.4-d3b8447f-BWA-0.7.17'];assert len(profile['WASP_source_files'])==82 and len(profile['BWA_source_files'])==77
 for filename,count in [('original-oracle.json.gz',6),('original-converter-oracle.json.gz',2)]:
  with gzip.open(repo/'src/test/resources/org/jlinalg/sequence/wasp-mapping'/filename,'rb')as stream:oracle=json.load(stream)
  assert len(oracle['cases'])==count and oracle['expected_results_from_project_Java']is False and oracle['WASP_revision']=='d3b8447fd7719fffa00b856fd1f27c845554693e'
 if evidence is not None:
  proof=read(evidence/'QUALIFICATION-PROOF.json');assert proof['public_qualification']==pin(folder/'QUALIFICATION.json')
  for name,expected in proof['retained_files'].items():assert pin(evidence/name)==expected,('Changed private WASP evidence',name)
  assert pin(evidence/q['producer_relative'])==q['producer'];build=read(evidence/'project-build-Windows-5/COMPLETED.json');assert build['producer']==q['producer']and build['totals']==dict(tests=355,failures=0,errors=0,skipped=0)
  native=read(evidence/'native-tests-Linux-3/COMPLETED.json');assert native['tests']==355 and native['catalog_credit']==0
  full=read(evidence/'full-workflows-Linux-3/COMPLETED.json');assert full['producer']==q['producer']and full['cases']==q['cases']and len(full['records'])==23 and all(row['exit_code']==row['expected']==0 for row in full['records'])
  for case in q['cases']:
   capture=evidence/'full-workflows-Linux-3'/(case+'-capture');replay=evidence/'full-workflows-Linux-3'/(case+'-replay');assert read(capture/'COMPLETED.json')['filtering_admitted']is True and read(replay/'COMPLETED.json')['filtering_admitted']is True
   fresh=read(replay/'REPLAY.json');assert fresh['all_canonical_populations_equal']and fresh['final_BAM_and_index_byte_parity']and not fresh['paper_recipe_admitted'];assert read(capture/'manifest.json')['producer']==q['producer']
   assert read(capture/'canonical.json')==read(replay/'canonical.json')
  direct=read(evidence/'direct-original-comparisons-Linux-1/COMPLETED.json');assert direct['cases']==q['cases']and not direct['project_worker_used']and not direct['project_Java_used_for_expected_results']and len(direct['comparisons'])==11
  assert all(len(row['stages'])==8 and not row['expected_results_from_project_Java']for row in direct['comparisons'])
  negative=read(evidence/'packaged-failures-Linux-2/COMPLETED.json');assert negative['producer']==q['producer']and negative['controls']==len(negative['records'])==10 and all(row['exit_code']==row['expected']and not row['complete_result_admitted']for row in negative['records'])
  assert read(evidence/'native-vcf-contract-Linux-1/COMPLETED.json')['cases']==2
 return dict(status='PASS_SCOPED_WASP_SOURCE_AND_WORKFLOW_EVIDENCE',native_tests_per_os=355,actual_original_captures=11,fresh_original_replays=11,independent_direct_original_comparisons=11,packaged_negative_controls=10,private_evidence_checked=evidence is not None,new_upstream_family_credit=1,new_native_family_credit=0,whole_goal_complete=False)
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2]);parser.add_argument('--evidence',type=Path);args=parser.parse_args();print(json.dumps(verify(args.repo.resolve(),args.evidence.resolve()if args.evidence else None),indent=2))
