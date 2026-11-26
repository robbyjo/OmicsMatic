"""Pure, bounded ALLCools admission. Existing four source reviews receive no new review credit."""
from collections import Counter
import copy,html,json,re
from gemma_primary import encoded,require
DATA='docs/literature/omics-2023-2026'
META=[DATA+'/'+n for n in ('papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md')]+['tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']
ADMISSION='docs/literature/allcools-posterior-catalog-admission'
Q='docs/literature/allcools-posterior-validation/QUALIFICATION.json'
GUIDE='docs/literature/ALLCOOLS_POSTERIOR.md'
PROGRAM='src/main/java/org/jlinalg/cli/AllcoolsPosteriorCli.java'
PAIRS={('P047','allcools'),('P053','allcools'),('P060','allcools'),('P061','allcools')}
SCOPE=('Native Java ALLCools 1.0.8 prepared-count posterior fractions and M-value transforms, with complete contiguous float64 feature axes per cell/type, '
 'population variance, unmodified moment priors, explicit NumPy/xarray clipping, complete count/prior/output accounting, exact producer capture and portable numerical replay. '
 'The xarray branch can replace NaN with the clip value; raw posteriors and replacements remain visible. '
 'Near-zero normalized-M divisors have an explicit numerical refusal; no automatic rescaling or fallback occurs. '
 'This is one scoped native family. Raw ALLC/Tabix/Zarr IO, tensor/Dask layouts, dataset construction, feature selection, clustering, pseudobulk merging, '
 'SeuratIntegration, label transfer, BaseDS and DMS/DMR workflows remain separate. A selected comparison tag does not authenticate an author commit.')
ACTION=('Inspect WARNINGS.md, OUTPUT-AUDIT.json and outputs/priors.tsv, especially zero coverage, degenerate priors, NaN/Inf values and xarray NaN-to-clip replacements. '
 'Require COMPLETED.json and saved verification with the exact producing JAR. FAIL2 admits no complete result: preserve the attempt, correct the named '
 'input, dimensions, capacity, cancellation, numeric or consistency problem and retry NEW paths. SKIP3 means the dependent analysis was skipped because '
 'a permitted prerequisite is unavailable; supply it where allowed or retain the skip. The native command needs no upstream runtime. '
 'Do not repair priors, impute counts or refresh old seals. MaxQuant runtime stays skipped; MaxQuant/Andromeda/MaxDIA remain pending. Cell Ranger remains excluded. '
 'Raw evidence stays private; literature reports remain withheld from Pages.')
CURRENT=('250 papers, 451 labels, 490 provider definitions and 2,731 observations; 1,809 reviewed / 922 pending uses. '
 '168 scoped families (34 native + 134 upstream), 171 including three existing adapters. 253 labels remain missing, '
 '13 method overlaps and five validated aliases. This checkpoint adds one scoped native ALLCools family and zero papers, observations, providers or source reviews. '
 'P047/P053 have a prepared posterior subset available; P060/P061 author roles remain pending. The full goal remains open.')
GAPS={
 'P047':'Prepared posterior fractions are available using explicit selected-tag settings. The author edition is unreported. Raw count/dataset preparation, exact weighted-neighbor integration/transfer and pseudobulk DMR routines/settings remain pending.',
 'P053':'Prepared posterior mCH/mCG fractions are available using the selected 1.0.8 comparison. Dataset construction, CEF permutation selection and ALLC merging remain pending. Keep YAP/aligners, openTSNE/UMAP and the separate methylpy DMR caller and 500 bp / difference >=0.3 recipe distinct.',
 'P060':'This integration/transfer/imputation role remains pending. Native posterior fractions do not implement SeuratIntegration, CCA/anchors, two-round RNA/MERFISH integration or multi-million-cell scale. Edition and complete settings are unreported.',
 'P061':'The cataloged dataset, clustering, merging, BaseDS, DMS/DMR and integration roles remain pending. The selected tag has generate_base_ds but no generate-base-ds CLI registration; resolve the updated author wrapper. Preserve paired CpGs, coverage caps 50 per sample / 3,000 total, and 250 bp / PCC >0.3 aggregation. Native posterior fractions do not implement these roles.'}

