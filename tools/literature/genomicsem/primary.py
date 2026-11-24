"""Recheck all 250 primary XMLs and freeze the five distinct Genomic SEM roles."""
from pathlib import Path
import gzip,hashlib,json,re,unicodedata,xml.etree.ElementTree as E
from collect_omics_review import paragraphs

DIRECTORY='docs/literature/genomicsem-source-audit'
TARGETS=['P035','P125','P131','P140','P150']
PATTERN=re.compile(r'(?<![A-Za-z0-9_])Genomic\s*SEM(?![A-Za-z0-9_])',re.I)

def require(value,message):
    if not value:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def census(root,cache):
    root,cache=Path(root),Path(cache)
    raw=(root/'docs/literature/omics-2023-2026/papers.json').read_bytes()
    papers=json.loads(raw);require(len(papers)==250,'Require the complete frozen 250-paper corpus')
    require(len({p['paper_id'] for p in papers})==250 and len({p['doi'] for p in papers})==250,'Duplicate paper/DOI')
    anchors=[];targets=[];texts={};structures=[]
    for p in papers:
        name=p['pmcid'];require(re.fullmatch(r'PMC[0-9]+',name),'Unsafe PMCID')
        path=cache/(name+'.xml.gz');require(path.is_file(),'Missing primary XML: '+name)
        with gzip.open(path,'rb') as f:xml=f.read(67108865)
        require(len(xml)<=67108864 and pin(xml)['sha256']==p['xml_sha256'],'Primary XML identity differs: '+name)
        document=E.fromstring(xml)
        doi={''.join(e.itertext()).strip().lower() for e in document.findall('.//article-meta/article-id') if e.get('pub-id-type')=='doi'}
        require(p['doi'].lower() in doi,'Primary XML DOI differs: '+name)
        rows=paragraphs(document)
        for r in rows:r['paragraph_sha256']=pin(r['text'].encode('utf-8'))['sha256']
        texts[p['paper_id']]={r['paragraph_index']:r for r in rows}
        for r in rows:
            if PATTERN.search(unicodedata.normalize('NFKC',r['match_text'])):
                anchors.append(dict(paper_id=p['paper_id'],paragraph_index=r['paragraph_index'],section=r['section'],paragraph_sha256=r['paragraph_sha256']))
        # Inspect source outside the intake's eligible prose without promoting it
        # to a positive execution. These occurrences are review targets only.
        for tag in ('table-wrap','fig','supplementary-material'):
            for i,node in enumerate(document.iter(tag)):
                if PATTERN.search(unicodedata.normalize('NFKC',' '.join(node.itertext()))):
                    text=' '.join(node.itertext());structures.append(dict(paper_id=p['paper_id'],tag=tag,index=i,text_sha256=pin(text.encode())['sha256']))
        uses=[u for u in p['tools'] if u['tool_id']=='genomicsem']
        if uses:
            require(len(uses)==1,'Duplicate Genomic SEM observation');u=uses[0]
            require(u['evidence_paragraph_index'] in texts[p['paper_id']],'Missing original evidence paragraph')
            require(texts[p['paper_id']][u['evidence_paragraph_index']]['paragraph_sha256']==u['evidence_paragraph_sha256'],'Original evidence identity differs')
            targets.append(dict(paper_id=p['paper_id'],doi=p['doi'],pmcid=name,xml_sha256=p['xml_sha256'],original_observation=u))
    require([p['paper_id'] for p in targets]==TARGETS,'Changed full-corpus target set')
    return dict(schema_version=1,status='PRIMARY_SOURCE_AUDIT_NO_CATALOG_ADMISSION',primary_XML_checked=250,papers_identity=pin(raw),targets=targets,anchors=anchors,other_structures=structures),texts

def audit(root,cache):
    root=Path(root);measured,texts=census(root,cache)
    require(measured==json.loads((root/DIRECTORY/'CENSUS.json').read_bytes()),'Changed frozen full-corpus source census')
    raw=(root/DIRECTORY/'ROLES.json').read_bytes()
    require(pin(raw)['sha256']=='805d152a048c476453d1ff3318e25171943c4a2b9004d584589cd760dd5bed04','Changed reviewed source-role interpretations')
    roles=json.loads(raw)
    require(roles['status']=='FIVE_REVIEWED_SOURCE_ROLES_NOT_ADMITTED' and roles['census_identity']==pin(encoded(measured)),'Changed role/census binding')
    require([r['paper_id'] for r in roles['roles']]==TARGETS,'Changed role set/order')
    for role in roles['roles']:
        pid=role['paper_id'];target=next(p for p in measured['targets'] if p['paper_id']==pid)
        require(role['source']==target,'Changed raw source observation')
        require(all(role[k] is False for k in ('catalog_admitted','paper_edition_qualified','original_paper_analysis_reexecuted','whole_paper_reproduced')),'Expanded implementation or paper claim')
        for anchor in role['reviewed_paragraphs']:
            r=texts[pid][anchor['paragraph_index']]
            require(anchor=={k:r[k] for k in ('paragraph_index','section','paragraph_sha256')},'Changed manually reviewed paragraph')
        for version in role['reported_edition_contexts']:
            r=texts[pid][version['paragraph_index']]
            require(version['literal'] in unicodedata.normalize('NFKC',r['text']),'Changed source version context')
    by={r['paper_id']:r for r in roles['roles']}
    require(by['P035']['reported_edition_contexts'][0]['literal']=='0.5.0','Do not normalize the reported 0.5.0 to 0.0.5')
    require(by['P140']['reported_edition_contexts'][0]['literal']=='0.0.3f','Do not substitute current GenomicSEM for historical 0.0.3f')
    require(by['P150']['source']['original_observation']['version_reported_near_name']=='0.0.5','Changed raw near-name version')
    require(all(by[p]['source']['original_observation']['version_reported_near_name'] is None for p in TARGETS[:-1]),'Contextual versions must not overwrite raw near-name fields')
    return dict(status='PASS_250_PRIMARY_XML_AND_FIVE_SOURCE_ROLE_BINDINGS',primary_XML_checked=250,roles=5,anchors=len(measured['anchors']),other_structures=len(measured['other_structures']),catalog_admitted=False,new_family_credit=0,new_review_credit=0,whole_goal_complete=False)
