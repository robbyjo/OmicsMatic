"""Correct SVA/ComBat source roles; preserve all prior implementation credit."""
import argparse,copy,html,json,os,re,sys,zipfile
from collections import Counter
from pathlib import Path
import flashpca_catalog as prior
from ascat_catalog import checksums,verify_sums
from catalog_fixed_cohort import verify_fixed_cohort
from sva_combat_source_audit import audit as source_audit,COMBAT_PATTERN,POSITIVE

ROOT=Path(__file__).resolve().parents[2];DATA=prior.DATA;META=prior.META
read=prior.read;pin=prior.pin;identity=prior.identity;encoded=prior.encoded;require=prior.require
FOLDER='docs/literature/sva-combat-catalog-correction'
SOURCE_FOLDER='docs/literature/sva-combat-source-audit'
ROLES=SOURCE_FOLDER+'/ROLES.json';Q=SOURCE_FOLDER+'/QUALIFICATION.json'
COVERAGE=DATA+'/sva-combat-source-role-coverage.json';PROOF=DATA+'/sva-combat-catalog-validation.json'
REVIEWED='individually-reviewed-primary-source-roles; whole-paper-method-review-open'
SCOPE=('Source correction only, with zero new native/upstream family credit. Continuous sva::ComBat, count-model sva::ComBat_seq, svaseq and longitudinal mixed-model ComBat are distinct operations. Existing native confounder methods remain method-overlap; no original-runtime, numerical equivalence, author recipe or whole-paper qualification is inferred.')
ACTION=('P086 names ComBat_seq but attributes it to DESeq2 and describes transformed/z-scored inputs. Preserve the discrepancy; obtain actual count inputs and author command before analysis. Do not round, invert or substitute continuous ComBat. P128 needs longitudinal subject-aware harmonization, not ordinary ComBat. P185 does not identify its SVA function/count estimator. Missing/forbidden prerequisites: SKIP3 and skip dependent analysis; supply the named permitted source/runtime/input and retry NEW paths. Changed/invalid evidence or failed analysis: FAIL2, no partial result admitted; fix the named cause and retain failed evidence. No new runtime is installed or analysis admitted by this source audit.')
NOTICE=('250 frozen papers; 453 labels and 492 provider definitions; 178 scoped families (35 native, 143 upstream), 1,854 reviewed uses and 880 pending uses. There are 2,734 observations and 247 missing labels. Six SVA/ComBat observations are individually reviewed. P206 is removed as a retrotransposon false positive; the previously missed P086 ComBat_seq spelling is recovered. This correction adds zero implementation credit. The full implementation goal remains open.')

def predecessor(root,before):
 folder=root/prior.FOLDER;base=read(folder/'BASELINE.json')
 require(pin(folder/'metadata-before.zip')==base['archive'],'Earlier FlashPCA archive changed')
 with zipfile.ZipFile(folder/'metadata-before.zip')as archive:
  require(set(archive.namelist())==set(META),'Earlier archive population changed');older={n:archive.read(n)for n in META}
 require({n:identity(raw)for n,raw in older.items()}==base['metadata'],'Earlier archived bytes changed')
 registry=prior.predecessor(root,older);expected,registry,_=prior.transition(older,registry,read(root/prior.ROLES)['roles'])
 require(expected==before and read(folder/'REGISTRY.json')==registry,'Predecessor differs from exact retained FlashPCA transition');return registry

