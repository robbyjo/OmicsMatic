from pathlib import Path
import gzip,hashlib,json,re,sys,xml.etree.ElementTree as ET
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');R=T.parents[1];B=T/'build/mrpresso-catalog-178'
sys.path.insert(0,str(T/'tools/literature'));from mrpresso_catalog import catalog as c,primary as p
context=B/'source-context-final';context.mkdir(exist_ok=True)
for name in c.sources():
 dest=context/name;dest.parent.mkdir(parents=True,exist_ok=True)
 if dest.exists():assert dest.read_bytes()==(T/name).read_bytes()
 else:dest.write_bytes((T/name).read_bytes())
before=json.loads((B/'plan-windows-1/result/PLAN.json').read_bytes())['source_code'];changed=[name for name,wanted in before.items() if pin(T/name)!=wanted]
assert set(changed)=={'tools/literature/mrpresso_catalog/model.py','tools/literature/mrpresso_catalog/check_ui.cjs'}
model=(T/changed[0]).read_bytes() if changed[0].endswith('model.py') else (T/'tools/literature/mrpresso_catalog/model.py').read_bytes()
start=model.index(b'VISIBLE_ACTION=');end=model.index(b'\ndef encoded',start)
old_notice='''NOTICE=('<aside id="mrpresso-catalog-notice" style="box-sizing:border-box;max-width:calc(100% - 2rem);margin:1rem;'
 'padding:1rem;border:2px solid #a56613;background:#fff8e8;color:#302718;overflow-wrap:anywhere">'
 '<h2>MR-PRESSO: native original-profile workflows and source roles</h2><p><strong>Warnings and required actions:</strong> '
 +html.escape(ACTION)+'</p><p>'+html.escape(CURRENT)+'</p><p>'+html.escape(SCOPE+' '+NOTE)+'</p><p><a href="'
 +'https://github.com/robbyjo/OmicsMatic/blob/main/'+GUIDE+'">Commands, source limits and recovery</a>.</p></aside>')

'''.encode()
reconstructed=model[:start]+old_notice.rstrip(b'\n')+b'\n'+model[end:]
versions={'tools/literature/mrpresso_catalog/model.py':reconstructed}
ui=(T/'tools/literature/mrpresso_catalog/check_ui.cjs').read_text()
new="  for(const pattern of [/153 scoped families/,/1,691 reviewed/,/1,031 pending/,/28 native/,/125 upstream/,/SKIP3/,/FAIL2/,/above one/])assert.match(await notice.innerText(),pattern);"
old="  for(const pattern of [/153 scoped families/,/1,691 reviewed/,/1,031 pending/,/28 native/,/125 upstream/,/258 missing/,/20 method-overlap/,/SKIP3/,/FAIL2/,/P135/,/P150/,/P189/,/P224/,/P177/,/above1/])assert.match(await notice.innerText(),pattern);"
assert new in ui;ui=ui.replace(new,old)
extra="  const details=notice.locator('#mrpresso-source-details');await details.evaluate(node=>node.open=true);\n  for(const pattern of [/258 missing/,/20 method-overlap/,/P135/,/P150/,/P189/,/P224/,/P177/])assert.match(await notice.innerText(),pattern);\n  await details.evaluate(node=>node.open=false);\n"
assert extra in ui;ui=ui.replace(extra,'').replace('/SKIP3: dependent analysis skipped/','/SKIP3: DEPENDENT ANALYSIS SKIPPED/')
versions['tools/literature/mrpresso_catalog/check_ui.cjs']=ui.encode()
original=B/'source-context-plan1';original.mkdir(exist_ok=True)
for name,raw in versions.items():
 assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==before[name],name
 dest=original/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
save(B/'SOURCE-CONTEXTS.json',dict(status='PASS_EXACT_SUPERSEDED_AND_FINAL_SOURCE_CONTEXTS',
 first_plan_changed_files={name:pin(original/name) for name in versions},final_source_files={name:pin(context/name) for name in c.sources()},
 reason='Shortened the visible mobile warning actions and retained full source/failure context in an expandable section; no scientific settings changed.',
 original_native_scientific_source_changed=False))
# Supplementary search catches API/expanded-name mentions beyond the named-tool
# census. No full paragraphs, code bodies or primary text are exported.
known={x['paper_id']:x for x in json.loads((T/'docs/literature/mrpresso-catalog-sources/ROLES.json').read_bytes())['primary_identities']}
api=re.compile(r'\bmr_presso\b',re.I);full=re.compile(r'pleiotropy[ -]+residual[ -]+sum.{0,20}outlier',re.I);found=[]
for paper_id,identity in known.items():
 with gzip.open(R/'build/literature-review/xml'/(identity['pmcid']+'.xml.gz'),'rb') as f:raw=f.read(64*1024*1024+1)
 assert len(raw)<=64*1024*1024 and hashlib.sha256(raw).hexdigest()==identity['xml_sha256']
 tree=ET.fromstring(raw);dois=[x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi'];assert len(dois)==1 and dois[0].lower()==identity['doi'].lower()
 body=tree.find('body')
 if body is None:continue
 counts={}
 for element in body.iter():
  if element.tag not in ('p','code','preformat','table-wrap'):continue
  counts[element.tag]=counts.get(element.tag,0)+1;text=p.words(element);normalized=p.normalized(text)
  kinds=[label for label,pattern in [('public_api_identifier',api),('expanded_method_name',full)] if pattern.search(normalized)]
  if kinds:found.append(dict(paper_id=paper_id,element_kind=element.tag,element_ordinal=counts[element.tag],text_identity=p.pin(text.encode()),match_kinds=kinds))
assert not any('public_api_identifier' in x['match_kinds'] for x in found)
assert {x['paper_id'] for x in found}=={'P150','P189'}
save(B/'SUPPLEMENTARY-ALIAS-SCREEN.json',dict(status='PASS_ALL250_BODY_CODE_AND_EXPANDED_NAME_SCREEN',papers_checked=250,
 element_kinds=['p','code','preformat','table-wrap'],public_api_identifier_candidates=0,expanded_name_candidates=found,
 additional_positive_papers_found=0,existing_named_roles_unchanged=True,full_primary_texts_exported=False,whole_goal_complete=False))
print('Retained hash-exact first/final preview source contexts; supplementary all250 API/body screen found no additional positive paper or reported caller settings.')
