"""Apply reviewed prose to the frozen cohort without re-running extraction.

This is allowed only when cached extraction reproduces every current primary
identity and evidence pointer. Registry changes still require full enrichment.
"""
import datetime as dt
import gzip
import hashlib
import json
from pathlib import Path
from collect_omics_review import CACHE,DEST,publication_record


def main():
    sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
    registry=Path(__file__).with_name('tool-registry.json');provenance=json.loads((DEST/'catalog-enrichment.json').read_text(encoding='utf-8'));assert provenance['registry_sha256']==sha(registry),'Registry changed; run full enrichment first'
    path=DEST/'papers.json';old=json.loads(path.read_text(encoding='utf-8'));new=[]
    for paper in old:
        cached=json.loads((CACHE/'extracted'/f"{paper['pmcid']}.json").read_text(encoding='utf-8'));updated=publication_record(cached);updated['paper_id']=paper['paper_id']
        for field in ['paper_id','pmcid','doi','xml_sha256','first_publication_date','journal','fulltext_url']:assert updated[field]==paper[field],(paper['paper_id'],field)
        pointer=lambda tool:(tool['tool_id'],tool['evidence_paragraph_index'],tool['evidence_paragraph_sha256'])
        assert [pointer(tool) for tool in updated['tools']]==[pointer(tool) for tool in paper['tools']],paper['paper_id']
        new.append(updated)
    prior=sha(path);history=CACHE/'catalog-history';history.mkdir(exist_ok=True);archived=history/(prior+'.json.gz')
    if not archived.exists():archived.write_bytes(gzip.compress(path.read_bytes(),mtime=0))
    path.write_text(json.dumps(new,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    result=dict(status='passed-reviewed-prose-refresh; primary-identities-and-pointers-unchanged',papers=len(new),observations=sum(len(p['tools']) for p in new),reviewed_uses=sum(t['review_status']=='usage-summary-reviewed; paper-wide-completeness-review-open' for p in new for t in p['tools']),previous_catalog_sha256=prior,catalog_sha256=sha(path),registry_sha256=sha(registry),usage_reviews_sha256=sha(Path(__file__).with_name('reviewed-uses.json')),refresher_sha256=sha(Path(__file__)),recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat())
    target=DEST/'catalog-review-refresh.json'
    if target.exists():
        previous=json.loads(target.read_text(encoding='utf-8'));result['history']=previous.get('history',[])+[{k:v for k,v in previous.items() if k!='history'}]
    target.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))


if __name__=='__main__':main()
