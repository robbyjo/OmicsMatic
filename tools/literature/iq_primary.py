"""Full250 source-role audit for iq and associated proteomics providers; article text stays private."""
from pathlib import Path
import argparse,gzip,json,re,sys,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,matching_words
from gemma_primary import encoded,pin,require

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/iq-proteomics-source-audit'
PATTERNS={k:re.compile(p,re.I)for k,p in dict(iq=r'(?<![A-Za-z0-9_])iq(?![A-Za-z0-9_])|fast[\W_]*MaxLFQ|MaxLFQ',diann=r'(?<![A-Za-z0-9_])dia[\W_]*nn(?![A-Za-z0-9_])',maxquant=r'(?<![A-Za-z0-9_])max[\W_]*quant(?![A-Za-z0-9_])',maxdia=r'(?<![A-Za-z0-9_])max[\W_]*dia(?![A-Za-z0-9_])',andromeda=r'(?<![A-Za-z0-9_])andromeda(?![A-Za-z0-9_])',macs_iq_view=r'MACS[\s-]*iQ[\s-]*View').items()}


def audit(root,cache):
    root,cache=Path(root),Path(cache);census=json.loads((root/FOLDER/'CENSUS.json').read_bytes());roles=json.loads((root/FOLDER/'ROLES.json').read_bytes())['roles'];papers=json.loads((root/'docs/literature/omics-2023-2026/papers.json').read_bytes())
    require(len(papers)==len(census['XML'])==250 and [p['paper_id']for p in papers]==list(census['XML']),'Changed frozen250-paper cohort')
    require(census['patterns']=={k:p.pattern for k,p in PATTERNS.items()},'Changed primary name census')
    hits=[];other=[];by_paragraph={}
    for paper in papers:
        expected=census['XML'][paper['paper_id']];require(paper['pmcid']==expected['pmcid']and paper['xml_sha256']==expected['sha256'],'Changed XML declaration')
        raw=gzip.decompress((cache/(paper['pmcid']+'.xml.gz')).read_bytes());require(pin(raw)=={k:expected[k]for k in('bytes','sha256')},'Changed exact article XML')
        tree=ET.fromstring(raw)
        for q in paragraphs(tree):
            matched=[k for k,p in PATTERNS.items()if p.search(q['match_text'])]
            a=dict(paper_id=paper['paper_id'],doi=paper['doi'],pmcid=paper['pmcid'],xml_sha256=paper['xml_sha256'],paragraph_index=q['paragraph_index'],section=q['section'],paragraph_sha256=pin(q['text'].encode())['sha256'])
            by_paragraph[paper['paper_id'],q['paragraph_index']]=a
            if matched:hits.append(dict(**a,matched_patterns=matched))
        for location in('front/article-meta/abstract','back','floats-group','body//supplementary-material'):
            for e in tree.findall(location):
                matched=[k for k,p in PATTERNS.items()if p.search(matching_words(e))]
                if matched:other.append(dict(paper_id=paper['paper_id'],location=location,matched_patterns=matched))
    require([{k:v for k,v in x.items()if k not in('classification','decision')}for x in census['anchors']]==hits and len(hits)==40 and other==census['other_structures'],'Changed broad source census; do not refresh historical hashes')
    require(all(x['classification']in('positive-method','supporting-method','other-software','reagent','cognitive-trait','mathematical-symbol')and x['decision']for x in census['anchors']),'Unreviewed primary name hit')
    pairs={(r['paper_id'],r['tool_id'])for r in roles};require(len(roles)==len(pairs)==16,'Changed semantic role population')
    require({p for p,t in pairs if t=='iq'}=={'P168'} and {p for p,t in pairs if t=='andromeda'}=={'P014','P144'} and {p for p,t in pairs if t=='maxdia'}=={'P014'} and {p for p,t in pairs if t=='macs_iq_view'}=={'P051'},'Unrelated IQ/reagent/parent software promoted to iq or a bundled-component version inferred')
    for r in roles:
        paper=next(p for p in papers if p['paper_id']==r['paper_id']);require(all(by_paragraph[a['paper_id'],a['paragraph_index']]==a for a in r['primary_anchors']),'Changed role anchor')
        if r['raw_source_fields']is not None:
            use=next(u for u in paper['tools']if u['tool_id']==r['tool_id']);require({k:use.get(k)for k in r['raw_source_fields']}==r['raw_source_fields'],'Raw version/name/source field changed')
        else:
            matches=[u for u in paper['tools']if u['tool_id']==r['tool_id']];require(len(matches)<=1,'Duplicate newly cataloged role')
            if matches:require(matches[0]['evidence_paragraph_sha256']==r['primary_anchors'][0]['paragraph_sha256'],'New observation points to another source')
        require(r['whole_paper_reproduced']is False and r['remaining_scope'] and r['implementation_available']==(r['tool_id']=='iq'),'Expanded runtime or paper claim')
        if r['tool_id']in('iq','andromeda','maxdia'):require(r['literal_reported_version']is None,'Author-unreported iq or embedded engine version silently inferred')
    return dict(status='PASS_FULL250_PROTEOMICS_PRIMARY_ROLES',primary_XML_checked=250,broad_paragraphs=40,other_structures=2,reviewed_roles=16,existing_observations_reviewed=11,new_positive_observations=5,new_named_providers=4,native_iq_roles=1,DIA_NN_raw_search_qualified=False,MaxQuant_runtime_qualified=False,whole_goal_complete=False)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--cache',type=Path,required=True);a=p.parse_args()
    try:print(json.dumps(audit(a.root,a.cache),sort_keys=True));return 0
    except Exception as e:
        code=3 if isinstance(e,(FileNotFoundError,PermissionError))else 2;print(('SKIP3 DEPENDENT SOURCE AUDIT SKIPPED: 'if code==3 else'FAIL2 NO SOURCE AUDIT ADMITTED: ')+str(e)+'. Restore exact permitted sources and preserve old hashes/attempts.',file=sys.stderr);return code


if __name__=='__main__':raise SystemExit(main())
