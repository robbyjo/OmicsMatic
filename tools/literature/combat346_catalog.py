"""Admit native continuous ComBat without crediting count/longitudinal/SVA models."""
import argparse,copy,html,json,os,re,sys,zipfile
from collections import Counter
from pathlib import Path
import sva_combat_catalog as prior
from ascat_catalog import checksums,verify_sums
from catalog_fixed_cohort import verify_fixed_cohort
from verify_combat346_workflows import verify,Q
ROOT=Path(__file__).resolve().parents[2];DATA=prior.DATA;META=prior.META
read=prior.read;pin=prior.pin;identity=prior.identity;encoded=prior.encoded;require=prior.require
FOLDER='docs/literature/combat346-catalog-admission';COVERAGE=DATA+'/combat346-source-role-coverage.json';PROOF=DATA+'/combat346-catalog-validation.json';SOURCE='src/main/java/org/jlinalg/cli/ComBatNativeCli.java'
SCOPE=('Native Java continuous ComBat following sva3.46.0 rules, with explicit parametric/nonparametric, mean-only, reference-batch and numeric keep-design settings. Original signed stopping, global missing-data sample variance, constant-row handling and direct likelihood underflow are retained. Complete literal inputs, every result file and exact defining code/profile support fresh native recomputation. Conditioning and explicit caller capacities may refuse inputs an R implementation could fit. This is one scoped continuous-ComBat family; ComBat_seq, longitudinal mixed models and surrogate-variable estimation remain separate and unqualified by this profile. Exact author flags/data and whole-paper reproduction are not established.')
ACTION=('Build current source and use combat-native with declared continuous units. Require exit0 and analysis-status.json result_admitted=true, inspect WARNINGS.md, then freshly --verify into NEW output. SKIP3 skips missing/forbidden prerequisites or unsupported requested models; restore/select the named permitted prerequisite/profile and retry NEW paths. FAIL2 admits no partial result; retain diagnostics and raw undefined output, correct the named input/design, integrity, numerical, convergence, cancellation or capacity cause and retry NEW paths. No R runtime is needed. Do not stabilize undefined likelihoods, reinterpret input units, or substitute this model for P086 ComBat_seq or P128 longitudinal ComBat. P185 does not specify its SVA function/count estimator.')
NOTICE=('250 frozen papers; 453 labels and 492 provider definitions; 179 scoped families (36 native, 143 upstream), 1,854 reviewed uses and 880 pending uses. There are 2,734 observations and 247 missing labels. One native continuous-ComBat family replaces a method-overlap classification; there are zero additional source reviews. P108 names ComBat and its sva provider for the same operation, with zero extra namespace credit. Count-based ComBat_seq, longitudinal ComBat, SVA factor procedures and the full implementation goal remain open.')

def predecessor(root,before):
 folder=root/prior.FOLDER;base=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==base['archive'],'Earlier SVA/ComBat source-correction archive changed')
 with zipfile.ZipFile(folder/'metadata-before.zip')as archive:
  require(set(archive.namelist())==set(META),'Earlier archived metadata population changed');older={n:archive.read(n)for n in META}
 require({n:identity(raw)for n,raw in older.items()}==base['metadata'],'Earlier archived metadata bytes changed');registry=prior.predecessor(root,older);expected,registry,_=prior.transition(older,registry,read(root/prior.ROLES));require(expected==before and read(folder/'REGISTRY.json')==registry,'Predecessor differs from exact retained source correction');return registry