def qualified(q):
 require(q['status']=='PASS_SCOPED_NATIVE_ALLCOOLS_PREPARED_POSTERIOR'and q['direct_public_source_build']
  and q['Windows_tests']==q['Linux_tests']and q['Windows_tests']>=98 and q['JUnit_skips']==0
  and q['original_source_cases']==384 and q['original_selected_functions_executed']
  and q['source_cases_matched']==376 and q['source_cases_explicitly_refused']==8 and q['source_numeric_comparisons']==205498
  and q['typed_captures_each_OS']==q['foreign_saved_replays_each_OS']==5
  and q['corrupted_capture_refusals_each_OS']==8 and q['resource_refusals_each_OS']==q['unavailable_source_skips_each_OS']==1
  and q['unstable_M_normalization_refusals_each_OS']==1
  and q['new_native_family_credit']==1 and q['new_upstream_family_credit']==q['new_source_reviews']==0
  and q['original_source_commit']=='2a77f30bf911726de10fe6cc4971331f023d9e68'
  and q['only_changed_prior_packaged_member']=='org/jlinalg/cli/JLinAlgCli.class'
  and q['MIT_and_BSD_notices_packaged']and not q['private_probes_packaged']
  and all(q[k]is False for k in ('whole_package_qualified','full_author_roles_implemented','whole_paper_reproduced','whole_goal_complete','dataset_generation_adapter_qualified')),
  'Incomplete or expanded ALLCools qualification')

def role(previous,paper):
 r=copy.deepcopy(previous);available=paper in ('P047','P053')
 r.update(implementation_available=available,native_prepared_posterior_available=available,native_prepared_insulation_available=False,
  remaining_scope=GAPS[paper],runtime_analysis_status='SCOPED_NATIVE_PREPARED_POSTERIOR_AVAILABLE_AUTHOR_ROLE_PENDING'if available else'ROLE_IMPLEMENTATION_PENDING_NATIVE_POSTERIOR_NOT_THIS_ROLE',
  full_source_role_implemented=False,paper_edition_reproduced=False,whole_package_qualified=False,whole_paper_reproduced=False,new_family_credit=0)
 return r

def registry(before):
 rows=copy.deepcopy(before);require(len(rows)==490,'Different provider population');r=[r for r in rows if r['id']=='allcools'];require(len(r)==1 and r[0]['current_coverage']=='missing','Provider already admitted or duplicated')
 r[0].update(current_coverage='native-subset-validated',existing_source=PROGRAM,coverage_note=SCOPE,integration_family='allcools',family_credit=1);return rows

