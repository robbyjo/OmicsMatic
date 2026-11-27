"""Check archived GraphTyper qualification context and optional retained private evidence.

This is structural evidence verification. Fresh scientific reruns use the exact
producing JAR's graphtyper-tools --verify and the unchanged permitted runtime.
"""
import argparse,hashlib,json,zipfile
from pathlib import Path
TAGS={'v2.7.5','v2.7.1','v2.0-beta','v1.4'}
STATUS='COMPLETE_SCOPED_ORIGINAL_GRAPHTYPER_JOINT_CALLING'

def read(path):return json.loads(Path(path).read_bytes())

def pin(path):
 path=Path(path);digest=hashlib.sha256();size=0
 with path.open('rb')as stream:
  for block in iter(lambda:stream.read(1048576),b''):size+=len(block);digest.update(block)
 assert size==path.stat().st_size
 return dict(bytes=size,sha256=digest.hexdigest())

def saved(root,producer):
 done=read(root/'COMPLETED.json');assert done['status']==STATUS and not done['author_recipe_qualified'];assert not(root/'FAILURE.json').exists()and not(root/'WORKER-FAILURE.json').exists();assert pin(root/'manifest.json')==done['manifest'];manifest=read(root/'manifest.json');assert manifest['producer']==producer and not manifest['author_recipe_qualified']
 members=list(root.rglob('*'));assert all(not p.is_symlink()for p in members),'Unexpected link in saved evidence';actual={p.relative_to(root).as_posix():pin(p)for p in members if p.is_file()and p.relative_to(root).as_posix()not in ['manifest.json','COMPLETED.json']};assert set(actual)==set(manifest['files'])
 for name,row in manifest['files'].items():assert row['kind']=='file'and actual[name]=={k:row[k]for k in ['bytes','sha256']},('Saved file changed',str(root),name)
 assert read(root/'OUTPUT-AUDIT.json')['status']==STATUS

def cases(evidence):
 result=[];full=read(evidence/'full-workflows-Linux-3/COMPLETED.json');assert len(full['runs'])==4 and not full['new_family_credit'];discovery=read(evidence/'independent-discovery-comparison-Linux-1/COMPLETED.json');assert len(discovery['runs'])==8 and{r['edition']for r in discovery['runs']}==TAGS
 for row in discovery['runs']:assert row['all_non_clock_headers_and_every_data_field_equal']and row['all_literal_GT_alleles_independently_match_pysam']and row['native_record_table_allele_counts_and_raw_field_hashes_match']
 for tag in sorted(TAGS):result.append(dict(case='discovery/'+tag,capture='full-workflows-Linux-3/'+tag+'-capture',replay='full-workflows-Linux-3/'+tag+'-replay'))
 for tag,folder in [('v2.7.5','initialization-v275-Linux-1'),('v2.7.1','initialization-v271-Linux-1'),('v2.0-beta','initialization-beta-Linux-2'),('v1.4','initialization-v14-Linux-2')]:
  init=read(evidence/folder/'COMPLETED.json');expected={'discovery-with-prior'}if tag=='v1.4'else{'discovery-with-prior','genotype-only'};assert init['tag']==tag and{r['mode']for r in init['runs']}==expected
  for row in init['runs']:
   for comparison in row['comparisons']:assert comparison['complete_non_clock_headers_and_data_fields_match']and comparison['all_GT_alleles_independently_match_pysam']and comparison['all_native_allele_counts_and_record_field_hashes_match']
   result.append(dict(case='initialization/'+tag+'/'+row['mode'],capture=folder+'/'+row['mode']+'/capture',replay=folder+'/'+row['mode']+'/replay'))
 cram=read(evidence/'cram-multitarget-Linux-1/COMPLETED.json');assert{r['tag']for r in cram['runs']}=={'v2.7.5','v2.7.1'}
 for row in cram['runs']:
  assert len(row['comparisons'])==4 and{r['target']for r in row['comparisons']}=={0,1}
  for c in row['comparisons']:assert c['complete_non_clock_headers_and_data_fields_match']and c['all_GT_alleles_independently_match_pysam']and c['all_native_allele_counts_and_record_field_hashes_match']
  result.append(dict(case='cram-multitarget/'+row['tag'],capture='cram-multitarget-Linux-1/'+row['tag']+'/capture',replay='cram-multitarget-Linux-1/'+row['tag']+'/replay'))
 node=read(evidence/'legacy-node-policies-Linux-1/COMPLETED.json');assert len(node['runs'])==2;failed=next(r for r in node['runs']if r['tag']=='v2.0-beta');assert not failed['analysis_admitted'];root=evidence/'legacy-node-policies-Linux-1/v2.0-beta/capture';assert read(root/'FAILURE.json')['status']=='FAIL2_NO_COMPLETE_GRAPHTYPER_JOINT_CALLING'and not(root/'COMPLETED.json').exists();log=(root/'engine/logs/000000-original-node-stderr').read_text();assert'Unsorted positions'in log and'final_hap_calls.vcf.gz'in log and'tbx_index_build failed'in log
 good=next(r for r in node['runs']if r['tag']=='v1.4');assert good['comparison']['complete_non_clock_headers_and_data_fields_match']and good['comparison']['all_GT_alleles_independently_match_pysam'];result.append(dict(case='original-node/v1.4',capture='legacy-node-policies-Linux-1/v1.4/capture',replay='legacy-node-policies-Linux-1/v1.4/replay'));assert len(result)==14 and len({r['case']for r in result})==14;return result

