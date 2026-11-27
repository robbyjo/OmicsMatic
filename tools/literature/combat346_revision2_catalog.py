"""Forward-only ComBat revision2 admission; one total family, no extra credit."""
import argparse,copy,html,json,os,re,sys,zipfile
from pathlib import Path
import combat346_catalog as prior
from ascat_catalog import checksums,verify_sums
from catalog_fixed_cohort import verify_fixed_cohort
from verify_combat346_revision2 import verify,Q
ROOT=Path(__file__).resolve().parents[2];DATA=prior.DATA;META=prior.META
read=prior.read;pin=prior.pin;identity=prior.identity;encoded=prior.encoded;require=prior.require
FOLDER='docs/literature/combat346-revision2-admission';COVERAGE=DATA+'/combat346-revision2-source-role-coverage.json';PROOF=DATA+'/combat346-revision2-catalog-validation.json'
SCOPE=('Current native continuous ComBat, sva3.46.0 rules, implementation revision2. Parametric/nonparametric, mean-only, reference-batch and numeric keep-design operations retain signed stopping, global missing-data sample variance, constant/numerically-zero row exclusion, unavailable-variance likelihood sums and complete zero-times-missing batch-product propagation. Every native result file is freshly recomputed from captured literal inputs. Native conditioning/capacity guards and explicit Java encoding of derived R NA/NaN remain disclosed. Counts, longitudinal mixed models, SVA factor estimation, exact author inputs/flags and whole-paper reproduction remain separate/unqualified.')
ACTION=('Build current source and use combat-native; no R runtime is needed. Require exit0 and analysis-status.json result_admitted=true, inspect WARNINGS.md and freshly --verify into NEW paths. SKIP3 skips missing/forbidden prerequisites or unsupported models; supply/select the named permitted prerequisite/profile and retry NEW paths. FAIL2 admits no partial result; retain failed inputs/raw outputs, fix the named input/order/design, numerical, convergence, integrity, cancellation or capacity cause and retry NEW paths. Supplied NA stays NA; derived R NA/NaN share Java Double.NaN and export as NaN, with the complete source nonfinite mask retained and refused. Do not substitute this model for P086 ComBat_seq or P128 longitudinal ComBat, infer P185 SVA settings, stabilize undefined likelihoods or refresh old seals. Version1 source/qualifications remain historical and have matching retained source/producer contexts.')
NOTICE=('250 frozen papers; 453 labels and 492 provider definitions; 179 scoped families (36 native, 143 upstream), 1,854 reviewed uses and 880 pending uses. There are 2,734 observations and 247 missing labels. The continuous-ComBat addition contributes one total native family. This forward correction adds zero extra families or reviews and gives zero extra credit to its sva provider label. Current revision2 matches16 complete finite R controls and both complete source undefined masks on Windows/Linux. The full implementation goal remains open.')

def predecessor(root,before):
 folder=root/prior.FOLDER;base=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==base['archive'],'Earlier native archive changed')
 with zipfile.ZipFile(folder/'metadata-before.zip')as archive:
  require(set(archive.namelist())==set(META),'Earlier native archive population changed');older={n:archive.read(n)for n in META}
 require({n:identity(v)for n,v in older.items()}==base['metadata'],'Earlier native archive bytes changed');registry=prior.predecessor(root,older);expected,registry,_=prior.transition(older,registry,read(root/prior.prior.ROLES));require(before==expected and read(folder/'REGISTRY.json')==registry,'Predecessor differs from exact retained native1 transition');return registry

