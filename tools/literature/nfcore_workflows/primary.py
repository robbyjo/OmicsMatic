# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit all 250 primary XMLs and distinguish every reported nf-core pipeline role."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,sys,unicodedata,xml.etree.ElementTree as E
from collect_omics_review import paragraphs

ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/nfcore-source-audit'
TARGETS=['P002','P027','P032','P050','P169','P228']
PATTERN=re.compile(r'(?<![A-Za-z0-9_])nf[-\u2010-\u2015 ]?core(?![A-Za-z0-9_])',re.I)

def require(value,message):
    if not value:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return(json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()
def census(papers_bytes,cache):
    papers=json.loads(papers_bytes)
    require(len(papers)==250 and len({p['doi']for p in papers})==250 and len({p['paper_id']for p in papers})==250,'Require the complete unique frozen 250-paper corpus')
    anchors=[];targets=[];texts={};extras=[]
    for paper in papers:
        pmc=paper['pmcid'];require(re.fullmatch('PMC[0-9]+',pmc),'Unsafe PMCID')
        with gzip.open(Path(cache)/(pmc+'.xml.gz'),'rb')as f:raw=f.read(67108865)
        require(len(raw)<=67108864 and pin(raw)['sha256']==paper['xml_sha256'],'Changed primary XML: '+pmc)
        doc=E.fromstring(raw)
        require(paper['doi'].lower()in{''.join(e.itertext()).strip().lower()for e in doc.findall('.//article-meta/article-id')if e.get('pub-id-type')=='doi'},'Changed primary DOI: '+pmc)
        rows=paragraphs(doc)
        for row in rows:row['paragraph_sha256']=pin(row['text'].encode())['sha256']
        hits=[row for row in rows if PATTERN.search(unicodedata.normalize('NFKC',row['match_text']))]
        anchors.extend(dict(paper_id=paper['paper_id'],**{k:row[k]for k in('paragraph_index','section','paragraph_sha256')})for row in hits)
        uses=[u for u in paper['tools']if u['tool_id']=='nfcore']
        if uses:
            require(len(uses)==1,'Duplicate original nf-core observation');u=uses[0]
            require(next(r for r in rows if r['paragraph_index']==u['evidence_paragraph_index'])['paragraph_sha256']==u['evidence_paragraph_sha256'],'Changed original evidence paragraph')
            targets.append(dict(paper_id=paper['paper_id'],doi=paper['doi'],pmcid=pmc,xml_sha256=paper['xml_sha256'],original_observation=u))
            texts[paper['paper_id']]={r['paragraph_index']:r for r in rows}
        for tag in('table-wrap','fig','supplementary-material'):
            for i,node in enumerate(doc.iter(tag)):
                text=' '.join(node.itertext())
                if PATTERN.search(unicodedata.normalize('NFKC',text)):extras.append(dict(paper_id=paper['paper_id'],tag=tag,index=i,text_sha256=pin(text.encode())['sha256']))
    require([p['paper_id']for p in targets]==TARGETS,'Changed original nf-core observation population')
    require({a['paper_id']for a in anchors}==set(TARGETS),'Additional full-corpus nf-core mentions require review')
    return dict(status='PRIMARY_SOURCE_CENSUS_NO_IMPLEMENTATION_CREDIT',papers_identity=pin(papers_bytes),primary_XML_checked=250,targets=targets,anchors=anchors,other_structures=extras,catalog_admitted=False,new_family_credit=0,new_review_credit=0),texts

def audit(papers_bytes,cache,root=ROOT):
    measured,texts=census(papers_bytes,cache);frozen=json.loads((root/FOLDER/'CENSUS.json').read_bytes())
    require(measured==frozen,'Changed frozen source census; restore exact predecessor papers metadata')
    roles=json.loads((root/FOLDER/'ROLES.json').read_bytes())
    require(roles['status']=='SIX_NFCORE_SOURCE_ROLES_NOT_ADMITTED'and roles['census_identity']==pin(encoded(frozen))and[r['paper_id']for r in roles['roles']]==TARGETS,'Changed role/census population')
    reviewed=[];branches=[]
    for role,target in zip(roles['roles'],measured['targets'],strict=True):
        require(role['source']==target,'Changed raw source observation')
        require(all(role[k]is False for k in('catalog_admitted','paper_edition_qualified','original_paper_analysis_reexecuted','whole_paper_reproduced')),'Expanded source qualification claim')
        for anchor in role['reviewed_paragraphs']:
            row=texts[role['paper_id']][anchor['paragraph_index']]
            require(anchor=={k:row[k]for k in('paragraph_index','section','paragraph_sha256')},'Changed reviewed primary paragraph')
            reviewed.append(dict(paper_id=role['paper_id'],**anchor))
        for branch in role['pipelines']:
            row=texts[role['paper_id']][branch['paragraph_index']]
            text=unicodedata.normalize('NFKC',row['text'])
            require(branch['pipeline_literal']in text and(branch['version_literal']is None or branch['version_literal']in text),'Changed literal pipeline/version context')
            require(branch['version_reported']is None or branch['version_literal']is not None,'Inferred exact paper edition')
            require(not branch['pipeline_name_inferred']or branch['version_reported']is None,'Ambiguous pipeline cannot qualify an exact edition')
            require(not branch['qualified']and branch['pipeline']in('eager','rnasplice','chipseq','atacseq','hic'),'Expanded pipeline claim')
            for setting in branch['literal_settings']:
                require(setting in text,'Changed reported literal setting: '+setting)
            branches.append(branch)
    require(reviewed==measured['anchors'],'Incomplete or extra eligible source anchors')
    require([r['source']['original_observation']['version_reported_near_name']for r in roles['roles']]==[None]*6,'Raw near-name versions changed')
    require(len(branches)==10 and sum(b['version_reported']is None for b in branches)==3,'Changed ten-branch source scope')
    return dict(status='PASS_250_PRIMARY_XML_SIX_NFCORE_ROLES_TEN_PIPELINE_BRANCHES',primary_XML_checked=250,roles=6,pipeline_branches=10,eligible_anchors=len(reviewed),other_structures=len(measured['other_structures']),catalog_admitted=False,new_family_credit=0,new_review_credit=0,whole_paper_reproduced=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--cache',required=True,type=Path);p.add_argument('--papers',type=Path,default=ROOT/'docs/literature/omics-2023-2026/papers.json');p.add_argument('--root',type=Path,default=ROOT)
    a=p.parse_args()
    try:print(json.dumps(audit(a.papers.read_bytes(),a.cache,a.root),indent=2));return 0
    except(FileNotFoundError,PermissionError)as e:print('SKIP3: dependent source audit skipped. '+str(e)+'. Restore exact permitted primary XML/source metadata or retain this skip.',file=sys.stderr);return 3
    except Exception as e:print('FAIL2: no complete source audit. '+repr(e)+'. Preserve evidence and correct the source mismatch; never refresh old hashes.',file=sys.stderr);return 2
if __name__=='__main__':sys.exit(main())
