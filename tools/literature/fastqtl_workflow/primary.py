"""Fresh, full-cohort FastQTL name/pipeline census. Primary article text is never exported."""
import gzip,hashlib,json,re,unicodedata,xml.etree.ElementTree as E
from pathlib import Path
import sys

def audit(root,cache):
    sys.path.insert(0,str(root/'tools/literature'))
    from collect_omics_review import paragraphs,words,matching_words
    papers=json.loads((root/'docs/literature/omics-2023-2026/papers.json').read_bytes());pattern=re.compile(r'fast[\s_-]*qtl',re.I);identities=[];anchors=[];structures=[]
    assert len(papers)==250
    for paper in papers:
        with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb') as stream:raw=stream.read(67108865)
        assert len(raw)<=67108864 and hashlib.sha256(raw).hexdigest()==paper['xml_sha256'],'Primary XML identity differs'
        tree=E.fromstring(raw);doi=[x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi'];assert len(doi)==1 and doi[0].lower()==paper['doi'].lower(),'DOI identity differs'
        identities.append({k:paper[k] for k in ('paper_id','doi','pmcid','xml_sha256')});standard=paragraphs(tree);seen={p['text'] for p in standard}
        for row in standard:
            matches=list(pattern.finditer(unicodedata.normalize('NFKC',row.get('match_text',row['text']))))
            if matches:anchors.append(dict(paper_id=paper['paper_id'],paragraph_index=row['paragraph_index'],section=row['section'],paragraph_sha256=hashlib.sha256(row['text'].encode()).hexdigest(),matches=sorted({m.group() for m in matches})))
        for tag in ('p','table-wrap','code','preformat'):
            for i,e in enumerate(tree.findall('.//body//'+tag),1):
                if (tag!='p' or words(e) not in seen) and pattern.search(matching_words(e)):structures.append(dict(paper_id=paper['paper_id'],tag=tag,ordinal=i,element_id=e.get('id'),text_sha256=hashlib.sha256(words(e).encode()).hexdigest()))
    return dict(status='ALL250_FASTQTL_NAME_AND_PIPELINE_CANDIDATES_NOT_YET_ADMITTED',papers_checked=250,identities=identities,anchors=anchors,structures=structures,canonical_modified=False)
