"""Forward one scoped pbmm2 upstream family and four distinct primary-source reviews."""
import argparse,copy,html,json,os,re,zipfile
from pathlib import Path
import cadd_linear_catalog as prior
import pbmm2_source_audit as source
from ascat_catalog import checksums,verify_sums
from catalog_fixed_cohort import verify_fixed_cohort
from verify_pbmm2 import verify,Q
ROOT=Path(__file__).resolve().parents[2];DATA=prior.DATA;META=prior.META
read=prior.read;pin=prior.pin;identity=prior.identity;encoded=prior.encoded;require=prior.require
FOLDER='docs/literature/pbmm2-admission';COVERAGE=DATA+'/pbmm2-source-role-coverage.json';PROOF=DATA+'/pbmm2-catalog-validation.json'
SCOPE='Exact original pbmm21.13.1/1.17.0 INDEX/ALIGN workflows over complete explicit regular-file input roots, source presets/options and native BAM/FASTA/FASTQ/FOFN/XML/JSON inputs. Literal resource locators and identifiers are retained through owned original-path namespaces; every output is captured. Native Java checks complete output BAM CIGAR arithmetic and available identity/concordance tags, with portable verification and whole-input replay. Full109-case controls per edition retain three1.17 legacy expectation failures; all500 native/container BAMs match in record order and all29808 records/tags. No complete newer binary/source closure, private-runner/C++-suite, independent all-tag/reference-fit/index-construction, methylation/variant or whole-paper accuracy claim. P214 TableS5 parameters and unresolved author references/recipes remain separate.'
ACTION='Require COMPLETED.json and successful --verify; read version warnings and original process diagnostics. SKIP3 skips analysis until exact permitted prerequisites/capabilities exist: install separately where allowed or keep skipped. FAIL2 admits no result: retain every failure, correct the named source/input/runtime/process/numerical/capacity cause and use NEW paths. Never restart a live or indeterminate job, subset a reference/read population, repair identifiers/alleles, change an original expected result or substitute an edition. Symbolic links are explicitly skipped by this snapshot profile. Cell Ranger remains excluded; MaxQuant is pending with runtime skipped. Full CADD annotation is separately pending and its live transfer must be preserved. No release or literature Pages deployment is requested.'
NOTICE='250 frozen papers;453 named labels and492 provider definitions;186 scoped families(37 native,149 upstream),1940 reviewed uses and801 pending uses across2741 observations;246 missing labels. pbmm2 adds one scoped upstream family and four source-role reviews, with no duplicate native-family or recovered-observation credit. Full implementation and source-semantic review remain open.'

def totals():return dict(papers=250,named_labels=453,provider_definitions=492,observations=2741,reviewed_uses=1940,pending_uses=801,scoped_families=186,native=37,upstream=149,missing_labels=246,new_native_family_credit=0,new_upstream_family_credit=1,new_source_reviews=4,recovered_observations=0,whole_goal_complete=False)
def predecessor(root,before):
 folder=root/prior.FOLDER;base=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==base['archive'],'Prior CADD linear-component archive differs')
 with zipfile.ZipFile(folder/'metadata-before.zip')as z:require(set(z.namelist())==set(META),'Prior metadata family differs');older={n:z.read(n)for n in META}
 registry=prior.predecessor(root,older);expected,registry,_=prior.transition(root,older,registry,read(root/prior.Q));require(before==expected and read(folder/'REGISTRY.json')==registry,'Prior exact CADD component transition differs');return registry
