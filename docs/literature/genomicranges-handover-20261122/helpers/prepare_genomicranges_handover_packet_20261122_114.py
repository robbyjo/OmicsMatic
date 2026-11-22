from pathlib import Path
import hashlib,json,shutil,subprocess,ast
from prepare_genomicranges_20261122_114 import R,T,B,O,BASE
from handover_inventory_20261122_111 import pin,save,snapshot

def copy(src,dest):
 dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dest)

def main():
 build=B/'normal-gradle-handover-1';result=json.loads((build/'RESULT.json').read_bytes())
 assert result['exit_code']==0 and result['failures']==0
 assert result['suites']['org.jlinalg.genetics.GenomicRangeGeometryReferenceTest']['tests']==21
 d=T/'docs/literature/genomicranges-handover-20261122';d.mkdir()
 (d/'.gitattributes').write_text('* -text\n',encoding='utf-8')
 for pattern in ['*genomicranges*20261122_114.py','*genomicranges*20261122_114.R']:
  for p in (R/'build').glob(pattern):copy(p,d/'helpers'/p.name)
 copy(R/'build/handover_inventory_20261122_111.py',d/'helpers/handover_inventory_20261122_111.py')
 for p in (d/'helpers').glob('*.py'):ast.parse(p.read_bytes(),filename=str(p))
 for dirname,names in {
  'geometry-references-164-1':['STARTED.json','PROCESS.json','stdout','stderr','original/ORIGINAL-OBJECTS.rds','original/RUNTIME.txt'],
  'geometry-feasibility-1':['STARTED.json','PROCESS.json','stdout','stderr'],
  'private-r410-extract-1':['STARTED.json','EXTRACT-PROCESS.json','VERSION-PROCESS.json','RESULT.json','version.stdout','version.stderr'],
  'legacy-r410-intake-1':['RESULT.json','R-INTAKE.json'],
  'normal-gradle-handover-1':['STARTED.json','PROCESS.json','RESULT.json','stdout','stderr'],
 }.items():
  for name in names:copy(B/dirname/name,d/'receipts'/dirname/name)
 for dirname in ['upstream-intake-1','upstream-intake-2']:
  for p in (B/dirname).glob('*/RESULT.json'):copy(p,d/'receipts'/dirname/p.parent.name/p.name)
 screen=json.loads((R/'build/genomicranges-source-screening-20261122-114.json').read_bytes());roles=[]
 for p in screen['papers']:
  row={k:p[k] for k in ['paper_id','title','pmcid','doi','journal','primary_XML_sha256']}
  row['uses']=[{k:u[k] for k in ['tool_id','evidence_section','evidence_paragraph_index','evidence_paragraph_sha256','review_status','version_reported_near_name'] if k in u} for u in p['uses']]
  row['named_paragraphs']=[{'paragraph_index':q['paragraph_index'],'section':q['section'],'sha256':hashlib.sha256(q['text'].encode()).hexdigest(),'characters':len(q['text'])} for q in p['named_paragraphs']]
  roles.append(row)
 save(d/'ROLE-SCREENING.json',{'status':'DEVELOPMENT_SCREEN_NOT_CURATED_ADMISSION','papers':roles,'current_reviews_unchanged':True,'new_catalog_credit':0,'new_reviews':0,'full_primary_text_retained_privately':True})
 oldjar=R/'build/sk-2/build/skat-runtime-1/normal-gradle-8/project-build/cli/omicsmatic-0.4.0.jar'
 acatjar=R/'build/sa-1/build/skat-acat-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar'
 assert pin(oldjar)['sha256']=='d87eb1597fcdd48214a4ee6fb72b7b2e4cb4c7b0ce547d1782bcd4303ae902cc'
 assert pin(acatjar)['sha256']=='9965571a5f24f6d701b4da693d20b8c44e58c4359da54dd932476dc1ee19fd7b'
 newjar=build/'project-build/cli/omicsmatic-0.4.0.jar';assert pin(newjar)==result['development_jar']
 private={'status':'PRIVATE_TRANSFER_REQUIRED_NOT_PERFORMED','all_worktrees':70,'preserve_entire_shared_root_and_build':str(R),'current_owner':str(T),'current_raw_evidence_and_runtime_root':str(B),'private_primary_XML':str(R/'build/literature-review/xml'),'unabridged_role_screening':str(R/'build/genomicranges-source-screening-20261122-114.json'),'existing_read_only_modern_R_library':str(R/'build/omics-sctransform-r'),'private_portable_R410':str(B/'private-r410-extract-1/extracted'),'historical_package_dependencies_installed':False,'publication_receipts':str(O),'producers':[{ 'path':str(p),'pin':pin(p),'scope':scope} for p,scope in [(oldjar,'QUALIFIED_ORIGINAL_SKAT225_KEEP_UNCHANGED'),(acatjar,'QUALIFIED_NATIVE_ACAT_KEEP_UNCHANGED'),(newjar,'DEVELOPMENT_GEOMETRY_COMPONENT_NOT_FULL_ADAPTER')]],'private_historical_bundle':{'path':str(R/'build/git-publish/20261122-handover-111/historical-sources-private.bundle'),'upload_authorized':False},'transfer_raw_null_partial_failed_superseded_invalidated_evidence':True,'preserve_junctions_and_symlinks_explicitly':True}
 save(d/'PRIVATE-TRANSFER.json',private)
 index=T/'docs/literature/HANDOVER_CURRENT.md';old=index.read_text(encoding='utf-8');assert 'GENOMICRANGES.md' not in old[:2500]
 prefix='''**Current: [handover, remaining work and private transfer](HANDOVER_2026-11-22_GENOMICRANGES.md).**
The 250-paper collection is complete; the full implementation goal is unfinished.
Current main includes qualified original SKAT2.2.5 and catalog admission: **148
scoped families (27 native +121 upstream),1661 reviewed/1062 pending uses**;
446 labels,485 definitions,2723 observations. The new GenomicRanges Java component
has original1.64.0 scalar promoter/boundary references (17 cases/4096 artificial
ranges); its saved normal-build result gives actual component/regression coverage.
It is not a complete S4 adapter and adds **zero family credit or reviews**.
Exact1.44.0/1.58.0/1.60.0 source archives and private portable R4.1.0 are saved;
historical dependency installs, full adapter, RUN/VERIFY/REPLAY and admission are
pending. Next: finish GenomicRanges, then high-impact FUSION/g:Profiler and other
gaps, plus1062 use reviews and missed-tool screening. Preserve **70 worktrees**,
old qualified JARs, private runtimes, primary text and complete failed evidence;
GitHub alone is insufficient for machine handover. Read SKIP3/FAIL2 remedies and
the blocked historical-export scope in the handover. Cell Ranger stays excluded;
both unfinished reports stay off Pages. No release or full-goal completion.
Lower entries retain historical counts and are superseded where stated.

'''
 index.write_text(old.replace('# Current toolbox handover\n\n','# Current toolbox handover\n\n'+prefix,1),encoding='utf-8',newline='\n')
 save(d/'EVIDENCE.json',{'status':'NARROW_GEOMETRY_REFERENCE_COMPONENT_HANDOVER','build':result,'original_R_reference':json.loads((T/'src/test/resources/org/jlinalg/genetics/genomicranges-geometry/1.64.0/REFERENCE.json').read_bytes()),'historical_profiles_qualified':False,'full_GenomicRanges_adapter_qualified':False,'new_catalog_credit':0,'new_reviews':0,'whole_goal_complete':False})
 save(d/'STATUS.json',{'status':'COMPONENT_TESTED_FULL_ADAPTER_AND_CURATION_PENDING','base':BASE,'owner':str(T),'branch':'codex/genomicranges-workflows-20261122','scoped_families':148,'native':27,'upstream':121,'papers':250,'reviewed_uses':1661,'pending_uses':1062,'new_catalog_credit':0,'new_reviews':0,'build_tests':result['tests'],'build_failures':result['failures'],'full_GenomicRanges_adapter_qualified':False,'whole_goal_complete':False,'file_pins':{p.relative_to(T).as_posix():pin(p) for p in sorted(d.rglob('*')) if p.is_file()}})
 before=json.loads((O/'WORKTREES-BEFORE.json').read_bytes());after=snapshot();assert len(after)==70
 bypath={x['worktree']:x for x in after}
 assert all(bypath[x['worktree']]==x for x in before)
 save(O/'WORKTREES-PRECOMMIT.json',after)
 save(O/'HANDOVER-PRESERVATION.json',{'all69_prior_owners_exact':True,'old_qualified_producers_exact':True,'root_and_indexes_preserved':True,'current_owner_count':70,'historical_exports_pushed':False})
 tracked=subprocess.run(['git','diff','--name-only'],cwd=T,capture_output=True,text=True,check=True).stdout.splitlines();assert tracked==['docs/literature/HANDOVER_CURRENT.md'],tracked
 print('PASS handover packet prepared;all69 prior owners and both qualified JARs exact;build tests='+str(result['tests']),flush=True)

if __name__=='__main__':main()
