# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare seven primary-source SoupX role reviews without changing the credited catalog."""
import argparse,gzip,hashlib,json
from pathlib import Path
import xml.etree.ElementTree as ET
from setup_deseq2_reference import ROOT,pin,save
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort

ROLES={
 'P008':(71,None,'TF-IDF marker ranking identified genes characteristic of each cluster.','The paragraph does not establish ambient RNA estimation or correction. The function, cutoff, N, FDR, normalization and SoupX version are not reported. Native marker settings must be explicit; do not reconstruct them from the package defaults.','cluster-marker-ranking',['inverse document frequency']),
 'P087':(59,None,'Removed ambient RNA using supplied Seurat clusters and manual human-liver annotations, with ALB as a hepatocyte-lineage marker; a separate mouse immune-cell analysis used Csf1r as an MP-lineage marker.','The human-liver SoupX version and functions are unspecified. Paragraph62 reports1.6.2 for the mouse immune-cell analysis; do not transfer that version to the human analysis. Preserve manual annotations and marker roles; automatic estimation does not reproduce unknown caller-supplied non-expression masks.','manual-marker-ambient-RNA-removal',['ALB']),
 'P104':(30,'1.6.2','Used SoupX1.6.2 as a secondary check that significant ambient RNA effects were absent, after a CellBender assessment.','A validation check is reported; the paragraph does not establish that SoupX-corrected counts were used. Functions, settings and thresholds for that conclusion are unspecified.','ambient-RNA-validation',['1.6.2']),
 'P145':(50,'1.6.2','Estimated ambient contamination with SoupX1.6.2 autoEstCont and corrected the count matrix with adjustCounts, using supplied raw matrices, filtered counts and clusters.','Reported autoEstCont overrides are tfidfMin=0.95, soupQuantile=0.6, priorRho=0.1 and contaminationRange=c(0.05,0.8). Other choices, exact source commit, pooling, clustering decisions and exclusion masks are not inferred. Downstream ambient covariate adjustment is a separate operation.','automatic-ambient-RNA-correction',['autoEstCont','adjustCounts']),
 'P182':(17,'1.3.0','Estimated and removed cell-free mRNA from Cell Ranger count matrices using SoupX1.3.0 before Seurat analysis.','No SoupX functions, markers or settings are stated. The recovered GitHub commit labeled1.3.0 is a source candidate, not proof of the paper commit or historical R and dependency environment. Scrublet doublet filtering is separate.','ambient-RNA-removal',['1.3.0']),
 'P225':(9,None,'Removed ambient RNA with SoupX during sample-wise single-cell preprocessing.','The SoupX version and functions are not reported. scds doublet assessment and later manual removal of an ambient-signature cluster are distinct steps and are not implemented by a SoupX correction call.','ambient-RNA-removal',['scds']),
 'P233':(8,'1.6.2','Removed ambient RNA from Cell Ranger count matrices with SoupX1.6.2 before Seurat preprocessing.','Functions and settings are unspecified. Subsequent cell/gene filters, cell annotation and red-blood-cell removal are separate. A selected modern runtime does not establish the paper source commit.','ambient-RNA-removal',['1.6.2'])}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/soupx-runtime') and not out.exists();out.mkdir();data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers);reviews=ROOT/'tools/literature/reviewed-uses.json';old=json.loads(reviews.read_bytes());assert len(old['reviews'])==918 and not any(r['tool_id']=='soupx' for r in old['reviews']);observed={p['paper_id'] for p in papers if any(t['tool_id']=='soupx' for t in p['tools'])};assert observed==set(ROLES);drafts=[]
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),reviews,data/'papers.json',data/'summary.json']}
    for paper in papers:
        if paper['paper_id'] not in ROLES:continue
        index,version,summary,note,context,needles=ROLES[paper['paper_id']];path=ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz');raw=gzip.decompress(path.read_bytes());assert hashlib.sha256(raw).hexdigest()==paper['xml_sha256'];root=ET.fromstring(raw);assert next(e.text for e in root.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi').lower()==paper['doi'].lower();ps=paragraphs(root);paragraph=ps[index-1];assert paragraph['paragraph_index']==index and 'soupx' in paragraph['match_text'].lower() and all(s.lower() in paragraph['match_text'].lower() for s in needles),(paper['paper_id'],index);digest=hashlib.sha256(paragraph['text'].encode('utf8')).hexdigest();support=[]
        for extra in {'P087':[62],'P145':[52]}.get(paper['paper_id'],[]):
            s=ps[extra-1];assert 'soupx' in s['match_text'].lower();support.append(dict(paragraph_index=extra,section=s['section'],paragraph_sha256=hashlib.sha256(s['text'].encode('utf8')).hexdigest()))
        review=dict(pmcid=paper['pmcid'],doi=paper['doi'],tool_id='soupx',evidence_paragraph_sha256=digest,use_summary=summary,implementation_scope_note=note,version_reported_near_name=version,usage_context=context,supporting_evidence=support);drafts.append(dict(paper_id=paper['paper_id'],primary_paragraph=index,section=paragraph['section'],review=review,fulltext_url=paper['fulltext_url'],xml_sha256=paper['xml_sha256']))
    save(out/'draft-reviews.json',drafts);assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='audited-seven-SoupX-primary-roles-drafts-not-applied',source_pins=pins,drafts=pin(out/'draft-reviews.json'),roles=7,prior_reviews_preserved=918,applied=False,implementation_credit=False,whole_goal_complete=False));print('Audited7 SoupX primary roles; catalog unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/role checks')
    main()
