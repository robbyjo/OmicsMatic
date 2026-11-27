"""Forward-only admission of one original SVA factor-workflow family."""
import argparse,copy,html,json,os,re,sys,zipfile
from pathlib import Path
import combat346_revision2_catalog as prior
from ascat_catalog import checksums,verify_sums
from catalog_fixed_cohort import verify_fixed_cohort
from verify_sva346_workflows import verify,Q
ROOT=Path(__file__).resolve().parents[2];DATA=prior.DATA;META=prior.META
read=prior.read;pin=prior.pin;identity=prior.identity;encoded=prior.encoded;require=prior.require
FOLDER='docs/literature/sva346-admission';COVERAGE=DATA+'/sva346-source-role-coverage.json';PROOF=DATA+'/sva346-catalog-validation.json'
SCOPE=('Original sva3.46.0 factor-workflow adapter on the explicit private R4.2.3 Linux profile: sva, svaseq and num.sv; IRW/two-step/supervised; explicit or original BE/Leek factor counts; original vfilter ties/repeated filtering, fractional normalized counts and zero-factor shapes. Core package file hashes, original body/formals, RNG states, original RDS, console/warnings and pre/post file fingerprints are retained. R-free verification audits complete saved integrity, literal intake and exported structure/norms/probabilities without refitting. This selected software workflow does not establish a paper recipe, historical-version equivalence or biological reproduction. Generic native Java SVA stays method-overlap. Continuous ComBat, ComBat_seq and longitudinal mixed models remain distinct.')
ACTION=('Build current source and use sva-tools with explicit permitted Rscript/libraries and the exact licensed source archive; see docs/sva-tools.md. Run the original runtime inside the qualified Linux environment. Windows supports R-free saved verification; a Windows original-R profile is not qualified. Read WARNINGS.md, analysis-status.json, original report/warnings and console logs; require exit0, results_admitted=true and fresh --verify. SKIP3 skips the dependent analysis; restore the named exact permitted prerequisite/profile or retain the skip. FAIL2 admits no partial result: preserve all original inputs/objects/diagnostics, fix the named data/design/numerical/integrity/process/capacity cause and retry NEW paths. Do not infer P176 author package version or complete design, or P185 function/count estimator/seed/outcome. Never execute or redistribute the unlicensed author script. Do not substitute this workflow for P086 count ComBat or P128 longitudinal ComBat. No release, whole-paper or literature Pages publication is qualified.')
NOTICE=('250 frozen papers; 453 labels and 492 provider definitions; 180 scoped families (36 native, 144 upstream), 1,854 reviewed uses and 880 pending uses. There are 2,734 observations and 247 missing labels. This adds one SVA factor-workflow family and zero source reviews. The P108 sva provider mention receives no extra ComBat credit. The full 250-paper implementation and semantic review remain open.')
def totals():return dict(papers=250,named_labels=453,provider_definitions=492,observations=2734,reviewed_uses=1854,pending_uses=880,scoped_families=180,native=36,upstream=144,missing_labels=247,new_source_reviews=0,new_upstream_family_credit=1,whole_goal_complete=False)
def predecessor(root,before):
 folder=root/prior.FOLDER;base=read(folder/'BASELINE.json')
 require(pin(folder/'metadata-before.zip')==base['archive'],'Prior archive changed')
 with zipfile.ZipFile(folder/'metadata-before.zip')as z:older={n:z.read(n)for n in META}
 registry=prior.predecessor(root,older);expected,registry,_=prior.transition(older,registry,read(root/prior.Q),read(root/prior.prior.COVERAGE))
 require(before==expected and read(folder/'REGISTRY.json')==registry,'Prior native ComBat revision2 transition changed');return registry
