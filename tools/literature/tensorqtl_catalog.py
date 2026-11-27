"""Forward-only tensorQTL workflow admission and four primary-source reviews."""
import argparse,copy,html,json,os,re,sys,zipfile
from pathlib import Path
import sva346_catalog as prior
from ascat_catalog import checksums,verify_sums
from catalog_fixed_cohort import verify_fixed_cohort
from verify_tensorqtl_workflows import verify,Q
import tensorqtl_source_audit as source
ROOT=Path(__file__).resolve().parents[2];DATA=prior.DATA;META=prior.META
read=prior.read;pin=prior.pin;identity=prior.identity;encoded=prior.encoded;require=prior.require
FOLDER='docs/literature/tensorqtl-admission';COVERAGE=DATA+'/tensorqtl-source-role-coverage.json';PROOF=DATA+'/tensorqtl-catalog-validation.json'
SCOPE=('Original tensorQTL 1.0.5/1.0.9 prepared-matrix workflows on explicit Python/Torch/R Linux profiles: cis nominal/permutation, grouped conditional discoveries, supplied-table independent mapping, qvalues, trans/interactions and modern original SuSiE hosting. Mapping preserves source float32 arithmetic, version-specific missing/MAF rules, explicit project seeds, original optimizer/model/RNG objects and exact critical source/runtime file guards. CPU and actual CUDA have separate evidence. Failed optimizers, nonconvergence, unexpected nonfinite outputs and original errors admit no complete result; beta_approx=false retains the five source-defined undefined beta fields. R/Python-free saved verification audits integrity, literal intake, journal boundaries and exported structure/bounds without unpickling or statistical refitting. This is a selected software workflow, not an executed author recipe or whole-paper reproduction. The source 1.0.9 dense-trans interaction shape error remains a refused branch; it is never silently rewritten or converted to sparse output.')
ACTION=('Build current source and use tensorqtl-tools with explicit permitted exact Python/source/R/device bindings; see docs/tensorqtl-tools.md and the synthetic example. Read WARNINGS.md, analysis-status.json and original worker reports/logs; require exit0, results_admitted=true and fresh --verify. SKIP3 skips the dependent analysis: restore the named exact permitted prerequisite or retain the skip. FAIL2 admits no partial result: preserve original inputs/objects/diagnostics, correct the named data/design/numerical/convergence/integrity/process/capacity cause and retry NEW paths. Do not infer author versions, seeds, half-window widths, defaults or CUDA execution; relabel interaction t-tests as likelihood-ratio tests; execute/redistribute unlicensed author programs; repair nonfinite values; or replace tensorQTL-hosted SuSiE with standalone susieR. Original 1.0.9 dense trans interaction fails on the qualified fixture and remains unavailable for admitted results. No release, whole-paper or literature Pages publication is qualified.')
NOTICE=('250 frozen papers; 453 labels and 492 provider definitions; 181 scoped families (36 native, 145 upstream), 1,858 reviewed uses and 876 pending uses. There are 2,734 observations and 247 missing labels. This adds one tensorQTL workflow family and four source reviews. The cited 2022 auxiliary paper adds zero cohort papers. The full implementation and semantic review goal remains open.')
def totals():return dict(papers=250,named_labels=453,provider_definitions=492,observations=2734,reviewed_uses=1858,pending_uses=876,scoped_families=181,native=36,upstream=145,missing_labels=247,new_source_reviews=4,new_upstream_family_credit=1,whole_goal_complete=False)
def predecessor(root,before):
 folder=root/prior.FOLDER;base=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==base['archive'],'Prior SVA archive changed')
 with zipfile.ZipFile(folder/'metadata-before.zip')as z:require(set(z.namelist())==set(META),'Prior archive population changed');older={n:z.read(n)for n in META}
 registry=prior.predecessor(root,older);expected,registry,_=prior.transition(older,registry,read(root/prior.Q));require(before==expected and read(folder/'REGISTRY.json')==registry,'Prior exact SVA transition changed');return registry
