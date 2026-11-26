"""Deterministic Merqury admission: three new source reviews, one expanded review, one upstream family."""
from collections import Counter
import copy,html,json,re
from gemma_primary import encoded,require

DATA='docs/literature/omics-2023-2026'
META=[DATA+'/'+name for name in ('papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md')]+['tools/literature/reviewed-uses.json','site/literature-review.html','site/literature-warnings.html']
ADMISSION='docs/literature/merqury-catalog-admission'
Q='docs/literature/merqury-validation/QUALIFICATION.json'
GUIDE='docs/MERQURY_TOOLS.md'
PROGRAM='src/main/java/org/jlinalg/cli/MerquryToolsCli.java'
REVIEWED='usage-summary-reviewed; paper-wide-completeness-review-open'
PAPERS={'P101','P164','P171','P248'}
PRIOR_REVIEW=('PMC12597820','merqury')
SCOPE=('Complete ordinary k-mer counting, assembly QV/completeness/copy and assembly spectra, per-sequence QV/error tracks, '
       'optional defined source false duplication, and parental hap-mer construction through explicitly bound Merqury/Meryl workflows. '
       'Original1.1/1.3 controls remain separate from explicitly selected current1.4.1 native-boundary/palette mode. '
       'Java checks complete count populations, every position track and source equations; original binary databases are freshly decoded on admission and replay. '
       'Legacy1.1 TDF datasets are fully compared with deterministic coverage reconstructed from validated BED/FAI. '
       'This is one scoped upstream family with native helpers receiving no extra family credit. Homopolymer compression, full trio phasing, '
       'assembler execution, inferred author parameters, whole-package equivalence and full-paper reproduction remain unqualified.')
ACTION=('Require COMPLETED.json, read WARNINGS.md and OUTPUT-AUDIT.json, and run merqury-tools --verify with a NEW scratch path, '
        'the exact producing JAR and unchanged bound Linux runtime. Missing/forbidden prerequisites give SKIP3 and skip dependent analyses; '
        'supply/install exact prerequisites only where permitted, otherwise retain the skip. FAIL2 admits no complete result: preserve the attempt, '
        'correct the named data/runtime, original-process, capacity, cancellation, undefined-rate or consistency failure and retry NEW paths. '
        'Original shell exit0 is insufficient; low-k-mer lookup and plotting failures remain retained. Inf/NaN and source1.1 21-base BED conventions '
        'remain explicit. Native first-depth output is not a repaired full ploidy report. No source fallback or source-version/compression inference occurs. '
        'MaxQuant runtime stays skipped and MaxQuant/Andromeda/MaxDIA pending; Cell Ranger remains excluded. Raw contexts/process/runtime/cleanup evidence '
        'stay private, literature HTMLs remain withheld from Pages, and earlier0.4.1 release downloads predate this source-built command.')
CURRENT=('250 papers, 451 labels, 490 provider definitions and 2,731 observations; 1,812 reviewed / 919 pending uses. '
         '169 scoped families (34 native + 135 upstream), 172 including three existing adapters; 252 missing labels, 13 method overlaps and five validated aliases. '
         'This admission adds one Merqury upstream family and three source reviews, expanding the existing P164 review. '
         'No paper, observation or provider is added. Native helpers receive no duplicate family credit. The full goal remains open.')

def qualified(q):
 require(q['status']=='PASS_SCOPED_MERQURY_COUNT_ASSESSMENT_AND_PARENTAL_DATABASES'
  and q['direct_public_source_build']and q['Windows_JUnit']==q['Linux_JUnit']==31 and q['JUnit_skips']==0
  and q['original_effective_depth_cases']==614 and q['original_false_duplication_reports']==6
  and q['complete_captures']==q['fresh_binary_numerical_saved_replays']==9
  and q['source_bug_refusals']==2 and q['corruption_refusals']==3 and q['missing_runtime_skips']==1
  and q['owned_process_limit_refusals']==q['owned_cancellation_refusals']==q['undefined_duplication_refusals']==1
  and q['only_changed_prior_packaged_member']=='org/jlinalg/cli/JLinAlgCli.class'
  and q['new_upstream_family_credit']==1 and q['new_native_family_credit']==0 and q['new_source_reviews']==3
  and not q['private_probes_packaged']and q['original_failures_retained']
  and all(q[k]is False for k in ('whole_package_qualified','full_author_roles_implemented','whole_paper_reproduced','whole_goal_complete')),
  'Incomplete or expanded Merqury qualification')

