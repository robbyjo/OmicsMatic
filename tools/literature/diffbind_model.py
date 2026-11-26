"""Deterministic DiffBind admission: three new source reviews, one expanded review, one upstream family."""
from collections import Counter
import copy,html,json,re
from gemma_primary import encoded,require

DATA='docs/literature/omics-2023-2026'
META=[DATA+'/'+name for name in ('papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md')]+['tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']
ADMISSION='docs/literature/diffbind-catalog-admission'
Q='docs/literature/diffbind-validation/QUALIFICATION.json'
GUIDE='docs/diffbind-tools.md'
PROGRAM='src/main/java/org/jlinalg/cli/DiffbindToolsCli.java'
REVIEWED='usage-summary-reviewed; paper-wide-completeness-review-open'
PAPERS={'P020','P046','P155','P163'}
PRIOR_REVIEW=('PMC11525197','diffbind')
SCOPE='Complete BAM/BED consensus counting through original summarizeOverlaps, explicit LIB/RLE/TMM normalization and full/peak library sizes, additive source-factor contrasts with DESeq2 or edgeR, complete reports including source NULL selection, PCA and uniquely labeled correlation heatmaps through original DiffBind 3.24.0 / R 4.6.2. Single-end, paired-end and shared-control workflows are qualified. Java validates complete input BAM/BED populations and projects fitted DESeq2 rows against the full original namespace; upstream code fits and shrinks models. Complete model/count RDS, reports and PNGs are freshly replayed byte-for-byte. One scoped upstream family; native helpers receive no extra family credit. Historical author editions, inferred formulas/defaults, arbitrary R expressions or input RDS, custom GoodpeaksScript, randomized-pair generation, whole-package equivalence and full-paper reproduction remain unqualified.'
ACTION='Require COMPLETED.json, read WARNINGS.md and OUTPUT-AUDIT.json and all original source warnings/logs, then run diffbind-tools --verify with NEW scratch, the exact producing JAR and unchanged bound Linux runtime. Missing or forbidden prerequisites give SKIP3 and skip dependent analysis; supply/install exact prerequisites only where permitted, otherwise retain the skip and required action. FAIL2 admits no complete result: preserve the attempt, correct changed/invalid data/runtime, model failure, capacity exhaustion or cancellation and retry NEW paths. External JVM termination may return a signal exit code; interrupted captures remain incomplete. Original NULL selected reports and their warnings are preserved as valid empty selections, separate from failed models. test_fold changes the original hypothesis test AND report filter; it is not a post-hoc view-only cutoff. Repeated report headers remain ordinal columns. MaxQuant runtime stays skipped and MaxQuant/Andromeda/MaxDIA pending; Cell Ranger remains excluded. Raw XML/process/runtime/cleanup evidence stay private; literature HTMLs remain withheld from Pages. Existing 0.4.1 release downloads predate this source-built command.'
CURRENT='250 papers, 451 labels, 490 provider definitions and 2,731 observations; 1,815 reviewed / 916 pending uses. 170 scoped families (34 native + 136 upstream), 173 including three existing adapters; 251 missing labels, 13 method overlaps and five validated aliases. This admission adds one DiffBind upstream family and three source reviews, expanding the existing P163 review. No paper, observation or provider is added. Native helpers receive no duplicate family credit. The full goal remains open.'

def qualified(q):
 require(q['status']=='PASS_SCOPED_DIFFBIND_BAM_PEAK_MODELS_REPORTS_AND_PLOTS'
  and q['direct_public_source_build']and q['Windows_JUnit']==q['Linux_JUnit']==21 and q['JUnit_skips']==0
  and q['complete_captures']==q['complete_fresh_saved_replays']==4 and q['independent_raw_count_cells']==3600
  and q['full_model_native_projection_rows']==1800 and q['direct_original_reference_files']==14 and q['controlled_refusals']==7
  and q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0 and q['new_source_reviews']==3
  and q['existing_source_review_expanded']==1 and q['original_failures_retained']
  and all(q[k]is False for k in('whole_package_qualified','full_author_roles_implemented','whole_paper_reproduced','whole_goal_complete')),
  'Incomplete or expanded DiffBind qualification')