def transition(root,before,registry,q):
 papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in[0,1,2,7]]
 require((len(papers),len(inventory),sum(len(x['tools'])for x in papers),len(ledger['reviews']),len(registry))==(250,453,2734,1854,492),'Prior cohort/review/family population differs')
 registry=copy.deepcopy(registry);item=next(x for x in registry if x['id']=='tensorqtl');require(item['current_coverage']=='method-overlap','TensorQTL already credited')
 item.update(current_coverage='upstream-adapter-validated',existing_source='src/main/java/org/jlinalg/cli/TensorQtlWorkflowCli.java',coverage_note=SCOPE)
 tool=next(x for x in inventory if x['id']=='tensorqtl');require(tool['papers']==['P157','P181','P183','P234'],'Paper membership differs')
 tool.update(**{k:v for k,v in item.items()if k in['current_coverage','existing_source','coverage_note']},implementation_status='upstream-adapter-validated',implementation_source=item['existing_source'],implementation_scope=SCOPE,current_qualification=Q,validation_note='Twelve contract tests; complete direct-original synthetic comparisons, actual CPU/CUDA checks, original errors/nonconvergence refused, both-host offline verification, replay and explicit prerequisite/integrity/process/capacity refusals. See '+Q)
 roles={x['paper_id']:copy.deepcopy(x)for x in read(root/source.FOLDER/'ROLES.json')['roles']};reviews=copy.deepcopy(read(root/source.FOLDER/'REVIEW-ENTRIES.json')['reviews']);require(len(reviews)==4 and set(roles)==set(tool['papers']),'Reviewed roles differ')
 for pid,role in roles.items():
  paper=next(x for x in papers if x['paper_id']==pid);use=next(x for x in paper['tools']if x['tool_id']=='tensorqtl');review=next(x for x in reviews if x['pmcid']==paper['pmcid']);require(not any(x['pmcid']==paper['pmcid']and x['tool_id']=='tensorqtl'for x in ledger['reviews']),'Duplicate prior tensorQTL review')
  require(review['doi']==paper['doi']and review['evidence_paragraph_sha256']==use['evidence_paragraph_sha256'],'Source identity differs')
  review['implementation_scope_note']=SCOPE+' '+ACTION+' Paper-specific primary/linked-source limits: '+review['implementation_scope_note'];use.update(use_summary=review['use_summary'],implementation_scope_note=review['implementation_scope_note'],supporting_evidence=copy.deepcopy(review['supporting_evidence']),review_status='individually-reviewed-primary-source-roles; whole-paper-method-review-open',evidence_selection='individually-reviewed-expanded-primary-source-census')
  role.update(selected_original_workflow_available=True,exact_author_recipe_qualified=False,whole_paper_reproduced=False)
 ledger['reviews'].extend(reviews);require(len(ledger['reviews'])==1858,'Review count differs');tool['paper_role_qualification']=roles
 summary['implementation_status_counts']['upstream-adapter-validated']+=1;summary['implementation_status_counts']['method-overlap']-=1
 summary['integration_family_counts'].update(scoped=181,upstream=145,including_existing_adapters=184);summary.update(reviewed_tool_uses=1858,pending_tool_use_reviews=876,current_effective_registry=FOLDER+'/REGISTRY.json')
 after={META[i]:encoded(x)for i,x in[(0,papers),(1,inventory),(2,summary),(7,ledger)]}
 preface='# Current original tensorQTL workflows\n\n'+NOTICE+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [guide](../../tensorqtl-tools.md) and [qualification/handover](../tensorqtl-validation/HANDOVER.md). Earlier checkpoints below are historical.\n\n---\n\n'
 for name in META[3:7]:after[name]=preface.encode()+before[name]
 notice='<aside id="tensorqtl-notice"><h2>Original tensorQTL workflows</h2><p>'+html.escape(NOTICE)+'</p><p>'+html.escape(SCOPE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p></aside>'
 for name in META[8:]:
  page=before[name].decode();require('id="tensorqtl-notice"'not in page,'Already admitted')
  if name==META[8]:
   match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Prior interactive payload differs');payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
  body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Report body boundary differs');after[name]=(page[:body.end()]+notice+'<details id="tensorqtl-prior"><summary>Historical catalog checkpoints</summary>'+page[body.end():marker]+'</details>'+page[marker:]).encode()
 coverage=dict(schema_version=1,scope=SCOPE,required_action=ACTION,roles=roles,new_upstream_family_credit=1,new_source_reviews=4,qualification=Q,exact_author_recipe_qualified=False,whole_goal_complete=False)
 return after,registry,coverage
def check(root):
 folder=root/FOLDER;base=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==base['archive']and pin(folder/'PREVIOUS-SHA256SUMS')==base['previous_checksums'],'Admission baseline differs')
 require(pin(root/Q)==base['qualification']and pin(root/'tools/literature/tensorqtl_catalog.py')==base['transformer'],'Admission context differs')
 with zipfile.ZipFile(folder/'metadata-before.zip')as z:require(set(z.namelist())==set(META),'Baseline population differs');before={n:z.read(n)for n in META}
 require({n:identity(v)for n,v in before.items()}==base['metadata'],'Baseline bytes differ');registry=predecessor(root,before);after,registry,coverage=transition(root,before,registry,read(root/Q));require(all((root/n).read_bytes()==v for n,v in after.items())and read(folder/'REGISTRY.json')==registry and read(root/COVERAGE)==coverage,'Current catalog differs from exact tensorQTL transition')
 require(read(root/PROOF)==dict(schema_version=1,status='PASS_ORIGINAL_TENSORQTL_CATALOG',totals=totals(),qualification=pin(root/Q),raw_source_or_traces_published=False,whole_paper_reproduced=False,website_reports_published=False),'Catalog proof differs')
 a,b=json.loads(before[META[0]]),json.loads(after[META[0]]);verify_fixed_cohort(root,root/DATA,b)
 allowed={'use_summary','implementation_scope_note','supporting_evidence','review_status','evidence_selection'}
 for x,y in zip(a,b,strict=True):
  require({k:v for k,v in x.items()if k!='tools'}=={k:v for k,v in y.items()if k!='tools'},'Paper identity changed')
  for u,v in zip(x['tools'],y['tools'],strict=True):require(u==v if u['tool_id']!='tensorqtl'else{k:t for k,t in u.items()if k not in allowed}=={k:t for k,t in v.items()if k not in allowed},'Unrelated or raw source observation changed')
 old,new=json.loads(before[META[7]])['reviews'],json.loads(after[META[7]])['reviews'];require(new[:len(old)]==old and len(new)==len(old)+4,'Existing reviewed facts changed')
 previous=checksums((folder/'PREVIOUS-SHA256SUMS').read_bytes());sums=checksums((root/DATA/'SHA256SUMS').read_bytes());require(set(sums)==set(previous)|{Path(COVERAGE).name,Path(PROOF).name},'Checksum population differs');changed={Path(n).name for n in META if n.startswith(DATA+'/')};require(all(sums[n]==v or n in changed for n,v in previous.items()),'Unrelated checksum refreshed');verify_sums(root,sums);return totals()