def transition(before,registry,document):
 papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in [0,1,2,7]]
 require((len(papers),len(inventory),sum(len(p['tools'])for p in papers),len(ledger['reviews']),len(registry))==(250,453,2734,1848,492),'Predecessor totals changed')
 roles=document['roles'];require(len(roles)==6 and {(r['paper_id'],r['tool_id'])for r in roles}==POSITIVE,'Six exact positive source observations required')
 excluded=document['excluded_observation'];paper=next(p for p in papers if p['paper_id']=='P206')
 require([u for u in paper['tools']if u['tool_id']=='sva']==[excluded['raw_observation_preserved']],'Excluded raw observation changed')
 require(not any(r['pmcid']==paper['pmcid']and r['tool_id']=='sva'for r in ledger['reviews']),'Cannot drop an existing review')
 paper['tools']=[u for u in paper['tools']if u['tool_id']!='sva'];coverage=[]
 for role in roles:
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools']if u['tool_id']==role['tool_id']]
  require(not any(r['pmcid']==paper['pmcid']and r['tool_id']==role['tool_id']for r in ledger['reviews']),'Existing source review must be preserved')
  if role['paper_id']=='P086':
   require(not uses,'Recovery already applied');use=copy.deepcopy(role['raw_observation_preserved']);paper['tools'].insert(next((i for i,u in enumerate(paper['tools'])if u['tool_id']>'combat'),len(paper['tools'])),use)
  else:require(uses==[role['raw_observation_preserved']],'Original observation changed');use=uses[0]
  qualified={k:copy.deepcopy(role[k])for k in ['paper_id','tool_id','reported_family','reviewed_reported_version','remaining_scope','exact_author_recipe_qualified','whole_paper_reproduced']};coverage.append(qualified)
  note=SCOPE+' '+role['remaining_scope']+' '+ACTION
  use.update(review_status=REVIEWED,use_summary=role['use_summary'],implementation_scope_note=note,evidence_selection='individually-reviewed-expanded-primary-source-census',supporting_evidence=copy.deepcopy(role['primary_anchors']),source_role_qualification=qualified)
  ledger['reviews'].append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id=role['tool_id'],evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=use['use_summary'],implementation_scope_note=note,supporting_evidence=copy.deepcopy(role['primary_anchors']),source_role_qualification=copy.deepcopy(qualified)))
 registry=copy.deepcopy(registry)
 for tid in ['sva','combat']:
  item=next(t for t in inventory if t['id']==tid);provider=next(t for t in registry if t['id']==tid)
  require(item['implementation_status']==provider['current_coverage']=='method-overlap','This source-only correction cannot admit a runtime')
  item.update(coverage_note=SCOPE,implementation_scope=SCOPE,validation_note='Full250 primary-source census: six positive observations, one excluded false positive and one recovered underscore spelling. No source/runtime/numerical analysis or new implementation is qualified. See '+Q,paper_role_qualification={r['paper_id']:r for r in coverage if r['tool_id']==tid})
  provider['coverage_note']=SCOPE
  if tid=='combat':item['pattern']=provider['pattern']=COMBAT_PATTERN
  item['papers']=[p['paper_id']for p in papers if any(u['tool_id']==tid for u in p['tools'])];item['paper_count']=len(item['papers'])
 summary.update(reviewed_tool_uses=1854,pending_tool_use_reviews=880,current_effective_registry=FOLDER+'/REGISTRY.json')
 after=dict(before)
 for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[7],ledger)]:after[name]=encoded(value)
 note=('# Current SVA and ComBat source corrections\n\n'+NOTICE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [source decisions and next implementation work](../sva-combat-source-audit/HANDOVER.md). Earlier checkpoints below are historical.\n\n').encode()
 table=('| Paper / label | Individually reviewed source use |\n| --- | --- |\n'+'\n'.join('| '+r['paper_id']+' / '+r['tool_id']+' | '+r['use_summary']+' |'for r in roles)+'\n\n').encode()
 for name in META[3:7]:after[name]=note+(table if name in META[5:7]else b'')+b'---\n\n'+before[name]
 notice='<aside id="sva-combat-source-notice" style="margin:1rem;padding:1rem;border:2px solid #986619;overflow-wrap:anywhere"><h2>SVA and ComBat: source corrections and unresolved analyses</h2><p>'+html.escape(NOTICE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/'+SOURCE_FOLDER+'/HANDOVER.md">Source decisions and remaining implementation</a></p></aside>'
 for name in META[8:]:
  page=before[name].decode();require('id="sva-combat-source-notice"'not in page,'Correction already applied')
  if name==META[8]:
   match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
   require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Prior interactive payload changed')
   payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
  body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Report body boundary changed')
  after[name]=(page[:body.end()]+notice+'<details id="sva-combat-prior-catalog"><summary>Earlier catalog checkpoints (historical)</summary>'+page[body.end():marker]+'</details>'+page[marker:]).encode()
 return after,registry,coverage

def totals():return dict(papers=250,named_labels=453,provider_definitions=492,observations=2734,reviewed_uses=1854,pending_uses=880,scoped_families=178,native=35,upstream=143,missing_labels=247,new_source_reviews=6,recovered_observations=1,excluded_false_positive_observations=1,new_upstream_family_credit=0,new_native_family_credit=0,whole_goal_complete=False)

def check(root):
 folder=root/FOLDER;base=read(folder/'BASELINE.json')
 for name,key in [('metadata-before.zip','archive'),('PREVIOUS-SHA256SUMS','previous_checksums')]:require(pin(folder/name)==base[key],'Transition context changed: '+name)
 for name,key in [(Q,'qualification'),(ROLES,'source_roles'),('tools/literature/sva_combat_catalog.py','transformer'),('tools/literature/sva_combat_source_audit.py','source_verifier')]:require(pin(root/name)==base[key],'Transition context changed: '+name)
 with zipfile.ZipFile(folder/'metadata-before.zip')as archive:
  require(set(archive.namelist())==set(META),'Baseline population changed');before={n:archive.read(n)for n in META}
 require({n:identity(raw)for n,raw in before.items()}==base['metadata'],'Baseline bytes changed')
 registry=predecessor(root,before);after,registry,coverage=transition(before,registry,read(root/ROLES))
 require(all((root/n).read_bytes()==raw for n,raw in after.items())and read(folder/'REGISTRY.json')==registry,'Canonical metadata differs from exact source correction')
 require(read(root/COVERAGE)==dict(schema_version=1,tool_ids=['sva','combat'],roles=coverage,new_family_credit=0,whole_paper_reproduced=False),'Role coverage changed')
 require(read(root/PROOF)==dict(schema_version=1,status='PASS_SVA_COMBAT_SOURCE_CORRECTION_ONLY',totals=totals(),qualification=pin(root/Q),raw_source_text_published=False,new_runtime_or_analysis_qualified=False,website_reports_published=False),'Catalog proof changed')
 old,current=json.loads(before[META[0]]),json.loads(after[META[0]]);verify_fixed_cohort(root,root/DATA,current)
 for a,b in zip(old,current,strict=True):
  require({k:v for k,v in a.items()if k!='tools'}=={k:v for k,v in b.items()if k!='tools'},'Paper identity changed')
  require([u for u in a['tools']if u['tool_id']not in ['sva','combat']]==[u for u in b['tools']if u['tool_id']not in ['sva','combat']],'Unrelated observation changed')
 require([t for t in json.loads(before[META[1]])if t['id']not in ['sva','combat']]==[t for t in json.loads(after[META[1]])if t['id']not in ['sva','combat']],'Unrelated inventory changed')
 ledger=json.loads(after[META[7]])['reviews'];require(ledger[:-6]==json.loads(before[META[7]])['reviews'] and len({(r['pmcid'],r['tool_id'])for r in ledger})==len(ledger)==1854,'Prior reviews or uniqueness changed')
 counts=Counter(u['tool_id']for p in current for u in p['tools']);require(len(counts)==453 and sum(counts.values())==2734,'Derived observation counts differ')
 for item in json.loads(after[META[1]]):require(item['paper_count']==counts[item['id']] and item['papers']==[p['paper_id']for p in current if any(u['tool_id']==item['id']for u in p['tools'])],'Derived inventory membership differs: '+item['id'])
 a,b=json.loads(before[META[2]]),json.loads(after[META[2]])
 require({k:v for k,v in a.items()if k not in ['reviewed_tool_uses','pending_tool_use_reviews','current_effective_registry']}=={k:v for k,v in b.items()if k not in ['reviewed_tool_uses','pending_tool_use_reviews','current_effective_registry']},'Source-only correction changed implementation credit or other summary fields')
 previous=checksums((folder/'PREVIOUS-SHA256SUMS').read_bytes());sums=checksums((root/DATA/'SHA256SUMS').read_bytes())
 require(set(sums)==set(previous)|{Path(COVERAGE).name,Path(PROOF).name},'Checksum population changed');allowed={Path(n).name for n in META if n.startswith(DATA+'/')}
 require(all(sums[n]==digest or n in allowed for n,digest in previous.items()),'Unrelated checksum refreshed');verify_sums(root,sums);return totals()

def apply(root,private,cache,out):
 require(not(root/FOLDER).exists() and out.is_relative_to(private) and not out.exists(),'NEW catalog/private paths required')
 primary=source_audit(root,cache,private);require(read(root/Q)==primary,'Saved source qualification differs')
 before={n:(root/n).read_bytes()for n in META};registry=predecessor(root,before);after,registry,coverage=transition(before,registry,read(root/ROLES))
 previous=(root/DATA/'SHA256SUMS').read_bytes();rows=checksums(previous);require(len(rows)==910,'Predecessor checksum count changed');verify_sums(root,rows)
 out.mkdir();folder=root/FOLDER;folder.mkdir()
 with zipfile.ZipFile(folder/'metadata-before.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as archive:
  for name,raw in before.items():archive.writestr(name,raw)
 (folder/'PREVIOUS-SHA256SUMS').write_bytes(previous)
 (folder/'BASELINE.json').write_bytes(encoded(dict(schema_version=1,archive=pin(folder/'metadata-before.zip'),metadata={n:identity(raw)for n,raw in before.items()},previous_checksums=pin(folder/'PREVIOUS-SHA256SUMS'),qualification=pin(root/Q),source_roles=pin(root/ROLES),transformer=pin(root/'tools/literature/sva_combat_catalog.py'),source_verifier=pin(root/'tools/literature/sva_combat_source_audit.py'))))
 (out/'PLAN.json').write_bytes(encoded(dict(before={n:identity(raw)for n,raw in before.items()},after={n:identity(raw)for n,raw in after.items()},primary=primary,totals=totals())))
 for i,(name,raw)in enumerate(after.items()):
  require((root/name).read_bytes()==before[name],'Concurrent catalog edit: '+name);stage=out/('staged-'+str(i));stage.write_bytes(raw);os.replace(stage,root/name)
 (folder/'REGISTRY.json').write_bytes(encoded(registry))
 (root/COVERAGE).write_bytes(encoded(dict(schema_version=1,tool_ids=['sva','combat'],roles=coverage,new_family_credit=0,whole_paper_reproduced=False)))
 (root/PROOF).write_bytes(encoded(dict(schema_version=1,status='PASS_SVA_COMBAT_SOURCE_CORRECTION_ONLY',totals=totals(),qualification=pin(root/Q),raw_source_text_published=False,new_runtime_or_analysis_qualified=False,website_reports_published=False)))
 for name in[n for n in META if n.startswith(DATA+'/')]+[COVERAGE,PROOF]:rows[Path(name).name]=pin(root/name)['sha256']
 newline='\r\n'if previous.count(b'\n')==previous.count(b'\r\n')else'\n';(root/DATA/'SHA256SUMS').write_bytes(''.join(digest+'  '+name+newline for name,digest in rows.items()).encode())
 result=check(root);(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_EXACT_SVA_COMBAT_SOURCE_CORRECTION',totals=result)));return result

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--apply',action='store_true');p.add_argument('--private',type=Path);p.add_argument('--xml-cache',type=Path);p.add_argument('--out',type=Path);a=p.parse_args()
 try:
  if a.apply:
   require(all([a.private,a.xml_cache,a.out]),'Apply needs private source evidence, XML cache and NEW output');result=apply(a.root.resolve(),a.private.resolve(),a.xml_cache.absolute(),a.out.resolve())
  else:result=check(a.root.resolve())
  print(json.dumps(result,indent=2))
 except Exception as error:
  code=3 if isinstance(error,(FileNotFoundError,PermissionError))else 2
  print(('SKIP3: Catalog correction skipped; restore the named prerequisite. 'if code==3 else'FAIL2: No complete catalog correction. ')+str(error),file=sys.stderr);raise SystemExit(code)
