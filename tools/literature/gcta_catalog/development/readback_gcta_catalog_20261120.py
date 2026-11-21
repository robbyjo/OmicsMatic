from pathlib import Path
from collections import Counter
import hashlib,json,sys,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/gc-8';B=T/'build/gcta-catalog-1';O=B/'independent-readback-1';O.mkdir();sys.path.insert(0,str(T/'tools/literature'))
from gcta_catalog import model as m,evidence as e
from gsea_catalog.model import registry as old_registry
from catalog_source_integrity import verify_source
from gsea_dispatch_transition import DISPATCH,BEFORE
registry=m.registry();old=old_registry();assert len(registry)==len({r['id'] for r in registry})==485
assert [r for r in registry if r['id'] not in ['gcta','cojo']]==[r for r in old if r['id'] not in ['gcta','cojo']]
with zipfile.ZipFile(T/e.FOLDER/'metadata-before.zip') as archive:before={n:json.loads(archive.read(n)) for n in [m.META[0],m.META[1],m.META[2],m.META[5]]}
papers=json.loads((T/m.META[0]).read_bytes());inventory=json.loads((T/m.META[1]).read_bytes());summary=json.loads((T/m.META[2]).read_bytes());reviews=json.loads((T/m.META[5]).read_bytes())
assert len(papers)==250 and len(inventory)==446 and sum(len(p['tools']) for p in papers)==2715
assert len(reviews['reviews'])==1575 and reviews['reviews'][:1548]==before[m.META[5]]['reviews'];assert len({(r['pmcid'],r['tool_id']) for r in reviews['reviews']})==1575
counts=Counter(r['implementation_status'] for r in inventory);assert dict(counts)==summary['implementation_status_counts'] and counts['upstream-adapter-validated']==115 and counts['native-subset-validated']==26 and counts['validated-family-alias']==1 and counts['method-overlap']==33 and counts['missing']==259
assert summary['integration_family_counts']==dict(scoped=141,native=26,upstream=115,including_existing_adapters=144,validated_alias_labels_excluded_from_extra_credit=1)
changes=[]
for a,b in zip(before[m.META[0]],papers,strict=True):
    assert {k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'}
    assert [r for r in a['tools'] if r['tool_id'] not in ['gcta','cojo']]==[r for r in b['tools'] if r['tool_id'] not in ['gcta','cojo']]
    oldids=[r['tool_id'] for r in a['tools']];newids=[r['tool_id'] for r in b['tools']]
    if oldids!=newids:changes.append(dict(paper_id=a['paper_id'],added=[x for x in newids if x not in oldids],removed=[x for x in oldids if x not in newids]))
assert changes==[dict(paper_id='P112',added=['gcta'],removed=[]),dict(paper_id='P127',added=[],removed=['cojo']),dict(paper_id='P169',added=['gcta'],removed=[])]
assert sum(r['tool_id']=='gcta' for p in papers for r in p['tools'])==19 and sum(r['tool_id']=='cojo' for p in papers for r in p['tools'])==8
gsea_proof=json.loads((T/e.OLD_PROOF).read_bytes());verify_source(T,T/m.DATA,DISPATCH,BEFORE['sha256']);verify_source(T,T/m.DATA,m.META[5],gsea_proof['before'][m.META[5]]['sha256'])
source=json.loads((T/'docs/literature/gcta-global-development/PROFILE.json').read_bytes())
for n,w in source['source_inputs'].items():
    if n.startswith('src/'):
        raw=(T/n).read_bytes();assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==w,n
record=dict(status='PASS_INDEPENDENT_GCTA_COUNTS_OLD_REVIEWS_REGISTRY_ALIAS_AND_MULTI_STEP_SOURCE_INVERSES',papers=250,registry_definitions=485,unrelated_registry_definitions_exact=483,named_tool_labels=446,observations=2715,reviewed=1575,pending=1140,integration_families=141,native=26,upstream=115,validated_alias_labels=1,alias_extra_credit=0,old_reviews_exact=1548,changes=changes,scientific_sources_unchanged=True,dispatcher_and_review_multi_step_inverses_passed=True)
(O/'RESULT.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8',newline='\n');print('PASS independent counts,485 definitions,exact old reviews,and multi-step source inverses',flush=True)