def apply(root,private,out):
 require(not(root/FOLDER).exists()and out.is_relative_to(private)and not out.exists(),'NEW admission paths required');verify(root,private);prior.check(root);before={n:(root/n).read_bytes()for n in META};registry=predecessor(root,before);after,registry,coverage=transition(root,before,registry,read(root/Q));previous=(root/DATA/'SHA256SUMS').read_bytes();rows=checksums(previous);require(len(rows)==918,'Prior checksum population differs');verify_sums(root,rows);out.mkdir();folder=root/FOLDER;folder.mkdir()
 with zipfile.ZipFile(folder/'metadata-before.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as z:
  for name,raw in before.items():z.writestr(name,raw)
 (folder/'PREVIOUS-SHA256SUMS').write_bytes(previous);(folder/'BASELINE.json').write_bytes(encoded(dict(schema_version=1,archive=pin(folder/'metadata-before.zip'),metadata={n:identity(v)for n,v in before.items()},previous_checksums=pin(folder/'PREVIOUS-SHA256SUMS'),qualification=pin(root/Q),transformer=pin(root/'tools/literature/tensorqtl_catalog.py'))));(out/'PLAN.json').write_bytes(encoded(dict(before={n:identity(v)for n,v in before.items()},after={n:identity(v)for n,v in after.items()},totals=totals())))
 for i,(name,raw)in enumerate(after.items()):
  require((root/name).read_bytes()==before[name],'Concurrent catalog edit');stage=out/('staged-'+str(i));stage.write_bytes(raw);os.replace(stage,root/name)
 (folder/'REGISTRY.json').write_bytes(encoded(registry));(root/COVERAGE).write_bytes(encoded(coverage));(root/PROOF).write_bytes(encoded(dict(schema_version=1,status='PASS_ORIGINAL_TENSORQTL_CATALOG',totals=totals(),qualification=pin(root/Q),raw_source_or_traces_published=False,whole_paper_reproduced=False,website_reports_published=False)))
 for name in[n for n in META if n.startswith(DATA+'/')]+[COVERAGE,PROOF]:rows[Path(name).name]=pin(root/name)['sha256']
 newline='\r\n'if previous.count(b'\n')==previous.count(b'\r\n')else'\n';(root/DATA/'SHA256SUMS').write_bytes(''.join(v+'  '+n+newline for n,v in rows.items()).encode());result=check(root);(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_FORWARD_ONLY_TENSORQTL_CATALOG',totals=result)));return result
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);p.add_argument('--out',type=Path);p.add_argument('--apply',action='store_true');a=p.parse_args()
 try:
  if a.apply:require(a.private and a.out,'Apply requires private evidence and NEW output');result=apply(a.root.resolve(),a.private.resolve(),a.out.resolve())
  else:result=check(a.root.resolve())
  print(json.dumps(result,indent=2))
 except Exception as e:print('FAIL2: no complete tensorQTL catalog admission; preserve context and correct the named cause: '+str(e),file=sys.stderr);raise SystemExit(2)
