# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add source-reviewed MALT family roles while preserving exact predecessor metadata."""
import gzip,json
import scdrs_catalog as prior
from setup_deseq2_reference import pin
from signac_scan_evidence import byte_pin
from catalog_source_integrity import verify_source
from run_mrmega_catalog_checks import ArchivedCatalogView
from malt_source_registry import IDS,extend


def verify_extension(root):
    data=root/'docs/literature/omics-2023-2026';proof=json.loads((data/'malt-source-role-coverage.json').read_bytes())
    assert proof['status']=='PASS_THREE_MALT_FAMILY_ADDITIONS_WITH_DISTINCT_PRIMARY_SOURCE_KINDS' and proof['applied']
    assert (proof['papers'],proof['prior_observations'],proof['observations'],proof['prior_reviews'],proof['reviews'])==(250,2476,2479,1049,1052)
    assert pin(data/'papers.json')==proof['current_papers'] and pin(root/'tools/literature/reviewed-uses.json')==proof['current_reviews']
    for name,value in proof['source_pins'].items():assert pin(root/name)==value,name
    for name,key in [('malt-prior-papers.json.gz','prior_papers_archive'),('malt-prior-reviews.json.gz','prior_reviews_archive'),('malt-applied-source-reviews.json','applied_reviews'),('malt-registry-source-review.json','registry_review')]:assert pin(data/name)==proof[key]
    oldpapers=json.loads(gzip.decompress((data/'malt-prior-papers.json.gz').read_bytes()));papers=json.loads((data/'papers.json').read_bytes())
    oldreviews=json.loads(gzip.decompress((data/'malt-prior-reviews.json.gz').read_bytes()))['reviews'];reviews=json.loads((root/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    assert len(oldreviews)==1049 and reviews[:1049]==oldreviews and len(reviews)==1052
    assert len({(r['pmcid'],r['tool_id']) for r in reviews})==1052 and {r['tool_id'] for r in reviews[1049:]}==IDS
    assert sum(len(r.get('supporting_evidence',[])) for r in reviews)==377
    for a,b in zip(oldpapers,papers,strict=True):
        assert {k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'}
        assert a['tools']==[t for t in b['tools'] if t['tool_id'] not in IDS]
        if b['paper_id']!='P002':assert a==b
        else:assert {t['tool_id'] for t in b['tools'] if t['tool_id'] in IDS}==IDS
    assert sum(len(p['tools']) for p in papers)==2479
    applied=json.loads((data/'malt-applied-source-reviews.json').read_bytes());assert [r['review'] for r in applied['updates']]==reviews[1049:]
    verify_source(root,data,'tools/literature/reviewed-uses.json',proof['reviews_before']['sha256'])
    return proof


def predecessor_view(root):
    data=root/'docs/literature/omics-2023-2026';overrides={}
    for path,archive in [('tools/literature/reviewed-uses.json','malt-prior-reviews.json.gz'),('docs/literature/omics-2023-2026/papers.json','malt-prior-papers.json.gz')]:overrides[path]=gzip.decompress((data/archive).read_bytes())
    ledger=json.loads((data/'malt-prior-metadata-view.json').read_bytes())
    for name,row in ledger['files'].items():
        archive=data/row['archive'];assert pin(archive)==row['archive_pin'];raw=gzip.decompress(archive.read_bytes());assert byte_pin(raw)==row['decoded_pin'];overrides['docs/literature/omics-2023-2026/'+name]=raw
    return ArchivedCatalogView(root,overrides)


def load_catalog_registry(root):
    verify_extension(root);rows=prior.load_catalog_registry(predecessor_view(root));assert len(rows)==440
    result=extend(rows);assert len(result)==443;return result


def _wrapped(name):
    def metadata(root,data,*args,**kwargs):
        old=predecessor_view(root);return getattr(prior,name)(old,old/'docs/literature/omics-2023-2026',*args,**kwargs)
    return metadata


__all__=['load_catalog_registry']
for _name in dir(prior):
    if _name.endswith('_metadata') and callable(getattr(prior,_name)):
        globals()[_name]=_wrapped(_name);__all__.append(_name)
# Match the two explicit aliases in the established renderer.
meme_discovery_metadata=_wrapped('current_meme_metadata')
chipseeker_metadata=_wrapped('current_chipseeker_metadata')
__all__+=['meme_discovery_metadata','chipseeker_metadata']