def transition(before,registry,q):
 papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in [0,1,2,7]]
 require((len(papers),len(inventory),sum(len(x['tools'])for x in papers),len(ledger['reviews']),len(registry))==(250,453,2734,1854,492),'Cohort/observations changed')
 registry=copy.deepcopy(registry);item=next(x for x in registry if x['id']=='sva');require(item['current_coverage']=='method-overlap','SVA already credited')
 item.update(current_coverage='upstream-adapter-validated',existing_source='src/main/java/org/jlinalg/cli/SvaWorkflowCli.java',coverage_note=SCOPE)
 tool=next(x for x in inventory if x['id']=='sva');tool.update(**{k:v for k,v in item.items()if k in ['current_coverage','existing_source','coverage_note']},implementation_status='upstream-adapter-validated',implementation_source=item['existing_source'],implementation_scope=SCOPE,current_qualification=Q,validation_note='Ten contract tests;17 complete direct original comparisons and two original errors refused;Linux packaged RUN/VERIFY,Windows R-free verification,replay and explicit prerequisite/integrity/model refusals. See '+Q)
 require(tool['papers']==['P108','P176','P185'],'SVA source membership changed')
 roles={}
 for pid in tool['papers']:
  role=tool['paper_role_qualification'][pid];role.update(selected_factor_workflow_available=True,exact_author_recipe_qualified=False,whole_paper_reproduced=False);roles[pid]=copy.deepcopy(role)
  paper=next(p for p in papers if p['paper_id']==pid);use=next(u for u in paper['tools']if u['tool_id']=='sva');review=next(u for u in ledger['reviews']if u['pmcid']==paper['pmcid']and u['tool_id']=='sva');require(use['implementation_scope_note']==review['implementation_scope_note'],'Use/review note differs');use['implementation_scope_note']=review['implementation_scope_note']=SCOPE+' '+ACTION+' Historical source/implementation checkpoints: '+use['implementation_scope_note']
 summary['implementation_status_counts']['upstream-adapter-validated']+=1;summary['implementation_status_counts']['method-overlap']-=1
 summary['integration_family_counts'].update(scoped=180,upstream=144,including_existing_adapters=183);summary['current_effective_registry']=FOLDER+'/REGISTRY.json'
 after={META[i]:encoded(x)for i,x in [(0,papers),(1,inventory),(2,summary),(7,ledger)]}
 preface='# Current original SVA factor workflows\n\n'+NOTICE+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [current guide](../../sva-tools.md) and [qualification/handover](../sva346-validation/HANDOVER.md). Earlier checkpoints below are historical.\n\n---\n\n'
 for name in META[3:7]:after[name]=preface.encode()+before[name]
 notice='<aside id="sva346-notice"><h2>Original SVA factor workflows</h2><p>'+html.escape(NOTICE)+'</p><p>'+html.escape(SCOPE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p></aside>'
 for name in META[8:]:
  page=before[name].decode();require('id="sva346-notice"'not in page,'Already admitted')
  if name==META[8]:
   match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Previous interactive data changed');payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
  body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Report body boundary changed');after[name]=(page[:body.end()]+notice+'<details id="sva346-prior"><summary>Historical catalog checkpoints</summary>'+page[body.end():marker]+'</details>'+page[marker:]).encode()
 coverage=dict(schema_version=1,scope=SCOPE,required_action=ACTION,roles=roles,new_upstream_family_credit=1,new_source_reviews=0,qualification=Q,author_recipe_qualified=False,whole_goal_complete=False)
 return after,registry,coverage
def check(root):
 folder=root/FOLDER;base=read(folder/'BASELINE.json')
 require(pin(folder/'metadata-before.zip')==base['archive']and pin(folder/'PREVIOUS-SHA256SUMS')==base['previous_checksums'],'Admission baseline changed')
 require(pin(root/Q)==base['qualification']and pin(root/'tools/literature/sva346_catalog.py')==base['transformer'],'SVA admission context changed')
 with zipfile.ZipFile(folder/'metadata-before.zip')as z:require(set(z.namelist())==set(META),'Baseline population changed');before={n:z.read(n)for n in META}
 require({n:identity(v)for n,v in before.items()}==base['metadata'],'Baseline bytes changed');registry=predecessor(root,before);after,registry,coverage=transition(before,registry,read(root/Q))
 require(all((root/n).read_bytes()==v for n,v in after.items())and read(folder/'REGISTRY.json')==registry and read(root/COVERAGE)==coverage,'Catalog differs from exact transition')
 require(read(root/PROOF)==dict(schema_version=1,status='PASS_ORIGINAL_SVA346_CATALOG',totals=totals(),qualification=pin(root/Q),raw_source_or_traces_published=False,whole_paper_reproduced=False,website_reports_published=False),'Proof differs')
 a,b=json.loads(before[META[0]]),json.loads(after[META[0]]);verify_fixed_cohort(root,root/DATA,b)
 for x,y in zip(a,b,strict=True):
  require({k:v for k,v in x.items()if k!='tools'}=={k:v for k,v in y.items()if k!='tools'},'Paper identity changed')
  for u,v in zip(x['tools'],y['tools'],strict=True):require({k:t for k,t in u.items()if k!='implementation_scope_note'}=={k:t for k,t in v.items()if k!='implementation_scope_note'},'Source use changed')
 old,new=json.loads(before[META[7]])['reviews'],json.loads(after[META[7]])['reviews'];require([{k:v for k,v in r.items()if k!='implementation_scope_note'}for r in old]==[{k:v for k,v in r.items()if k!='implementation_scope_note'}for r in new],'Reviewed source facts changed')
 previous=checksums((folder/'PREVIOUS-SHA256SUMS').read_bytes());sums=checksums((root/DATA/'SHA256SUMS').read_bytes());require(set(sums)==set(previous)|{Path(COVERAGE).name,Path(PROOF).name},'Checksum population differs');allowed={Path(n).name for n in META if n.startswith(DATA+'/')};require(all(sums[n]==v or n in allowed for n,v in previous.items()),'Unrelated checksum refreshed');verify_sums(root,sums);return totals()
def apply(root,private,out):
 require(not(root/FOLDER).exists()and out.is_relative_to(private)and not out.exists(),'NEW admission paths required');verify(root,private);prior.check(root);before={n:(root/n).read_bytes()for n in META};registry=predecessor(root,before);after,registry,coverage=transition(before,registry,read(root/Q));previous=(root/DATA/'SHA256SUMS').read_bytes();rows=checksums(previous);require(len(rows)==916,'Prior checksum population changed');verify_sums(root,rows);out.mkdir();folder=root/FOLDER;folder.mkdir()
 with zipfile.ZipFile(folder/'metadata-before.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as z:
  for name,raw in before.items():z.writestr(name,raw)
 (folder/'PREVIOUS-SHA256SUMS').write_bytes(previous);(folder/'BASELINE.json').write_bytes(encoded(dict(schema_version=1,archive=pin(folder/'metadata-before.zip'),metadata={n:identity(v)for n,v in before.items()},previous_checksums=pin(folder/'PREVIOUS-SHA256SUMS'),qualification=pin(root/Q),transformer=pin(root/'tools/literature/sva346_catalog.py'))));(out/'PLAN.json').write_bytes(encoded(dict(before={n:identity(v)for n,v in before.items()},after={n:identity(v)for n,v in after.items()},totals=totals())))
 for i,(name,raw)in enumerate(after.items()):
  require((root/name).read_bytes()==before[name],'Concurrent catalog edit');stage=out/('staged-'+str(i));stage.write_bytes(raw);os.replace(stage,root/name)
 (folder/'REGISTRY.json').write_bytes(encoded(registry));(root/COVERAGE).write_bytes(encoded(coverage));(root/PROOF).write_bytes(encoded(dict(schema_version=1,status='PASS_ORIGINAL_SVA346_CATALOG',totals=totals(),qualification=pin(root/Q),raw_source_or_traces_published=False,whole_paper_reproduced=False,website_reports_published=False)))
 for name in[n for n in META if n.startswith(DATA+'/')]+[COVERAGE,PROOF]:rows[Path(name).name]=pin(root/name)['sha256']
 newline='\r\n'if previous.count(b'\n')==previous.count(b'\r\n')else'\n';(root/DATA/'SHA256SUMS').write_bytes(''.join(v+'  '+n+newline for n,v in rows.items()).encode());result=check(root);(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_FORWARD_ONLY_SVA346_CATALOG',totals=result)));return result
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);p.add_argument('--out',type=Path);p.add_argument('--apply',action='store_true');a=p.parse_args()
 try:
  if a.apply:require(a.private and a.out,'Apply requires private evidence and NEW output');result=apply(a.root.resolve(),a.private.resolve(),a.out.resolve())
  else:result=check(a.root.resolve())
  print(json.dumps(result,indent=2))
 except Exception as e:print('FAIL2: no complete SVA catalog admission; preserve context and correct the named cause: '+str(e),file=sys.stderr);raise SystemExit(2)