def transition(before,registry,qualification,old_coverage):
 papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in [0,1,2,7]];require((len(papers),len(inventory),sum(len(p['tools'])for p in papers),len(ledger['reviews']),len(registry))==(250,453,2734,1854,492),'Predecessor totals changed')
 paper=next(p for p in papers if p['paper_id']=='P108')
 for tid in ['combat','sva']:
  use=next(u for u in paper['tools']if u['tool_id']==tid);review=next(r for r in ledger['reviews']if r['pmcid']==paper['pmcid']and r['tool_id']==tid);require(use['implementation_scope_note']==review['implementation_scope_note'],'Review/use implementation note differs');note=SCOPE+' '+ACTION+' Historical selected-profile/source-review notes: '+use['implementation_scope_note'];use['implementation_scope_note']=review['implementation_scope_note']=note
 item=next(t for t in inventory if t['id']=='combat');require(item['implementation_status']=='native-subset-validated'and item['papers']==['P086','P108','P128'],'Native family/membership changed');item.update(coverage_note=SCOPE,implementation_scope=SCOPE,validation_note='Current revision2:21 tests;16 complete original finite R cases and both full undefined masks per host;'+str(qualification['finite_comparisons_per_host'])+' finite adjusted/posterior values per host;32 native captures,33 full recomputations and26 refusals across Windows/Linux. Missing-variance R NA versus Java NaN encoding is explicit; no undefined result is admitted. No extra family/source-review credit. See '+Q,implementation_revision=2,current_qualification=Q)
 for pid,role in item['paper_role_qualification'].items():role['implementation_revision']=2 if pid=='P108'else None
 registry=copy.deepcopy(registry);next(t for t in registry if t['id']=='combat')['coverage_note']=SCOPE;summary['current_effective_registry']=FOLDER+'/REGISTRY.json';coverage=copy.deepcopy(old_coverage)
 coverage.update(implementation_revision=2,new_native_family_credit=0,total_native_combat_family_credit=1)
 for role in coverage['roles']:role['implementation_revision']=2 if role['paper_id']=='P108'else None
 after=dict(before)
 for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[7],ledger)]:after[name]=encoded(value)
 note=('# Current native ComBat revision2\n\n'+NOTICE+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [current command](../../combat-native.md) and [qualification and handover](../combat346-revision2-validation/HANDOVER.md). Earlier checkpoints below are historical.\n\n').encode()
 for name in META[3:7]:after[name]=note+b'---\n\n'+before[name]
 notice='<aside id="combat346-revision2-notice" style="margin:1rem;padding:1rem;border:2px solid #986619;overflow-wrap:anywhere"><h2>Current native continuous ComBat: source undefined-mask fidelity</h2><p>'+html.escape(NOTICE)+'</p><p>'+html.escape(SCOPE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/docs/combat-native.md">Input contract, current command and recovery</a></p></aside>'
 for name in META[8:]:
  page=before[name].decode();require('id="combat346-revision2-notice"'not in page,'Already corrected')
  if name==META[8]:
   match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Earlier interactive payload changed');payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
  body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Report body boundary changed');after[name]=(page[:body.end()]+notice+'<details id="combat346-revision2-prior"><summary>Earlier catalog checkpoints (historical)</summary>'+page[body.end():marker]+'</details>'+page[marker:]).encode()
 return after,registry,coverage

def totals():return dict(papers=250,named_labels=453,provider_definitions=492,observations=2734,reviewed_uses=1854,pending_uses=880,scoped_families=179,native=36,upstream=143,missing_labels=247,new_source_reviews=0,new_extra_family_credit=0,total_native_combat_family_credit=1,whole_goal_complete=False)

def check(root):
 folder=root/FOLDER;base=read(folder/'BASELINE.json')
 for name,key in [('metadata-before.zip','archive'),('PREVIOUS-SHA256SUMS','previous_checksums')]:require(pin(folder/name)==base[key],'Revision2 context changed')
 for name,key in [(Q,'qualification'),('tools/literature/combat346_revision2_catalog.py','transformer')]:require(pin(root/name)==base[key],'Revision2 source changed')
 with zipfile.ZipFile(folder/'metadata-before.zip')as archive:
  require(set(archive.namelist())==set(META),'Revision2 baseline population changed');before={n:archive.read(n)for n in META}
 require({n:identity(v)for n,v in before.items()}==base['metadata'],'Revision2 baseline bytes changed');registry=predecessor(root,before);after,registry,coverage=transition(before,registry,read(root/Q),read(root/prior.COVERAGE));require(all((root/n).read_bytes()==v for n,v in after.items())and read(folder/'REGISTRY.json')==registry,'Current metadata differs from exact revision2 transition');require(read(root/COVERAGE)==coverage,'Coverage changed');require(read(root/PROOF)==dict(schema_version=1,status='PASS_CURRENT_NATIVE_COMBAT_REVISION2_CATALOG',totals=totals(),qualification=pin(root/Q),original_source_raw_text_published=False,whole_paper_reproduced=False,website_reports_published=False),'Proof changed')
 old,new=json.loads(before[META[0]]),json.loads(after[META[0]]);verify_fixed_cohort(root,root/DATA,new)
 for a,b in zip(old,new,strict=True):
  require({k:v for k,v in a.items()if k!='tools'}=={k:v for k,v in b.items()if k!='tools'},'Paper identity changed')
  for u,v in zip(a['tools'],b['tools'],strict=True):require({k:x for k,x in u.items()if k!='implementation_scope_note'}=={k:x for k,x in v.items()if k!='implementation_scope_note'},'Raw/source observation changed')
 a,b=json.loads(before[META[7]])['reviews'],json.loads(after[META[7]])['reviews'];require(len(a)==len(b)==1854 and [{k:v for k,v in r.items()if k!='implementation_scope_note'}for r in a]==[{k:v for k,v in r.items()if k!='implementation_scope_note'}for r in b],'Reviewed source facts changed');require({k:v for k,v in json.loads(before[META[2]]).items()if k!='current_effective_registry'}=={k:v for k,v in json.loads(after[META[2]]).items()if k!='current_effective_registry'},'Family/review totals changed');require([t for t in json.loads(before[META[1]])if t['id']!='combat']==[t for t in json.loads(after[META[1]])if t['id']!='combat'],'Unrelated inventory changed')
 previous=checksums((folder/'PREVIOUS-SHA256SUMS').read_bytes());sums=checksums((root/DATA/'SHA256SUMS').read_bytes());require(set(sums)==set(previous)|{Path(COVERAGE).name,Path(PROOF).name},'Checksum population changed');allowed={Path(n).name for n in META if n.startswith(DATA+'/')};require(all(sums[n]==v or n in allowed for n,v in previous.items()),'Unrelated checksum refreshed');verify_sums(root,sums);return totals()

def apply(root,private,out):
 require(not(root/FOLDER).exists()and out.is_relative_to(private)and not out.exists(),'NEW revision2 paths required');qualified=verify(root,private);before={n:(root/n).read_bytes()for n in META};registry=predecessor(root,before);after,registry,coverage=transition(before,registry,read(root/Q),read(root/prior.COVERAGE));previous=(root/DATA/'SHA256SUMS').read_bytes();rows=checksums(previous);require(len(rows)==914,'Earlier checksum count changed');verify_sums(root,rows);out.mkdir();folder=root/FOLDER;folder.mkdir()
 with zipfile.ZipFile(folder/'metadata-before.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as archive:
  for name,raw in before.items():archive.writestr(name,raw)
 (folder/'PREVIOUS-SHA256SUMS').write_bytes(previous);(folder/'BASELINE.json').write_bytes(encoded(dict(schema_version=1,archive=pin(folder/'metadata-before.zip'),metadata={n:identity(v)for n,v in before.items()},previous_checksums=pin(folder/'PREVIOUS-SHA256SUMS'),qualification=pin(root/Q),transformer=pin(root/'tools/literature/combat346_revision2_catalog.py'))));(out/'PLAN.json').write_bytes(encoded(dict(before={n:identity(v)for n,v in before.items()},after={n:identity(v)for n,v in after.items()},qualified=qualified,totals=totals())))
 for i,(name,raw)in enumerate(after.items()):
  require((root/name).read_bytes()==before[name],'Concurrent metadata edit');stage=out/('staged-'+str(i));stage.write_bytes(raw);os.replace(stage,root/name)
 (folder/'REGISTRY.json').write_bytes(encoded(registry));(root/COVERAGE).write_bytes(encoded(coverage));(root/PROOF).write_bytes(encoded(dict(schema_version=1,status='PASS_CURRENT_NATIVE_COMBAT_REVISION2_CATALOG',totals=totals(),qualification=pin(root/Q),original_source_raw_text_published=False,whole_paper_reproduced=False,website_reports_published=False)))
 for name in[n for n in META if n.startswith(DATA+'/')]+[COVERAGE,PROOF]:rows[Path(name).name]=pin(root/name)['sha256']
 newline='\r\n'if previous.count(b'\n')==previous.count(b'\r\n')else'\n';(root/DATA/'SHA256SUMS').write_bytes(''.join(v+'  '+n+newline for n,v in rows.items()).encode());result=check(root);(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_FORWARD_ONLY_NATIVE_COMBAT_REVISION2_CATALOG',totals=result)));return result

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--apply',action='store_true');p.add_argument('--private',type=Path);p.add_argument('--out',type=Path);a=p.parse_args()
 try:
  if a.apply:require(a.private and a.out,'Apply needs qualified private evidence and NEW output');result=apply(a.root.resolve(),a.private.resolve(),a.out.resolve())
  else:result=check(a.root.resolve())
  print(json.dumps(result,indent=2))
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3: Restore the named permitted revision2 prerequisite. 'if code==3 else'FAIL2: No complete revision2 catalog transition. ')+str(error),file=sys.stderr);raise SystemExit(code)