def transition(before,registry,source_roles):
 papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in [0,1,2,7]]
 require((len(papers),len(inventory),sum(len(p['tools'])for p in papers),len(ledger['reviews']),len(registry))==(250,453,2734,1854,492),'Predecessor population changed')
 item=next(t for t in inventory if t['id']=='combat');require(item['implementation_status']=='method-overlap'and item['papers']==['P086','P108','P128'],'Continuous-ComBat predecessor changed')
 paper=next(p for p in papers if p['paper_id']=='P108')
 for tid in ['combat','sva']:
  use=next(u for u in paper['tools']if u['tool_id']==tid);review=next(r for r in ledger['reviews']if r['pmcid']==paper['pmcid']and r['tool_id']==tid)
  require(use['implementation_scope_note']==review['implementation_scope_note'],'Existing source/ledger implementation notes differ')
  addition=('Current selected project profile: '+SCOPE+' '+('This sva label identifies the provider of the same ComBat operation; no separate SVA factor algorithm or family is credited. 'if tid=='sva'else'')+ACTION+' Historical primary-source review boundary: ')
  use['implementation_scope_note']=review['implementation_scope_note']=addition+use['implementation_scope_note']
 qualifications=copy.deepcopy(item['paper_role_qualification'])
 for pid,role in qualifications.items():role.update(selected_project_profile='sva-3.46.0-continuous-ComBat'if pid=='P108'else None,operation_available_from_this_profile=pid=='P108',exact_author_recipe_qualified=False,whole_paper_reproduced=False)
 item.update(current_coverage='native-subset-validated',existing_source=SOURCE,coverage_note=SCOPE,implementation_status='native-subset-validated',implementation_source=SOURCE,implementation_scope=SCOPE,validation_note='21 unit/contract regressions pass. All25703 finite original-R adjusted/posterior values and exact missing/unestimated masks are checked in14 complete finite cases per host on Windows/Linux.28 complete native captures and29 fresh full result recomputations accompany22 SKIP3/FAIL2 refusals across both hosts. Original likelihood underflow, source failures, earlier failed fixtures and superseded producers remain retained. See '+Q,paper_role_qualification=qualifications,current_documentation='docs/combat-native.md')
 registry=copy.deepcopy(registry);next(t for t in registry if t['id']=='combat').update(current_coverage='native-subset-validated',existing_source=SOURCE,coverage_note=SCOPE)
 coverage=[]
 for role in source_roles['roles']:
  coverage.append(dict(paper_id=role['paper_id'],tool_id=role['tool_id'],reported_family=role['reported_family'],reviewed_reported_version=role['reviewed_reported_version'],selected_project_profile='sva-3.46.0-continuous-ComBat'if role['paper_id']=='P108'else None,operation_available_from_this_profile=role['paper_id']=='P108',namespace_only_no_extra_credit=role['paper_id']=='P108'and role['tool_id']=='sva',remaining_scope=role['remaining_scope'],exact_author_recipe_qualified=False,whole_paper_reproduced=False))
 summary.update(implementation_status_counts=dict(Counter(t['implementation_status']for t in inventory)),current_effective_registry=FOLDER+'/REGISTRY.json');summary['integration_family_counts'].update(scoped=179,native=36,upstream=143,including_existing_adapters=182)
 require(summary['implementation_status_counts']['native-subset-validated']==36 and summary['implementation_status_counts']['upstream-adapter-validated']==143 and summary['implementation_status_counts']['missing']==247,'Derived native admission counts differ')
 after=dict(before)
 for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[7],ledger)]:after[name]=encoded(value)
 note=('# Current native continuous ComBat checkpoint\n\n'+NOTICE+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [native command and complete example](../../combat-native.md) and [qualification](../combat346-validation/QUALIFICATION.json). Earlier checkpoints below are historical.\n\n').encode()
 for name in META[3:7]:after[name]=note+b'---\n\n'+before[name]
 notice='<aside id="combat346-catalog-notice" style="margin:1rem;padding:1rem;border:2px solid #986619;overflow-wrap:anywhere"><h2>Native continuous ComBat and unresolved separate models</h2><p>'+html.escape(NOTICE)+'</p><p>'+html.escape(SCOPE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/docs/combat-native.md">Command, source rules, input units and recovery</a></p></aside>'
 for name in META[8:]:
  page=before[name].decode();require('id="combat346-catalog-notice"'not in page,'Already admitted')
  if name==META[8]:
   match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Earlier interactive payload changed');payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
  body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Report body boundary changed');after[name]=(page[:body.end()]+notice+'<details id="combat346-prior-catalog"><summary>Earlier catalog checkpoints (historical)</summary>'+page[body.end():marker]+'</details>'+page[marker:]).encode()
 return after,registry,coverage

def totals():return dict(papers=250,named_labels=453,provider_definitions=492,observations=2734,reviewed_uses=1854,pending_uses=880,scoped_families=179,native=36,upstream=143,missing_labels=247,new_source_reviews=0,new_native_family_credit=1,new_upstream_family_credit=0,extra_sva_namespace_credit=0,whole_goal_complete=False)

def check(root):
 folder=root/FOLDER;base=read(folder/'BASELINE.json')
 for name,key in [('metadata-before.zip','archive'),('PREVIOUS-SHA256SUMS','previous_checksums')]:require(pin(folder/name)==base[key],'Admission context changed: '+name)
 for name,key in [(Q,'qualification'),('tools/literature/combat346_catalog.py','transformer'),(prior.ROLES,'source_roles')]:require(pin(root/name)==base[key],'Admission source changed: '+name)
 with zipfile.ZipFile(folder/'metadata-before.zip')as archive:
  require(set(archive.namelist())==set(META),'Baseline metadata population changed');before={n:archive.read(n)for n in META}
 require({n:identity(raw)for n,raw in before.items()}==base['metadata'],'Baseline bytes changed');registry=predecessor(root,before);after,registry,coverage=transition(before,registry,read(root/prior.ROLES))
 require(all((root/n).read_bytes()==raw for n,raw in after.items())and read(folder/'REGISTRY.json')==registry,'Canonical metadata differs from exact native transition')
 require(read(root/COVERAGE)==dict(schema_version=1,roles=coverage,new_native_family_credit=1,extra_sva_namespace_credit=0,whole_paper_reproduced=False),'Role coverage differs');require(read(root/PROOF)==dict(schema_version=1,status='PASS_NATIVE_CONTINUOUS_COMBAT_CATALOG_ADMISSION',totals=totals(),qualification=pin(root/Q),raw_source_text_published=False,whole_paper_reproduced=False,website_reports_published=False),'Catalog proof differs')
 old,new=json.loads(before[META[0]]),json.loads(after[META[0]]);verify_fixed_cohort(root,root/DATA,new)
 for a,b in zip(old,new,strict=True):
  require({k:v for k,v in a.items()if k!='tools'}=={k:v for k,v in b.items()if k!='tools'},'Paper identity changed')
  for u,v in zip(a['tools'],b['tools'],strict=True):require({k:x for k,x in u.items()if k!='implementation_scope_note'}=={k:x for k,x in v.items()if k!='implementation_scope_note'},'Raw observation/source review changed')
 require([t for t in json.loads(before[META[1]])if t['id']!='combat']==[t for t in json.loads(after[META[1]])if t['id']!='combat'],'Unrelated inventory changed')
 a,b=json.loads(before[META[7]])['reviews'],json.loads(after[META[7]])['reviews'];require(len(a)==len(b)==1854 and len({(r['pmcid'],r['tool_id'])for r in b})==1854,'Review identities/count changed');require([{k:v for k,v in r.items()if k!='implementation_scope_note'}for r in a]==[{k:v for k,v in r.items()if k!='implementation_scope_note'}for r in b],'Existing reviewed source facts changed')
 previous=checksums((folder/'PREVIOUS-SHA256SUMS').read_bytes());sums=checksums((root/DATA/'SHA256SUMS').read_bytes());require(set(sums)==set(previous)|{Path(COVERAGE).name,Path(PROOF).name},'Checksum population changed');allowed={Path(n).name for n in META if n.startswith(DATA+'/')};require(all(sums[n]==digest or n in allowed for n,digest in previous.items()),'Unrelated checksum refreshed');verify_sums(root,sums);return totals()

def apply(root,private,cache,source_private,out):
 require(not(root/FOLDER).exists()and out.is_relative_to(private)and not out.exists(),'NEW catalog/private paths required');qualified=verify(root,private);primary=prior.source_audit(root,cache,source_private);before={n:(root/n).read_bytes()for n in META};registry=predecessor(root,before);after,registry,coverage=transition(before,registry,read(root/prior.ROLES))
 previous=(root/DATA/'SHA256SUMS').read_bytes();rows=checksums(previous);require(len(rows)==912,'Earlier checksum count changed');verify_sums(root,rows);out.mkdir();folder=root/FOLDER;folder.mkdir()
 with zipfile.ZipFile(folder/'metadata-before.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as archive:
  for name,raw in before.items():archive.writestr(name,raw)
 (folder/'PREVIOUS-SHA256SUMS').write_bytes(previous);(folder/'BASELINE.json').write_bytes(encoded(dict(schema_version=1,archive=pin(folder/'metadata-before.zip'),metadata={n:identity(raw)for n,raw in before.items()},previous_checksums=pin(folder/'PREVIOUS-SHA256SUMS'),qualification=pin(root/Q),transformer=pin(root/'tools/literature/combat346_catalog.py'),source_roles=pin(root/prior.ROLES))))
 (out/'PLAN.json').write_bytes(encoded(dict(before={n:identity(raw)for n,raw in before.items()},after={n:identity(raw)for n,raw in after.items()},qualification=qualified,primary=primary,totals=totals())))
 for i,(name,raw)in enumerate(after.items()):
  require((root/name).read_bytes()==before[name],'Concurrent catalog edit: '+name);stage=out/('staged-'+str(i));stage.write_bytes(raw);os.replace(stage,root/name)
 (folder/'REGISTRY.json').write_bytes(encoded(registry));(root/COVERAGE).write_bytes(encoded(dict(schema_version=1,roles=coverage,new_native_family_credit=1,extra_sva_namespace_credit=0,whole_paper_reproduced=False)));(root/PROOF).write_bytes(encoded(dict(schema_version=1,status='PASS_NATIVE_CONTINUOUS_COMBAT_CATALOG_ADMISSION',totals=totals(),qualification=pin(root/Q),raw_source_text_published=False,whole_paper_reproduced=False,website_reports_published=False)))
 for name in[n for n in META if n.startswith(DATA+'/')]+[COVERAGE,PROOF]:rows[Path(name).name]=pin(root/name)['sha256']
 newline='\r\n'if previous.count(b'\n')==previous.count(b'\r\n')else'\n';(root/DATA/'SHA256SUMS').write_bytes(''.join(digest+'  '+name+newline for name,digest in rows.items()).encode());result=check(root);(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_EXACT_NATIVE_COMBAT_ADMISSION',totals=result)));return result

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--apply',action='store_true');p.add_argument('--private',type=Path);p.add_argument('--xml-cache',type=Path);p.add_argument('--source-private',type=Path);p.add_argument('--out',type=Path);a=p.parse_args()
 try:
  if a.apply:require(all([a.private,a.xml_cache,a.source_private,a.out]),'Apply needs qualified private evidence, XML/source context and NEW output');result=apply(a.root.resolve(),a.private.resolve(),a.xml_cache.absolute(),a.source_private.resolve(),a.out.resolve())
  else:result=check(a.root.resolve())
  print(json.dumps(result,indent=2))
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2;print(('SKIP3: Native catalog admission skipped; restore the named prerequisite. 'if code==3 else'FAIL2: No complete native catalog admission. ')+str(error),file=sys.stderr);raise SystemExit(code)