def applied(role):
 result=copy.deepcopy(role)
 result.update(source_role_review_applied=True,catalog_admitted=True,implementation_available=True,
  implementation_status='SCOPED_WORKFLOWS_AVAILABLE_EXACT_AUTHOR_RECIPE_PENDING',literal_reported_version=role['literal_package_version_reported_elsewhere'],
  role_kind='assembly assessment and parental k-mer preparation'if role['paper_id']=='P164'else'assembly assessment',runtime_analysis_status='SCOPED_ORIGINAL_AND_EXPLICIT_CURRENT_NATIVE_PROFILE_AVAILABLE',
  full_source_role_implemented=False,paper_edition_reproduced=False,whole_package_qualified=False,whole_paper_reproduced=False,new_family_credit=0,
  remaining_scope=role['remaining_scope']+' '+SCOPE)
 return result

def registry(before):
 result=copy.deepcopy(before);require(len(result)==490,'Different provider population');rows=[r for r in result if r['id']=='merqury'];require(len(rows)==1 and rows[0]['current_coverage']=='missing','Merqury provider already admitted or duplicated')
 rows[0].update(current_coverage='upstream-adapter-validated',existing_source=PROGRAM,coverage_note=SCOPE,integration_family='merqury',family_credit=1)
 return result

def transition(before,roles,q):
 qualified(q);require(set(before)==set(META),'Different predecessor metadata population')
 papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in (0,1,2,5)]
 require((len(papers),len(inventory),sum(len(p['tools'])for p in papers),len(ledger['reviews']))==(250,451,2731,1809),'Different predecessor populations')
 selected=roles['roles'];require(len(selected)==4 and {r['paper_id']for r in selected}==PAPERS and all(r['tool_id']=='merqury'for r in selected),'Different complete Merqury source roles')
 prior=[r for r in ledger['reviews']if r['tool_id']=='merqury'];require(len(prior)==1 and(prior[0]['pmcid'],prior[0]['tool_id'])==PRIOR_REVIEW,'Existing Merqury review would be duplicated')
 for role in selected:
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools']if u['tool_id']=='merqury'];require(len(uses)==1 and uses[0]==role['raw_catalog_observation_preserved'],'Raw predecessor observation changed')
  use=uses[0];supporting=copy.deepcopy(use.get('supporting_evidence',[]))
  for anchor in role['primary_anchors']:
   if anchor['paragraph_index']!=use['evidence_paragraph_index']and not any(a.get('paragraph_index')==anchor['paragraph_index']for a in supporting):supporting.append(copy.deepcopy(anchor))
  scope=role['remaining_scope']+' Available scoped profile: '+SCOPE
  use.update(use_summary=role['use_summary'],review_status=REVIEWED,evidence_selection='individually-reviewed-source-role',supporting_evidence=supporting,implementation_scope_note=scope,source_role_qualification=applied(role))
  review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='merqury',evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=role['use_summary'],implementation_scope_note=scope,usage_context=use['usage_context'],version_reported_near_name=use['version_reported_near_name'],supporting_evidence=copy.deepcopy(supporting),source_role_qualification=applied(role))
  existing=[r for r in ledger['reviews']if(r['pmcid'],r['tool_id'])==(paper['pmcid'],'merqury')]
  if existing:require(len(existing)==1 and(paper['pmcid'],'merqury')==PRIOR_REVIEW,'Unexpected existing review');existing[0].update(review)
  else:ledger['reviews'].append(review)
 row=[r for r in inventory if r['id']=='merqury'];require(len(row)==1 and row[0]['implementation_status']==row[0]['current_coverage']=='missing','Merqury label already implemented or duplicated')
 row[0].update(current_coverage='upstream-adapter-validated',implementation_status='upstream-adapter-validated',existing_source=PROGRAM,implementation_source=PROGRAM,coverage_note=SCOPE,implementation_scope=SCOPE,current_qualification=Q,current_documentation=GUIDE,integration_family='merqury',family_credit=1,source_build_required=True,released_binary_contains_workflow=False,whole_tool_qualified=False,whole_paper_reproduced=False,paper_role_qualification={r['paper_id']:applied(r)for r in selected},runtime_analysis_status='SCOPED_WORKFLOWS_AVAILABLE_AUTHOR_RECIPE_AND_OTHER_COMPONENTS_PENDING')
 counts=Counter(r['implementation_status']for r in inventory)
 summary.update(implementation_status_counts=dict(counts),integration_family_counts=dict(scoped=counts['native-subset-validated']+counts['upstream-adapter-validated'],native=counts['native-subset-validated'],upstream=counts['upstream-adapter-validated'],including_existing_adapters=counts['native-subset-validated']+counts['upstream-adapter-validated']+counts['existing-adapter'],validated_alias_labels_excluded_from_extra_credit=counts['validated-family-alias']))
 after=dict(before)
 for name,value in ((META[0],papers),(META[1],inventory),(META[2],summary),(META[5],ledger)):
  raw=encoded(value);after[name]=raw.replace(b'\n',b'\r\n')if before[name].count(b'\n')==before[name].count(b'\r\n')else raw
 notice='<aside id="merqury-catalog-notice" style="margin:1rem;padding:1rem;border:2px solid #286d5a;background:#edf8f3;color:#163b30;overflow-wrap:anywhere"><h2>Merqury counting, assessment and parental database workflows</h2><p>'+html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Commands, inputs, source profiles and limitations</a></p></aside>'
 for name in META[6:]:
  page=before[name].decode();require('id="merqury-catalog-notice"'not in page and'id="allcools-posterior-catalog-notice"'in page,'Different predecessor report')
  if name==META[6]:
   match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Different predecessor report data')
   payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
   require(page.count('${allcoolsVersion(t)}')==page.count('function renderContent(){')==1,'Different predecessor role renderer')
   helper="function merquryVersion(t){if(t.tool_id!=='merqury'||!t.source_role_qualification)return allcoolsVersion(t);const q=t.source_role_qualification;return '<br><span style=\"overflow-wrap:anywhere\">Reported Merqury: '+esc(q.literal_reported_version)+'; Roles: '+esc(q.distinct_uses.join('; '))+'; Scoped workflow available; exact author recipe pending. '+esc(q.remaining_scope)+'</span>';}\n"
   page=page.replace('${allcoolsVersion(t)}','${merquryVersion(t)}',1).replace('function renderContent(){',helper+'function renderContent(){',1)
  body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Changed report boundary')
  after[name]=(page[:body.end()]+notice+'<details id="merqury-prior-catalog"><summary>Before Merqury admission (historical)</summary>'+page[body.end():marker]+'</details>'+page[marker:]).encode()
 prefix=('# Current Merqury catalog checkpoint\n\n'+CURRENT+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [Merqury guide](../../MERQURY_TOOLS.md).\n\n---\n\nEarlier checkpoints below are historical.\n\n').encode()
 for name in META[3:5]:after[name]=prefix+before[name]
 return after

def check(before,after,roles,q):
 require(after==transition(before,roles,q),'Different deterministic Merqury transition');old,new=json.loads(before[META[0]]),json.loads(after[META[0]])
 for left,right in zip(old,new,strict=True):
  require({k:v for k,v in left.items()if k!='tools'}=={k:v for k,v in right.items()if k!='tools'},'Paper identity changed');require([u for u in left['tools']if u['tool_id']!='merqury']==[u for u in right['tools']if u['tool_id']!='merqury'],'Unrelated observation changed')
 a,b=json.loads(before[META[1]]),json.loads(after[META[1]]);require([r for r in a if r['id']!='merqury']==[r for r in b if r['id']!='merqury'],'Unrelated inventory changed')
 prior=json.loads(before[META[5]])['reviews'];reviews=json.loads(after[META[5]])['reviews'];require(len(reviews)==len({(r['pmcid'],r['tool_id'])for r in reviews})==1812,'Wrong review count or duplicate')
 require([r for r in prior if r['tool_id']!='merqury']==[r for r in reviews if r['tool_id']!='merqury'],'Unrelated review changed')
 marked={(p['pmcid'],u['tool_id'])for p in new for u in p['tools']if u['review_status']in(REVIEWED,'individually-reviewed-primary-source-roles; whole-paper-method-review-open')};require(marked=={(r['pmcid'],r['tool_id'])for r in reviews},'Review markers and ledger differ')
 counts=Counter(r['implementation_status']for r in b);require((counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'],counts['method-overlap'],counts['validated-family-alias'])==(34,135,252,13,5),'Wrong family credit')
 return dict(papers=250,labels=451,provider_definitions=490,observations=2731,reviewed_uses=1812,pending_uses=919,scoped_families=169,native=34,upstream=135,including_existing_adapters=172,missing=252,method_overlap=13,validated_alias_labels=5,new_upstream_family_credit=1,new_native_family_credit=0,new_source_reviews=3,existing_source_review_expanded=1,new_papers_or_observations_or_providers=0,unchanged_unrelated_inventory_rows=450,unchanged_unrelated_observations=2727,unchanged_unrelated_reviews=1808,whole_package_qualified=False,full_author_roles_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False)