def transition(before,q):
 qualified(q);papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in (0,1,2,5)]
 require((len(papers),len(inventory),sum(len(p['tools'])for p in papers),len(ledger['reviews']))==(250,451,2731,1809),'Different predecessor population')
 targets=[]
 for paper in papers:
  for use in paper['tools']:
   if use['tool_id']!='allcools':continue
   require((paper['paper_id'],'allcools')in PAIRS and 'source_role_qualification'in use,'Unexpected or unreviewed ALLCools observation')
   targets.append((paper['paper_id'],'allcools'));scope=GAPS[paper['paper_id']]+' Available native scope: '+SCOPE
   use['source_role_qualification']=role(use['source_role_qualification'],paper['paper_id']);use['implementation_scope_note']=scope
   reviews=[r for r in ledger['reviews']if(r['pmcid'],r['tool_id'])==(paper['pmcid'],'allcools')];require(len(reviews)==1,'Missing/duplicate existing ALLCools review')
   reviews[0]['source_role_qualification']=copy.deepcopy(use['source_role_qualification']);reviews[0]['implementation_scope_note']=scope
 require(set(targets)==PAIRS and len(targets)==4,'Different ALLCools source roles')
 row=[r for r in inventory if r['id']=='allcools'];require(len(row)==1 and row[0]['implementation_status']==row[0]['current_coverage']=='missing','ALLCools already admitted or duplicated')
 row[0].update(current_coverage='native-subset-validated',implementation_status='native-subset-validated',existing_source=PROGRAM,implementation_source=PROGRAM,
  coverage_note=SCOPE,implementation_scope=SCOPE,current_qualification=Q,current_documentation=GUIDE,integration_family='allcools',family_credit=1,
  source_build_required=True,released_binary_contains_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,
  runtime_analysis_status='SCOPED_NATIVE_PREPARED_POSTERIOR_AVAILABLE_BROADER_WORKFLOWS_PENDING',
  validation_note='384 original-source synthetic cases, complete native captures, portable full replay and corruption/resource/skip controls on both operating systems; see the exact qualification for matches and explicit numerical refusals. No dataset or full-author-workflow adapter credit.',
  paper_role_qualification={pid:role(r,pid)for pid,r in row[0]['paper_role_qualification'].items()})
 counts=Counter(r['implementation_status']for r in inventory)
 summary.update(implementation_status_counts=dict(counts),integration_family_counts=dict(scoped=counts['native-subset-validated']+counts['upstream-adapter-validated'],native=counts['native-subset-validated'],upstream=counts['upstream-adapter-validated'],including_existing_adapters=counts['native-subset-validated']+counts['upstream-adapter-validated']+counts['existing-adapter'],validated_alias_labels_excluded_from_extra_credit=counts['validated-family-alias']))
 after=dict(before)
 for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
  raw=encoded(value);after[name]=raw.replace(b'\n',b'\r\n')if before[name].count(b'\n')==before[name].count(b'\r\n')else raw
 notice='<aside id="allcools-posterior-catalog-notice" style="margin:1rem;padding:1rem;border:2px solid #286d5a;background:#edf8f3;color:#163b30;overflow-wrap:anywhere"><h2>Native prepared methylation posterior fractions available</h2><p>'+html.escape(CURRENT)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Inputs, commands and pending ALLCools workflows</a></p></aside>'
 for name in META[6:]:
  page=before[name].decode();require('id="allcools-posterior-catalog-notice"'not in page and'id="contact-insulation-catalog-notice"'in page,'Different predecessor report')
  if name==META[6]:
   m=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(m and json.loads(m[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Different embedded report data')
   payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:m.start(2)]+payload+page[m.end(2):]
   require(page.count('${contactVersion(t)}')==page.count('function renderContent(){')==1,'Different role renderer')
   helper="function allcoolsVersion(t){if(t.tool_id!=='allcools'||!t.source_role_qualification)return contactVersion(t);const q=t.source_role_qualification;return '<br><span class=\"allcools-version\" style=\"overflow-wrap:anywhere\">Role: '+esc(q.role_kind)+'; Reported package version: '+esc(q.literal_reported_version===null?'unreported':q.literal_reported_version)+'; '+(q.native_prepared_posterior_available?'Native prepared posterior subset available; full author role pending':'This author role remains pending')+'; '+esc(q.remaining_scope)+'</span>';}\n"
   page=page.replace('${contactVersion(t)}','${allcoolsVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
  body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Changed report body boundary')
  after[name]=(page[:body.end()]+notice+'<details id="allcools-posterior-prior-catalog"><summary>Before native prepared methylation admission (historical)</summary>'+page[body.end():marker]+'</details>'+page[marker:]).encode()
 prefix=('# Current native ALLCools posterior catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [ALLCools posterior guide](../ALLCOOLS_POSTERIOR.md).\n\n---\n\nEarlier checkpoints below are historical.\n\n').encode()
 for name in META[3:5]:after[name]=prefix+before[name]
 return after

def check(before,after,q):
 require(after==transition(before,q),'Different deterministic ALLCools transition')
 old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
 for a,b in zip(old,new):
  require({k:v for k,v in a.items()if k!='tools'}=={k:v for k,v in b.items()if k!='tools'},'Paper identity changed')
  require([u for u in a['tools']if u['tool_id']!='allcools']==[u for u in b['tools']if u['tool_id']!='allcools'],'Unrelated observation changed')
 for i,key in ((1,None),(5,'reviews')):
  a,b=json.loads(before[META[i]]),json.loads(after[META[i]])
  if key:a,b=a[key],b[key]
  target='id'if key is None else'tool_id';require([r for r in a if r[target]!='allcools']==[r for r in b if r[target]!='allcools'],'Unrelated inventory/review changed')
 inv=json.loads(after[META[1]]);counts=Counter(r['implementation_status']for r in inv)
 require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'],counts['method-overlap'],counts['validated-family-alias'])==(34,134,253,13,5),'Incorrect family credit')
 ledger=json.loads(after[META[5]])['reviews'];require(len(ledger)==len({(r['pmcid'],r['tool_id'])for r in ledger})==1809,'Duplicate or additional source review')
 return dict(papers=250,labels=451,provider_definitions=490,observations=2731,reviewed_uses=1809,pending_uses=922,scoped_families=168,native=34,upstream=134,including_existing_adapters=171,missing=253,method_overlap=13,validated_alias_labels=5,new_native_family_credit=1,new_upstream_family_credit=0,new_source_reviews=0,new_papers_or_observations_or_providers=0,unchanged_unrelated_inventory_rows=450,unchanged_unrelated_observations=2727,unchanged_unrelated_reviews=1805,prepared_posterior_source_roles=2,other_ALLCools_author_roles_pending=2,whole_package_qualified=False,full_author_roles_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)