def transition(root,before,registry,q):
 require(q['status']=='PASS_ORIGINAL_PBMM2_PREPARED_INPUT_OPERATIONS_AND_NATIVE_CIGAR_CONTRACTS'and q['new_native_family_credit']==0 and q['new_upstream_family_credit']==1 and q['new_source_reviews']==4 and not q['whole_goal_complete']and not q['whole_paper_reproduced'],'Scoped original pbmm2 qualification differs')
 papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in [0,1,2,7]];require((len(papers),len(inventory),sum(len(x['tools'])for x in papers),len(ledger['reviews']),len(registry))==(250,453,2741,1936,492),'Prior fixed population differs');registry=copy.deepcopy(registry)
 item=next(x for x in registry if x['id']=='pbmm2');require(item['current_coverage']=='missing','pbmm2 family was already credited');item.update(current_coverage='upstream-adapter-validated',existing_source='src/main/java/org/jlinalg/cli/Pbmm2ToolsCli.java',coverage_note=SCOPE)
 tool=next(x for x in inventory if x['id']=='pbmm2');require(tool['implementation_status']=='missing'and tool['papers']==list(source.ROLES),'Prior pbmm2 membership/status differs')
 tool.update(**{k:item[k]for k in ['current_coverage','existing_source','coverage_note']},implementation_status='upstream-adapter-validated',implementation_source=item['existing_source'],implementation_scope=SCOPE,current_qualification=Q,validation_note='14 passing Java contracts; all109 unmodified functional cases per edition; original1.17 three legacy failures retained; full500-BAM native/container equality;28608 mapped native arithmetic records/28992 float tags; full native-format typed workflows, replay, portable Windows/Linux verification and refrozen corruption refusal. Qualification remains scoped; see '+Q)
 roles={x['paper_id']:copy.deepcopy(x)for x in read(root/source.FOLDER/'ROLES.json')['roles']};reviews=read(root/source.FOLDER/'REVIEW-ENTRIES.json')['reviews'];require(list(roles)==list(source.ROLES)and len(reviews)==4,'Wrong primary review population')
 for pid,role in roles.items():
  paper=next(x for x in papers if x['paper_id']==pid);use=next(x for x in paper['tools']if x['tool_id']=='pbmm2');review=next(x for x in reviews if x['pmcid']==paper['pmcid']);require(review['doi']==paper['doi']and review['evidence_paragraph_sha256']==use['evidence_paragraph_sha256'],'Primary evidence differs')
  require(not any(x['pmcid']==paper['pmcid']and x['tool_id']=='pbmm2'for x in ledger['reviews']),'Duplicate source-review credit');ledger['reviews'].append(copy.deepcopy(review))
  use.update(use_summary=review['use_summary'],implementation_scope_note=review['implementation_scope_note'],supporting_evidence=copy.deepcopy(review['supporting_evidence']),review_status='individually-reviewed-primary-source-roles; whole-paper-method-review-open',evidence_selection='individually-reviewed-expanded-primary-source-census');role.update(exact_author_recipe_qualified=False,runtime_mapping_backend_available=True)
 require(len(ledger['reviews'])==1940 and sum(len(p['tools'])for p in papers)==2741,'Missing/duplicate review or observation');tool['paper_role_qualification']=roles
 summary['implementation_status_counts']['upstream-adapter-validated']+=1;summary['implementation_status_counts']['missing']-=1;summary['integration_family_counts']['scoped']+=1;summary['integration_family_counts']['including_existing_adapters']+=1
 if 'upstream'in summary['integration_family_counts']:summary['integration_family_counts']['upstream']+=1
 summary.update(reviewed_tool_uses=1940,pending_tool_use_reviews=801,current_effective_registry=FOLDER+'/REGISTRY.json')
 after={META[i]:encoded(x)for i,x in [(0,papers),(1,inventory),(2,summary),(7,ledger)]}
 preface='# Current original pbmm2 mapping workflows\n\n'+NOTICE+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [guide](../../pbmm2.md), [primary roles](../pbmm2-source-audit/ROLES.json) and [qualification](../pbmm2-validation/QUALIFICATION.json). Earlier checkpoints below are historical.\n\n---\n\n'
 for name in META[3:7]:after[name]=preface.encode()+before[name]
 notice='<aside id="pbmm2-notice"><h2>Original pbmm2 mapping and native CIGAR checks</h2><p>'+html.escape(NOTICE)+'</p><p>'+html.escape(SCOPE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p></aside>'
 for name in META[8:]:
  page=before[name].decode();require('id="pbmm2-notice"'not in page,'Already admitted')
  if name==META[8]:
   match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S);require(match and json.loads(match[2])==dict(papers=json.loads(before[META[0]]),tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Prior report payload differs');payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
  body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Report boundary differs');after[name]=(page[:body.end()]+notice+'<details id="pbmm2-prior"><summary>Historical catalog checkpoints</summary>'+page[body.end():marker]+'</details>'+page[marker:]).encode()
 coverage=dict(schema_version=1,scope=SCOPE,required_action=ACTION,roles=roles,new_native_family_credit=0,new_upstream_family_credit=1,new_source_reviews=4,recovered_observations=0,qualification=Q,source_review=source.FOLDER,exact_author_recipe_qualified=False,complete_binary_source_equivalence_certified=False,whole_goal_complete=False)
 return after,registry,coverage
def check(root):
 folder=root/FOLDER;base=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==base['archive']and pin(folder/'PREVIOUS-SHA256SUMS')==base['previous_checksums']and pin(root/'tools/literature/pbmm2_catalog.py')==base['transformer'],'Forward admission baseline differs');verify(root)
 for name,expected in base['source_files'].items():require(pin(root/name)==expected,'Primary role declaration differs')
 with zipfile.ZipFile(folder/'metadata-before.zip')as z:require(set(z.namelist())==set(META),'Baseline metadata family differs');before={n:z.read(n)for n in META}
 require({n:identity(raw)for n,raw in before.items()}==base['metadata'],'Baseline bytes differ');registry=predecessor(root,before);after,registry,coverage=transition(root,before,registry,read(root/Q));require(all((root/n).read_bytes()==raw for n,raw in after.items()),'Catalog is not the exact pbmm2 transition');require(read(folder/'REGISTRY.json')==registry and read(root/COVERAGE)==coverage,'Current role registry differs');verify_fixed_cohort(root,root/DATA,json.loads(after[META[0]]))
 previous=checksums((folder/'PREVIOUS-SHA256SUMS').read_bytes());sums=checksums((root/DATA/'SHA256SUMS').read_bytes());require(set(sums)==set(previous)|{Path(COVERAGE).name,Path(PROOF).name},'Checksum family differs');changed={Path(n).name for n in META if n.startswith(DATA+'/')};require(all(sums[n]==v or n in changed for n,v in previous.items()),'Unrelated checksum differs');verify_sums(root,sums)
 require(read(root/PROOF)==dict(schema_version=1,status='PASS_PBMM2_FORWARD_ADMISSION',totals=totals(),raw_source_text_published=False,literature_pages_published=False),'Admission proof differs');return totals()
def apply(root,private,out,cache):
 require(not(root/FOLDER).exists()and out.is_relative_to(private)and not out.exists(),'Require NEW admission/evidence paths');source.audit(root,cache);verify(root,private);prior.check(root)
 before={n:(root/n).read_bytes()for n in META};registry=predecessor(root,before);after,registry,coverage=transition(root,before,registry,read(root/Q));previous=(root/DATA/'SHA256SUMS').read_bytes();rows=checksums(previous);require(len(rows)==932,'Prior checksum population differs');verify_sums(root,rows);out.mkdir();folder=root/FOLDER;folder.mkdir()
 with zipfile.ZipFile(folder/'metadata-before.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9)as z:
  for name,raw in before.items():z.writestr(name,raw)
 names=[source.FOLDER+'/'+n for n in ['CENSUS.json','ROLES.json','REVIEW-ENTRIES.json']]+['tools/literature/pbmm2_source_audit.py'];(folder/'PREVIOUS-SHA256SUMS').write_bytes(previous);(folder/'BASELINE.json').write_bytes(encoded(dict(schema_version=1,archive=pin(folder/'metadata-before.zip'),previous_checksums=pin(folder/'PREVIOUS-SHA256SUMS'),metadata={n:identity(raw)for n,raw in before.items()},transformer=pin(Path(__file__)),source_files={n:pin(root/n)for n in names})));(out/'PLAN.json').write_bytes(encoded(dict(before={n:identity(raw)for n,raw in before.items()},after={n:identity(raw)for n,raw in after.items()},totals=totals())))
 for i,(name,raw)in enumerate(after.items()):
  require((root/name).read_bytes()==before[name],'Concurrent catalog edit');stage=out/('stage-'+str(i));stage.write_bytes(raw);os.replace(stage,root/name)
 (folder/'REGISTRY.json').write_bytes(encoded(registry));(root/COVERAGE).write_bytes(encoded(coverage));(root/PROOF).write_bytes(encoded(dict(schema_version=1,status='PASS_PBMM2_FORWARD_ADMISSION',totals=totals(),raw_source_text_published=False,literature_pages_published=False)))
 for name in [n for n in META if n.startswith(DATA+'/')]+[COVERAGE,PROOF]:rows[Path(name).name]=pin(root/name)['sha256']
 newline='\r\n'if previous.count(b'\n')==previous.count(b'\r\n')else'\n';(root/DATA/'SHA256SUMS').write_bytes(''.join(value+'  '+name+newline for name,value in rows.items()).encode());result=check(root);(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_PBMM2_FORWARD_ADMISSION',totals=result)));return result
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);p.add_argument('--out',type=Path);p.add_argument('--xml-cache',type=Path);p.add_argument('--apply',action='store_true');a=p.parse_args()
 try:
  if a.apply:require(a.private and a.out and a.xml_cache,'Apply needs retained evidence, NEW output and original XML cache');result=apply(a.root.resolve(),a.private.resolve(),a.out.resolve(),a.xml_cache.resolve())
  else:result=check(a.root.resolve())
  print(json.dumps(result,indent=2))
 except Exception as e:raise SystemExit('FAIL2: no complete pbmm2 catalog admission; retain evidence and correct cause: '+str(e))