def verify(repo,evidence=None):
 folder=repo/'docs/literature/graphtyper-validation';q=read(folder/'QUALIFICATION.json');assert q['schema_version']==1 and q['status']=='QUALIFIED_SCOPED_ORIGINAL_GRAPHTYPER_JOINT_CALLING';assert(q['native_tests_per_os'],q['quality_CPP_oracle_rows'],q['full_workflow_captures'],q['fresh_workflow_replays'],q['independent_original_comparison_cases'],q['packaged_negative_controls'])==(25,712,14,14,14,12);assert q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0 and not q['whole_goal_complete']and not q['exact_author_recipes_qualified'];assert pin(folder/'source-context.zip')==q['source_archive']
 with zipfile.ZipFile(folder/'source-context.zip')as archive:
  assert len(archive.namelist())==len(q['sources'])and set(archive.namelist())==set(q['sources'])
  for name,expected in q['sources'].items():
   raw=archive.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==expected;assert pin(repo/name)==expected,('Restore archived context for historical verification; never refresh old seals',name)
 profiles=read(repo/'src/main/resources/org/jlinalg/omics/graphtyper/profiles.json')['profiles'];assert{p['tag']for p in profiles.values()}==TAGS and len(profiles)==4;old=next(p for p in profiles.values()if p['tag']=='v1.4');assert old['internal_version']=='1.3'and old['licence']=='GPL-3.0'and old['pipeline_commit']=='e7ec5ab56a1086ca00cbc88a0e75cf060f960c8e'
 for p in profiles.values():assert not p['scientific_source_modified']and not p['author_recipe_qualified'];assert bool(p['original_helper_binaries'])==p['legacy']
 if evidence is not None:
  proof=read(evidence/'QUALIFICATION-PROOF.json');assert proof['public_qualification']==pin(folder/'QUALIFICATION.json')
  for name,expected in proof['retained_files'].items():assert pin(evidence/name)==expected,('Retained private evidence changed',name)
  assert pin(evidence/q['producer_relative'])==q['producer'];build=read(evidence/'project-build-Windows-5/COMPLETED.json');assert build['status']=='PASS_GRAPHTYPER_PROJECT_BUILD_AND_NATIVE_CONTROL_TESTS_ONLY'and build['producer']==q['producer']and build['totals']==dict(tests=25,failures=0,errors=0,skipped=0)
  for name,expected in read(evidence/'project-build-Windows-5/SOURCES.json')['files'].items():assert pin(repo/name)==expected and pin(evidence/'project-build-Windows-5/source'/name)==expected
  linux=read(evidence/'scoped-tests-Linux-2/COMPLETED.json');assert linux['tests']==25 and not linux['new_family_credit'];context=read(evidence/'scoped-tests-Linux-2/CONTEXT.json')
  for name,expected in context['sources'].items():assert pin(repo/name)==expected
  oracle=read(evidence/'native-quality-oracles-Linux-1/COMPLETED.json');assert oracle['rows']==712 and not oracle['native_Java_used_for_expected'];built=read(evidence/'original-v1_4-build-Linux-12/COMPLETED.json');assert built['source_tag']=='v1.4'and built['source_internal_version']=='1.3'and not built['source_patches_applied'];actual=cases(evidence);assert actual==q['cases']
  for row in actual:
   capture=evidence/row['capture'];replay=evidence/row['replay'];saved(capture,q['producer']);saved(replay,q['producer']);assert read(capture/'canonical.json')==read(replay/'canonical.json')and read(replay/'REPLAY.json')['all_canonical_populations_equal']
  negative=read(evidence/'packaged-negatives-Linux-1/COMPLETED.json');assert len(negative['controls'])==11 and negative['producer']==q['producer']and all(r['exit_code']in [2,3]and not r['analysis_admitted']for r in negative['controls']);assert read(evidence/'full-workflows-Linux-1/FAILURE.json')['new_family_credit']==0;assert read(evidence/'original-legacy-beta-controls-Linux-1/FAILURE.json')['analysis_admitted']is False
 return dict(status='PASS_SOURCE_BOUND_GRAPHTYPER_QUALIFICATION',native_tests_per_os=25,quality_CPP_oracle_rows=712,full_original_captures=14,fresh_replays=14,independent_original_comparison_cases=14,packaged_negative_controls=12,private_evidence_checked=evidence is not None,new_upstream_family_credit=1,new_native_family_credit=0,whole_goal_complete=False)

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2]);parser.add_argument('--evidence',type=Path);args=parser.parse_args();print(json.dumps(verify(args.repo.resolve(),args.evidence.resolve()if args.evidence else None),indent=2))
