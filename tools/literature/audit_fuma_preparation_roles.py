"""Recheck fourteen retained FUMA sources and distinguish implemented preparation from paper ambiguity."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,validate_supporting_evidence

ROOT=Path(__file__).resolve().parents[2]
METHODS={
 'P019':['MAGMA gene fitting','known-association union','Fisher enrichment'],
 'P072':['locus definition','variant annotation','MAGMA gene/set/property fitting','GENE2FUNC'],
 'P088':['locus definition','variant annotation','MAGMA fitting','GENE2FUNC gene-set tests'],
 'P105':['MAGMA gene/set/property fitting','gene-window annotation'],
 'P107':['MAGMA gene/set fitting','gene-window annotation'],
 'P123':['locus definition','locus novelty'],
 'P130':['transcript-site mapping','MAGMA gene/set fitting'],
 'P131':['locus definition','MAGMA gene fitting','gene-window annotation'],
 'P135':['variant consequence/score annotation','eQTL mapping','mapped-gene enrichment'],
 'P140':['locus definition'],
 'P149':['GENE2FUNC gene-set overrepresentation'],
 'P150':['locus definition','positional/eQTL/chromatin mapping','MAGMA gene fitting'],
 'P162':['MAGMA gene/set/property fitting','independent variant selection'],
 'P243':['broad functional/expression/splicing interpretation; exact FUMA module unstated'],
}


def sha(raw):return hashlib.sha256(raw).hexdigest()
def pin(path):raw=path.read_bytes();return dict(bytes=len(raw),sha256=sha(raw))


def main():
    if not __debug__:raise RuntimeError('Never disable source evidence assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to((ROOT/'build/literature-review/fuma-runtime').resolve()) and not out.exists()
    data=ROOT/'docs/literature/omics-2023-2026';papers={p['paper_id']:p for p in json.loads((data/'papers.json').read_text(encoding='utf-8'))};rows=json.loads((ROOT/'tools/literature/fuma-use-reviews.json').read_text(encoding='utf-8'));reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_text(encoding='utf-8'))['reviews'];registry={t['id']:t for t in json.loads((ROOT/'tools/literature/tool-registry.json').read_text(encoding='utf-8'))};matrix=json.loads(args.matrix.read_text(encoding='utf-8'))
    assert matrix['status']=='passed-three-JVM-packaged-FUMA-expression-preparation' and matrix['tests_per_JVM']==148
    assert all(pin(ROOT/n)==v for n,v in matrix['source_pins'].items())
    assert {r['paper_id'] for r in rows}==set(METHODS)=={p['paper_id'] for p in papers.values() if any(t['tool_id']=='fuma' for t in p['tools'])} and len(rows)==14
    evidence=[]
    for row in rows:
        paper=papers[row['paper_id']];review=row['review'];assert review in reviews
        raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes());assert sha(raw)==paper['xml_sha256'];ps=paragraphs(ET.fromstring(raw));primary=ps[row['primary_paragraph']-1];assert sha(primary['text'].encode())==review['evidence_paragraph_sha256'];validate_supporting_evidence(ps,[review],registry)
        current=next(t for t in paper['tools'] if t['tool_id']=='fuma')
        for key in ['use_summary','implementation_scope_note','version_reported_near_name']:assert current[key]==review[key]
        expression=row['paper_id'] in {'P072','P105','P162'}
        evidence.append(dict(paper_id=row['paper_id'],doi=paper['doi'],pmcid=paper['pmcid'],primary_paragraph=row['primary_paragraph'],source_XML_sha256=paper['xml_sha256'],primary_paragraph_sha256=review['evidence_paragraph_sha256'],reported_FUMA_release=review['version_reported_near_name'],reviewed_role=row['observed_role'],reported_method_groups=METHODS[row['paper_id']],new_expression_preparation_relevance='Constructs tissue-expression covariates consumed by the documented MAGMA gene-property method. Exact paper preprocessing/resource identity remains a separate declaration.' if expression else 'No additional sample-level expression preparation run is established by this reviewed source role.',source_uncertainty=review['implementation_scope_note'],historical_paper_reproduction_required_for_tool_availability=False))
    summary=json.loads((data/'summary.json').read_text(encoding='utf-8'));assert summary['papers']==250 and summary['implementation_status_counts']['missing']==258
    inventory=json.loads((data/'tool-inventory.json').read_text(encoding='utf-8'));fuma=next(t for t in inventory if t['id']=='fuma');assert fuma['implementation_status']=='missing'
    source_files=[ROOT/'tools/literature/fuma-use-reviews.json',ROOT/'tools/literature/reviewed-uses.json',ROOT/'tools/literature/tool-registry.json',data/'papers.json',data/'tool-inventory.json',data/'summary.json',Path(__file__),args.matrix.resolve()]
    out.mkdir();report=dict(status='passed-fourteen-retained-FUMA-source-role-review',roles=14,new_preparation_relevant_papers=['P072','P105','P162'],evidence=evidence,source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in source_files},candidate=matrix['artifact'],catalog_unchanged=True,tool_availability_does_not_require_historical_or_whole_paper_equivalence=True,remaining_functional_boundaries=['Native sample preparation does not generate DEG sets, perform t-tests or cluster heatmaps. Original FUMA runtime also consumes precomputed resources; do not invent an author-specific generation protocol from its use.','Supplied variant/eQTL/chromatin modules do not themselves acquire/predict those annotations. Resource acquisition/upstream adapters must be distinguished from the native postprocessing methods.','Current managed MAGMA profile is1.10; this does not prove reproduction of explicitly reported1.08 software. This historical version distinction is not itself a missing generic method.','P243 does not enumerate a FUMA module; no precise command/parameters can be attributed from that platform description.'],catalog_metadata_refresh_needed=['The FUMA scope still lists reference/LD construction as unfinished, despite the validated reference-preparation candidate.','The scope still lists expression transformation as unfinished, despite the now-validated sample-level transformation candidate.'],next_action='Refresh the catalog generator and truthful scoped availability/remaining-role map using current proven commands. Separate missing method work from normal user-supplied data, unspecified paper settings and unrequested historical/full-paper equivalence; do not use those as invented completion gates.')
    (out/'coverage.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(dict(status=report['status'],roles=14,new_preparation_relevant_papers=report['new_preparation_relevant_papers'],catalog_unchanged=True)))


if __name__=='__main__':main()
