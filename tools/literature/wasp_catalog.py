"""Apply/check the qualified standalone WASP transition; preserve earlier catalog bytes."""
import argparse,copy,hashlib,html,json,os,re,zipfile
from collections import Counter
from pathlib import Path
import rfmix_catalog as prior
from ascat_catalog import checksums,verify_sums
from catalog_fixed_cohort import verify_fixed_cohort
from wasp_source_audit import audit as source_audit

ROOT=Path(__file__).resolve().parents[2];DATA=prior.DATA;META=prior.META
FOLDER='docs/literature/wasp-catalog-admission';ROLES='docs/literature/wasp-source-audit/ROLES.json';Q='docs/literature/wasp-validation/QUALIFICATION.json'
COVERAGE=DATA+'/wasp-source-role-coverage.json';PROOF=DATA+'/wasp-catalog-validation.json';SOURCE='src/main/java/org/jlinalg/cli/WaspToolsCli.java'
REVIEWED='individually-reviewed-primary-source-roles; whole-paper-method-review-open'
SCOPE=('Original standalone WASP0.3.4 mapping at d3b8447f with original BWA0.7.17: source/runtime/JAR-bound private execution, complete captured BAM/reference/variant inputs, text and explicit HDF sample/contig modes, checked original VCF-to-HDF conversion, original allele FASTQs/remapping/filtering, retained-original merge, optional seeded paired duplicate removal, final indexed BAM, native complete-population QC and fresh saved-input replay. Native helpers receive no separate family credit. Hornet, STARsolo built-in WASP, CHT, exact author recipes and whole-paper reproduction remain separate.')
ACTION=('WARNINGS AND REQUIRED ACTION: inspect all original logs and input/output audits, require completion plus exact-producer fresh replay, and retain every attempt. Missing/forbidden prerequisites produce SKIP3 and skip dependent analysis; supply/install the named software or input only where permitted and retry NEW paths. Invalid/changed bytes, QC/native disagreement, cancellation or resource exhaustion produce FAIL2 and no complete result. Native BAM audits retain records/maps in JVM memory; declared capacities do not reserve heap or establish whole-genome scale. Source quality/orientation conventions, unordered paired CIGAR comparison and partial-GT masking remain explicit; lossy converter truncation and retained wrong-chromosome matches fail admission. Empty final BAM skips downstream analysis. RFMix SIS-dependent analysis remains SKIPPED; legacy RFMix1.5.4 remains unavailable and v2 is not a substitute. Cell Ranger remains excluded; MaxQuant remains pending with runtime analyses skipped. ConstructASCATFiles/PREASCAT permission remains pending. Literature HTMLs remain withheld from Pages.')
NOTICE=('250 papers, 453 named labels, 492 providers and 2,733 observations; 1,827 reviewed / 906 pending uses. 173 scoped families: 34 native + 139 upstream. WASP adds one scoped original mapping family; 250 labels remain missing. The full goal remains active.')

def read(path):return json.loads(Path(path).read_bytes())
def encoded(value):return(json.dumps(value,ensure_ascii=False,indent=2,allow_nan=False)+'\n').encode()
def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return identity(Path(path).read_bytes())
def require(condition,message):
 if not condition:raise ValueError(message)
def predecessor(root,before):
 folder=root/prior.FOLDER;base=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==base['archive'],'Earlier RFMix catalog archive changed')
 with zipfile.ZipFile(folder/'metadata-before.zip')as stream:
  require(set(stream.namelist())==set(META),'Earlier metadata archive population differs');older={n:stream.read(n)for n in META}
 require({n:identity(raw)for n,raw in older.items()}==base['metadata'],'Earlier catalog bytes changed');registry=prior.predecessor(root,older);expected,effective,_=prior.transition(older,registry,read(root/prior.ROLES)['roles'])
 require(expected==before and read(folder/'REGISTRY.json')==effective,'WASP predecessor is not the retained exact RFMix transition');return effective
