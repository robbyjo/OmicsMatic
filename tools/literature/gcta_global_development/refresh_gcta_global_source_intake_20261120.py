from pathlib import Path
import ast,hashlib,json
R=Path('D:/projects/JLinAlg');T=R/'build/gc-7';O=T/'build/gcta-global-1/source-intake-1'
source=(R/'build/intake_gcta_census_20261120.py').read_text(encoding='utf-8')
for old,new in {"T=R/'build/gc-1';O=T/'build/gcta-high-impact-1/source-intake-1'":"T=R/'build/gc-7';O=T/'build/gcta-global-1/source-intake-1'","baseline_commit='bdf1c3e8067896131d31cbca3e7de9191aa93a0d'":"baseline_commit='cfeee186d5068a2af3be33ef9fb69508feefdd17'"}.items():assert old in source;source=source.replace(old,new)
ast.parse(source);exec(compile(source,__file__,'exec'))
prior=json.loads((R/'build/gc-1/build/gcta-high-impact-1/source-intake-1/CANDIDATES.json').read_bytes());current=json.loads((O/'CANDIDATES.json').read_bytes())
assert {k:v for k,v in prior.items() if k!='baseline_commit'}=={k:v for k,v in current.items() if k!='baseline_commit'}
reviewed=[]
for paper in current['candidates']:
    reviewed.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],existing_tool_ids=[t['tool_id'] for t in paper['existing_observations']],anchors=[dict(paragraph_index=a['paragraph_index'],section=a['section'],paragraph_sha256=a['paragraph_sha256'],matching_text_sha256=a['matching_text_sha256'],matched_literals=[m['literal'] for m in a['matched']]) for a in paper['anchors']]))
result=dict(status='PASS_FRESH_250_SOURCE_IDENTITIES_AND_EXACT_22_CANDIDATE_READBACK',source_identities=current['source_identities'],candidates=reviewed,all_250_primary_XML_hashes_and_DOIs_checked=True,prior_census_exact_except_base_label=True,raw_fulltext_private=True,canonical_metadata_modified=False,new_reviews_added=0)
(O/'FRESH-SOURCE-READBACK.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
print('Current source observation membership',json.dumps([{k:p[k] for k in ['paper_id','existing_tool_ids']} for p in reviewed]),flush=True)
