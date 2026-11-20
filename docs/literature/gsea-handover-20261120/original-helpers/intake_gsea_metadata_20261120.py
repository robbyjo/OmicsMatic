from pathlib import Path
import hashlib,json,urllib.request,traceback
R=Path('D:/projects/JLinAlg');G=R/'build/gs-1';O=G/'build/gsea-high-impact-1/upstream-metadata-1';O.mkdir(exist_ok=False)
def save(n,v):(O/n).write_bytes((json.dumps(v,indent=2)+'\n').encode())
def fetch(name,url):
    req=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-GSEA-source-audit','Accept':'application/vnd.github+json'})
    with urllib.request.urlopen(req,timeout=45) as f:raw=f.read(5000001);headers=dict(f.headers)
    assert len(raw)<=5000000
    (O/name).write_bytes(raw);return raw,dict(url=url,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),headers=headers)
try:
    raw,tags_meta=fetch('tags.json','https://api.github.com/repos/GSEA-MSigDB/gsea-desktop/tags?per_page=100')
    tags=json.loads(raw)
    raw,master_meta=fetch('master.json','https://api.github.com/repos/GSEA-MSigDB/gsea-desktop/commits/master')
    master=json.loads(raw)
    save('COMPLETED.json',dict(status='PASS_OFFICIAL_GSEA_SOURCE_TAG_AND_MASTER_INTAKE',tags=tags_meta,master=master_meta,
        tag_count=len(tags),master_commit=master['sha'],software_or_gene_sets_downloaded=False,registration_submitted=False))
    print('Official source tags:',[(t['name'],t['commit']['sha']) for t in tags],flush=True)
    print('Official source master:',master['sha'],flush=True)
except Exception as error:
    save('FAILURE.json',dict(error=repr(error),detail=traceback.format_exc()));raise