def transition(before,registry,roles):
 papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in [0,1,2,7]]
 require((len(papers),len(inventory),sum(len(p['tools'])for p in papers),len(ledger['reviews']),len(registry))==(250,453,2733,1825,492),'Predecessor totals differ')
 require({r['paper_id']for r in roles}=={'P007','P073','P157','P187'},'Complete WASP roles required');coverage=[]
 for role in roles:
  paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools']if u['tool_id']=='wasp'];require(len(uses)==1 and uses[0]==role['raw_observation_preserved'],'Original WASP observation changed');use=uses[0]
  qualified=dict(paper_id=paper['paper_id'],source_role=role['source_role'],reported_version=role['reported_version'],runtime_scope=role['runtime_scope'],remaining_scope=role['remaining_scope'],exact_author_recipe_qualified=False,whole_paper_reproduced=False,already_reviewed_preserved=role['already_reviewed'])
  coverage.append(qualified)
  if role['already_reviewed']:continue
  require(not any(x['pmcid']==paper['pmcid']and x['tool_id']=='wasp'for x in ledger['reviews']),'Pending source role already reviewed')
  use.update(review_status=REVIEWED,use_summary=role['use_summary'],implementation_scope_note=(('The stated sQTL step omitted WASP; no WASP execution is claimed or required for that omitted step. 'if paper['paper_id']=='P157'else SCOPE+' ')+role['remaining_scope']+' '+ACTION),evidence_selection='individually-reviewed-all-primary-WASP-contexts',supporting_evidence=copy.deepcopy(role['primary_anchors']),source_role_qualification=qualified)
  ledger['reviews'].append(dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='wasp',evidence_paragraph_sha256=use['evidence_paragraph_sha256'],use_summary=use['use_summary'],implementation_scope_note=use['implementation_scope_note'],supporting_evidence=copy.deepcopy(role['primary_anchors']),source_role_qualification=copy.deepcopy(qualified)))
 item=next(x for x in inventory if x['id']=='wasp');require(item['implementation_status']=='missing','WASP family already admitted')
 item.update(current_coverage='upstream-adapter-validated',existing_source=SOURCE,coverage_note=SCOPE,implementation_status='upstream-adapter-validated',implementation_source=SOURCE,implementation_scope=SCOPE,validation_note='Actual original workflow captures and fresh replays, independent complete original-source comparisons, 355 native/regression tests per OS, and prominent packaged prerequisite/data/tamper refusals. See docs/literature/wasp-validation/QUALIFICATION.json.',paper_role_qualification={x['paper_id']:x for x in coverage})
 registry=copy.deepcopy(registry);next(x for x in registry if x['id']=='wasp').update(current_coverage='upstream-adapter-validated',existing_source=SOURCE,coverage_note=SCOPE)
 summary.update(implementation_status_counts=dict(Counter(x['implementation_status']for x in inventory)),reviewed_tool_uses=1827,pending_tool_use_reviews=906,current_effective_registry=FOLDER+'/REGISTRY.json');summary['integration_family_counts'].update(scoped=173,native=34,upstream=139,including_existing_adapters=176)
 after=dict(before)
 for name,value in [(META[0],papers),(META[1],inventory),(META[2],summary),(META[7],ledger)]:after[name]=encoded(value)
 note=('# Current standalone WASP workflow checkpoint\n\n'+NOTICE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [WASP workflow guide](../../wasp-tools.md), [source roles](../wasp-source-audit/ROLES.json) and [qualification](../wasp-validation/QUALIFICATION.json). Earlier checkpoints below are historical.\n\n').encode()
 table=('| Paper | Reviewed interpretation | Runtime scope |\n| --- | --- | --- |\n'+'\n'.join('| '+r['paper_id']+' | '+r['use_summary']+' | '+r['runtime_scope']+' |'for r in roles)+'\n\n').encode()
 for name in META[3:7]:after[name]=note+(table if name in META[5:7]else b'')+b'---\n\n'+before[name]
 notice='<aside id="wasp-catalog-notice" style="margin:1rem;padding:1rem;border:2px solid #986619;overflow-wrap:anywhere"><h2>Standalone WASP workflow and warnings</h2><p>'+html.escape(NOTICE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p><p><a href="https://github.com/robbyjo/OmicsMatic/blob/main/docs/wasp-tools.md">Inputs, source behavior, validation and recovery</a></p></aside>'
 for name in META[8:]:
  page=before[name].decode();require('id="wasp-catalog-notice"'not in page,'Already transitioned')
  if name==META[8]:
   match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Earlier interactive payload differs');payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
  body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Report body boundary differs');after[name]=(page[:body.end()]+notice+'<details id="wasp-prior-catalog"><summary>Earlier catalog checkpoints (historical)</summary>'+page[body.end():marker]+'</details>'+page[marker:]).encode()
 return after,registry,coverage
def totals():return dict(papers=250,named_labels=453,provider_definitions=492,observations=2733,reviewed_uses=1827,pending_uses=906,scoped_families=173,native=34,upstream=139,missing_labels=250,new_source_reviews=2,new_upstream_family_credit=1,new_native_family_credit=0,already_reviewed_WASP_roles_preserved=2,explicitly_omitted_WASP_step=1,whole_goal_complete=False)
def check(root):
 folder=root/FOLDER;base=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==base['archive']and pin(folder/'PREVIOUS-SHA256SUMS')==base['previous_checksums']and pin(root/Q)==base['qualification']and pin(root/ROLES)==base['source_roles']and pin(root/'tools/literature/wasp_catalog.py')==base['transformer'],'Transition context changed')
 with zipfile.ZipFile(folder/'metadata-before.zip')as archive:
  require(set(archive.namelist())==set(META),'Baseline archive population differs');before={n:archive.read(n)for n in META}
 require({n:identity(raw)for n,raw in before.items()}==base['metadata'],'Baseline metadata bytes changed');registry=predecessor(root,before);after,registry,coverage=transition(before,registry,read(root/ROLES)['roles']);require(all((root/n).read_bytes()==raw for n,raw in after.items())and read(folder/'REGISTRY.json')==registry,'Canonical metadata differs from exact transition')
 require(read(root/COVERAGE)==dict(schema_version=1,tool_id='wasp',roles=coverage,new_family_credit=1,whole_paper_reproduced=False),'Source role coverage differs')
 require(read(root/PROOF)==dict(schema_version=1,status='PASS_SCOPED_WASP_MAPPING_CATALOG_ADMISSION',totals=totals(),qualification=pin(root/Q),raw_source_text_published=False,released_binary_contains_workflow=False,website_reports_published=False),'Catalog proof differs')
 old,current=json.loads(before[META[0]]),json.loads(after[META[0]]);verify_fixed_cohort(root,root/DATA,current)
 require([[u for u in p['tools']if u['tool_id']!='wasp']for p in old]==[[u for u in p['tools']if u['tool_id']!='wasp']for p in current],'Unrelated observation changed');require([x for x in json.loads(before[META[1]])if x['id']!='wasp']==[x for x in json.loads(after[META[1]])if x['id']!='wasp'],'Unrelated inventory changed')
 for pid in ['P007','P073']:require(next(p for p in old if p['paper_id']==pid)==next(p for p in current if p['paper_id']==pid),'Already reviewed WASP role changed')
 ledger=json.loads(after[META[7]])['reviews'];require(ledger[:-2]==json.loads(before[META[7]])['reviews']and len({(x['pmcid'],x['tool_id'])for x in ledger})==len(ledger)==1827,'Prior/duplicate reviews changed')
 previous=checksums((folder/'PREVIOUS-SHA256SUMS').read_bytes());sums=checksums((root/DATA/'SHA256SUMS').read_bytes());require(set(sums)==set(previous)|{Path(COVERAGE).name,Path(PROOF).name},'Checksum population differs');allowed={Path(n).name for n in META if n.startswith(DATA+'/')};require(all(sums[n]==digest or n in allowed for n,digest in previous.items()),'Unrelated checksum refreshed');verify_sums(root,sums);return totals()
def apply(root,private,cache,out):
 from verify_wasp_workflows import verify
 require(not(root/FOLDER).exists()and out.is_relative_to(private)and not out.exists(),'NEW catalog/private paths required');qualified=verify(root,private);primary=source_audit(root,cache);before={n:(root/n).read_bytes()for n in META};registry=predecessor(root,before);after,registry,coverage=transition(before,registry,read(root/ROLES)['roles']);previous=(root/DATA/'SHA256SUMS').read_bytes();rows=checksums(previous);require(len(rows)==898,'Earlier checksum population differs');verify_sums(root,rows);out.mkdir();folder=root/FOLDER;folder.mkdir()
 with zipfile.ZipFile(folder/'metadata-before.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as archive:
  for name,raw in before.items():archive.writestr(name,raw)
 (folder/'PREVIOUS-SHA256SUMS').write_bytes(previous);(folder/'BASELINE.json').write_bytes(encoded(dict(schema_version=1,archive=pin(folder/'metadata-before.zip'),metadata={n:identity(raw)for n,raw in before.items()},previous_checksums=pin(folder/'PREVIOUS-SHA256SUMS'),qualification=pin(root/Q),source_roles=pin(root/ROLES),transformer=pin(root/'tools/literature/wasp_catalog.py'))));(out/'PLAN.json').write_bytes(encoded(dict(before={n:identity(raw)for n,raw in before.items()},after={n:identity(raw)for n,raw in after.items()},qualification=qualified,primary=primary,totals=totals())))
 for i,(name,raw)in enumerate(after.items()):
  require((root/name).read_bytes()==before[name],'Concurrent catalog change: '+name);stage=out/('staged-'+str(i));stage.write_bytes(raw);os.replace(stage,root/name)
 (folder/'REGISTRY.json').write_bytes(encoded(registry));(root/COVERAGE).write_bytes(encoded(dict(schema_version=1,tool_id='wasp',roles=coverage,new_family_credit=1,whole_paper_reproduced=False)));(root/PROOF).write_bytes(encoded(dict(schema_version=1,status='PASS_SCOPED_WASP_MAPPING_CATALOG_ADMISSION',totals=totals(),qualification=pin(root/Q),raw_source_text_published=False,released_binary_contains_workflow=False,website_reports_published=False)))
 for name in[n for n in META if n.startswith(DATA+'/')]+[COVERAGE,PROOF]:rows[Path(name).name]=pin(root/name)['sha256']
 newline='\r\n'if previous.count(b'\n')==previous.count(b'\r\n')else'\n';(root/DATA/'SHA256SUMS').write_bytes(''.join(digest+'  '+name+newline for name,digest in rows.items()).encode());result=check(root);(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_EXACT_WASP_CATALOG_TRANSITION',totals=result)));return result
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=ROOT);parser.add_argument('--apply',action='store_true');parser.add_argument('--private',type=Path);parser.add_argument('--xml-cache',type=Path);parser.add_argument('--out',type=Path);args=parser.parse_args()
 if args.apply:require(all([args.private,args.xml_cache,args.out]),'Apply needs private qualification, XML cache and NEW output');result=apply(args.root.resolve(),args.private.resolve(),args.xml_cache.absolute(),args.out.resolve())
 else:result=check(args.root.resolve())
 print(json.dumps(result,indent=2))