def applied(role):
 result=copy.deepcopy(role)
 result.update(source_role_review_applied=True,catalog_admitted=True,implementation_available=True,
  implementation_status='SCOPED_CURRENT_324_WORKFLOWS_AVAILABLE_EXACT_AUTHOR_RECIPE_PENDING',literal_reported_version=role['literal_package_version_reported_elsewhere'],
  role_kind='differential binding/accessibility and source-specific QC',runtime_analysis_status='SCOPED_CURRENT_ORIGINAL_324_AVAILABLE',
  full_source_role_implemented=False,paper_edition_reproduced=False,whole_package_qualified=False,whole_paper_reproduced=False,new_family_credit=0,
  remaining_scope=role['remaining_scope']+' '+SCOPE)
 return result

def registry(before):
 result=copy.deepcopy(before);require(len(result)==490,'Different provider population');rows=[r for r in result if r['id']=='diffbind'];require(len(rows)==1 and rows[0]['current_coverage']=='missing','DiffBind provider already admitted or duplicated')
 rows[0].update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE,integration_family='diffbind',family_credit=1)
 return result

def transition(before,roles,q):
 qualified(q);require(set(before)==set(META),'Different predecessor metadata population')
 papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in (0,1,2,5)]
 require((len(papers),len(inventory),sum(len(p['tools'])for p in papers),len(ledger['reviews']))==(250,451,2731,1812),'Different predecessor populations')
 selected=roles['roles'];require(len(selected)==4 and {r['paper_id']for r in selected}==PAPERS and all(r['tool_id']=='diffbind'for r in selected),'Different complete DiffBind source roles')
 prior=[r for r in ledger['reviews']if r['tool_id']=='diffbind'];require(len(prior)==1 and(prior[0]['pmcid'],prior[0]['tool_id'])==PRIOR_REVIEW,'Existing DiffBind review would be duplicated')
 for role in selected:
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools']if u['tool_id']=='diffbind'];require(len(uses)==1 and uses[0]==role['raw_catalog_observation_preserved'],'Raw predecessor observation changed')
  use=uses[0];supporting=copy.deepcopy(use.get('supporting_evidence',[]))
  for anchor in role['primary_anchors']:
   if anchor['paragraph_index']!=use['evidence_paragraph_index']and not any(a.get('paragraph_index')==anchor['paragraph_index']for a in supporting):supporting.append(copy.deepcopy(anchor))
  scope=role['remaining_scope']+' Available scoped profile: '+SCOPE
  use.update(use_summary=role['use_summary'],review_status=REVIEWED,evidence_selection='individually-reviewed-source-role',supporting_evidence=supporting,implementation_scope_note=scope,source_role_qualification=applied(role))
  review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='diffbind',evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['use_summary'],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],supporting_evidence=copy.deepcopy(supporting),source_role_qualification=applied(role))
  existing=[r for r in ledger['reviews']if(r['pmcid'],r['tool_id'])==(paper['pmcid'],'diffbind')]
  if existing:require(len(existing)==1 and(paper['pmcid'],'diffbind')==PRIOR_REVIEW,'Unexpected existing review');existing[0].update(review)
  else:ledger['reviews'].append(review)
 row=[r for r in inventory if r['id']=='diffbind'];require(len(row)==1 and row[0]['implementation_status']==row[0]['current_coverage']=='missing','DiffBind label already implemented or duplicated')
 row[0].update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE,implementation_scope=SCOPE,current_qualification=Q,current_documentation=GUIDE,integration_family='diffbind',family_credit=1,source_build_required=True,released_binary_contains_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,paper_role_qualification={r['paper_id']:applied(r)for r in selected},runtime_analysis_status='SCOPED_WORKFLOWS_AVAILABLE_AUTHOR_RECIPE_AND_OTHER_COMPONENTS_PENDING')
 counts=Counter(r['implementation_status']for r in inventory)
 summary.update(implementation_status_counts=dict(counts),integration_family_counts=dict(scoped=counts['native-subset-validated']+counts['upstream-adapter-validated'],native=counts['native-subset-validated'],upstream=counts['upstream-adapter-validated'],including_existing_adapters=counts['native-subset-validated']+counts['upstream-adapter-validated']+counts['existing-adapter'],validated_alias_labels_excluded_from_extra_credit=counts['validated-family-alias']))
 after=dict(before)
 for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
  raw=encoded(value);after[name]=raw.replace(b'\n',b'\r\n')if before[name].count(b'\n')==before[name].count(b'\r\n')else raw
 notice='<aside id="diffbind-catalog-notice" style="margin:1rem;padding:1rem;border:2px solid #286d5a;background:#edf8f3;color:#163b30;overflow-wrap:anywhere"><h2>DiffBind counting, assessment and parental database workflows</h2><p>'+html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Commands, inputs, source profiles and limitations</a></p></aside>'
 for name in META[6:]:
  page=before[name].decode();require('id="diffbind-catalog-notice"'not in page and'id="allcools-posterior-catalog-notice"'in page,'Different predecessor report')
  if name==META[6]:
   match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Different predecessor report data')
   payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
   require(page.count('${merquryVersion(t)}')==page.count('function renderContent(){')==1,'Different predecessor role renderer')
   helper='function diffbindVersion(t){if(t.tool_id!==\'diffbind\'||!t.source_role_qualification)return merquryVersion(t);const q=t.source_role_qualification;return \'<br><span style="overflow-wrap:anywhere">Reported DiffBind: \'+esc(q.literal_reported_version??\'not reported\')+\'; reported engine: \'+esc(q.inference_engine_reported??\'not reported\')+\'; scoped runtime 3.24.0 available; exact author recipe pending. \'+esc(q.remaining_scope)+\'</span>\';}\n'
   page=page.replace('${merquryVersion(t)}','${diffbindVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
  body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Changed report boundary')
  after[name]=(page[:body.end()]+notice+'<details id="diffbind-prior-catalog"><summary>Before DiffBind admission (historical)</summary>'+page[body.end():marker]+'</details>'+page[marker:]).encode()
 prefix=('# Current DiffBind catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [DiffBind guide](../../diffbind-tools.md).\n\n---\n\nEarlier checkpoints below are historical.\n\n').encode()
 for name in META[3:5]:after[name]=prefix+before[name]
 return after

def check(before,after,roles,q):
 require(after==transition(before,roles,q),'Different deterministic DiffBind transition');old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
 for left,right in zip(old,new,strict=True):
  require({k:v for k,v in left.items()if k!='tools'}=={k:v for k,v in right.items()if k!='tools'},'Paper identity changed');require([u for u in left['tools']if u['tool_id']!='diffbind']==[u for u in right['tools']if u['tool_id']!='diffbind'],'Unrelated observation changed')
 a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']!='diffbind']==[r for r in b if r['id']!='diffbind'],'Unrelated inventory changed')
 prior=json.loads(before[META[5]])['reviews'];reviews=json.loads(after[META[5]])['reviews'];require(len(reviews)==len({(r['pmcid'],r['tool_id'])for r in reviews})==1815,'Wrong review count or duplicate')
 require([r for r in prior if r['tool_id']!='diffbind']==[r for r in reviews if r['tool_id']!='diffbind'],'Unrelated review changed')
 marked={(p['pmcid'],u['tool_id'])for p in new for u in p['tools']if u['review_status']in(REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open')};require(marked=={(r['pmcid'],r['tool_id'])for r in reviews},'Review markers and ledger differ')
 counts=Counter(r['implementation_status']for r in b);require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'],counts['method-overlap'],counts['validated-family-alias'])==(34,136,251,13,5),'Wrong family credit')
 return dict(papers=250,labels=451,provider_definitions=490,observations=2731,reviewed_uses=1815,pending_uses=916,scoped_families=170,native=34,upstream=136,including_existing_adapters=173,missing=251,method_overlap=13,validated_alias_labels=5,new_upstream_family_credit=1,new_native_family_credit=0,new_source_reviews=3,existing_source_review_expanded=1,new_papers_or_observations_or_providers=0,unchanged_unrelated_inventory_rows=450,unchanged_unrelated_observations=2727,unchanged_unrelated_reviews=1811,whole_package_qualified=False,full_author_roles_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)
