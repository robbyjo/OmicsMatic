# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit every fixed-cohort primary XML for Signac and retain neighboring unassigned method context."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from catalog_fixed_cohort import verify_fixed_cohort
from collect_omics_review import paragraphs

ROOT=Path(__file__).resolve().parents[2]
def sha(raw):return hashlib.sha256(raw).hexdigest()

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to((ROOT/'build/literature-review/signac-runtime').resolve()) and not out.exists();out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers)
    named=re.compile(r'(?<![A-Za-z0-9_])Signac(?![A-Za-z0-9_])',re.I)
    method=re.compile(r'RunTFIDF|RunSVD|FindTopFeatures|TSSEnrichment|NucleosomeSignal|GeneActivity|LinkPeaks|RegionStats|FeatureMatrix|CreateChromatinAssay|Footprint|FindMotifs|AddMotifs|chromVAR|TF.IDF|latent semantic|nucleosome|TSS enrichment',re.I)
    records=[];census=[]
    for paper in papers:
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert sha(raw)==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw));found=[p for p in ps if named.search(p['match_text'])];selected=[t for t in paper['tools'] if t['tool_id']=='signac'];census.append(dict(paper_id=paper['paper_id'],xml_sha256=paper['xml_sha256'],named_paragraphs=len(found),catalogued=bool(selected)))
        assert not selected or found,paper['paper_id']
        if not found:continue
        assert len(selected)<=1
        if selected:
            anchor=next(p for p in found if p['paragraph_index']==selected[0]['evidence_paragraph_index']);assert sha(anchor['text'].encode())==selected[0]['evidence_paragraph_sha256']
        decorate=lambda p:dict(p,sha256=sha(p['text'].encode()))
        neighbors=[p for p in ps if p not in found and any(abs(p['paragraph_index']-q['paragraph_index'])<=2 for q in found)]
        methods=[p for p in ps if p not in found and p not in neighbors and method.search(p['match_text'])]
        classification='catalogued-software-role-pending-review' if selected else 'institutional-name-collision-SignAC-not-Signac-software' if paper['paper_id']=='P007' else 'uncatalogued-reported-software-use-requires-source-curation'
        records.append(dict(paper_id=paper['paper_id'],pmcid=paper['pmcid'],doi=paper['doi'],xml_sha256=paper['xml_sha256'],classification=classification,selected=selected[0] if selected else None,matches=list(map(decorate,found)),neighboring_context=list(map(decorate,neighbors)),other_method_candidates_not_automatically_attributed=list(map(decorate,methods))))
    assert {r['paper_id'] for r in records}=={'P007','P016','P039','P051','P094','P099','P134','P153','P158','P187','P225'}
    result=dict(status='audited-all250-primary-XMLs-for-Signac-with-unassigned-neighbor-context',papers_scanned=250,source_papers=len(records),catalogued_software_papers=9,new_software_papers_pending_curation=1,nonsoftware_name_collisions=1,named_paragraphs=sum(len(r['matches']) for r in records),records=records,census=census,implementation_credit=False,whole_paper_reproduced=False)
    (out/'PRIMARY-USES.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8',newline='\n');print(json.dumps({k:result[k] for k in ['status','papers_scanned','source_papers','named_paragraphs']}))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete primary-source audits')
    main()
