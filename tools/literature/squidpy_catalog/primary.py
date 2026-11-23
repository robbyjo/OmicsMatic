"""Full250 Squidpy census and original-extraction binding; no canonical writes."""
from pathlib import Path
import gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,words
from gsea_catalog.primary import EXTRACTOR_SHA

ROOT=Path(__file__).resolve().parents[3]
POSITIVE=['P008','P009','P010','P024','P094','P161']
NAMED=re.compile(r'(?<![A-Za-z0-9_])squidpy(?![A-Za-z0-9_])',re.I)
SPECIFIC=re.compile(r'\bsq\.gr\.(?:spatial_neighbors|spatial_autocorr|ligrec)\b|\bsq\.read\.nanostring\b',re.I)
ROLES={
 'P008':dict(summary='Used Squidpy in per-library analysis of Visium spot-expression matrices. The exact Squidpy functions and edition are not reported.',
             observed_operations=['unspecified_spatial_expression_analysis'],reported_squidpy=None,reported_python=None,
             scope_limit='Current graph/statistic/reader interfaces are available scoped capabilities; do not infer their invocation here. Space Ranger preprocessing is a separate tool.'),
 'P009':dict(summary='Built per-sample centroid Delaunay graphs with Squidpy spatial_neighbors. Broader software provenance reports Squidpy1.1.2 and Python3.8.13.',
             observed_operations=['delaunay_spatial_neighbors'],reported_squidpy='1.1.2',reported_python='3.8.13',
             scope_limit='Preserve the original near-name unknown-edition anchor and add the broader paragraph separately. Custom200-micrometre pruning, GNN/subgraphs and other downstream steps are separate. The compatible3.8.20 validation patch is not the reported3.8.13 full environment.'),
 'P010':dict(summary='Used Squidpy for CellPhoneDB/OmniPath receptor-ligand inference across time points; temporal correlation was a separate downstream calculation.',
             observed_operations=['ligand_receptor_inference'],reported_squidpy=None,reported_python=None,
             scope_limit='Exact caller, interaction snapshot, permutations and other settings are unreported. The adapter requires explicit locally captured pairs/provenance; it does not silently fetch a changing database or implement the downstream temporal Spearman analysis.'),
 'P024':dict(summary='Built spatial neighborhoods using30 nearest neighbors per nucleus and sample before manual composition/image-based layer annotation.',
             observed_operations=['knn_spatial_neighbors'],reported_squidpy=None,reported_python=None,
             scope_limit='Keep per-sample boundaries explicit. Original1.1.2 lacks library_key and therefore requires separately supplied per-sample inputs. Manual neighborhood composition and image/pseudolayer annotation are separate methods.'),
 'P094':dict(summary='Used Squidpy1.2.2 Moran statistics on spatial and expression graphs before a custom spatial-versus-expression ranking.',
             observed_operations=['moran_spatial_and_expression_graph_statistics'],reported_squidpy='1.2.2',reported_python=None,
             scope_limit='Supply the intended weighted graphs and settings. Custom I_spatial minus sqrt(I_expression) ranking and subsequent module construction are not the upstream autocorrelation call; undefined values and unreported normalization/options remain explicit.'),
 'P161':dict(summary='Used Squidpy in CosMx/AtoMx conversion and construction of25/50/75/100-micrometre spatial graphs.',
             observed_operations=['radius_spatial_neighbors','input_conversion_unspecified_entrypoint'],reported_squidpy=None,reported_python=None,
             scope_limit='The historical reader/caller and edition are unreported. read.nanostring is a source-verified available1.2.2/1.8.4 reader, not an established historical invocation. Radius units depend on supplied coordinates; scale/FOV/sample partitioning must be explicit.'),
}


def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def require(value,message):
    if not value:raise ValueError(message)


def audit(cache):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Frozen primary extractor changed')
    papers=json.loads((ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes());require(len(papers)==250,'Changed250-paper population')
    prior=json.loads((ROOT/'docs/literature/squidpy-workflow-development/SOURCE-AUDIT.json').read_bytes())
    expected={r['paper_id']:r for r in prior['candidate_papers']};require(sorted(expected)==POSITIVE,'Changed prior source population')
    candidates=[];identities=[];tables=[]
    for paper in papers:
        path=Path(cache)/(paper['pmcid']+'.xml.gz');require(path.stat().st_size<=64*1024*1024,'Compressed primary capacity')
        with gzip.open(path,'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==paper['xml_sha256'],'Changed primary XML '+paper['paper_id'])
        tree=ET.fromstring(raw);doi=[n.text for n in tree.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi']
        require(len(doi)==1 and doi[0].lower()==paper['doi'].lower(),'Changed primary DOI')
        identity={k:paper[k] for k in ('paper_id','pmcid','doi','xml_sha256')};identities.append(identity);anchors=[];texts={}
        for paragraph in paragraphs(tree):
            text=paragraph.get('match_text',paragraph['text'])
            if NAMED.search(text) or SPECIFIC.search(text):
                anchor=dict(paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=pin(paragraph['text'].encode())['sha256'])
                anchors.append(anchor);texts[paragraph['paragraph_index']]=paragraph['text']
        if anchors:
            paper_id=paper['paper_id'];require(paper_id in expected,'New candidate needs source review '+paper_id)
            require(anchors==expected[paper_id]['anchors'],'Changed complete source anchors '+paper_id)
            uses=[u for u in paper['tools'] if u['tool_id']=='squidpy'];require(len(uses)==1,'Changed extracted Squidpy use')
            observation=uses[0];anchor=next(a for a in anchors if a['paragraph_index']==observation['evidence_paragraph_index'])
            require(anchor['paragraph_sha256']==observation['evidence_paragraph_sha256'],'Changed original extraction witness')
            reported=[]
            if paper_id=='P009':
                require(re.search(r'squidpy\s*\(\s*1\.1\.2\s*\)',texts[118],re.I) is not None and re.search(r'Python\s*\(\s*3\.8\.13\s*\)',texts[118],re.I) is not None,'Changed broader P009 edition witness')
                reported=[dict(paragraph_index=118,paragraph_sha256=pin(texts[118].encode())['sha256'],reported_squidpy='1.1.2',reported_python='3.8.13',original_near_name_field_preserved=observation['version_reported_near_name'])]
            candidates.append(dict(**identity,anchors=anchors,original_observation=observation,**ROLES[paper_id],broader_version_evidence=reported,
                                   whole_paper_reproduced=False,original_author_environment_reproduced=False))
        for table in tree.iter('table-wrap'):
            text=words(table)
            if NAMED.search(text) or SPECIFIC.search(text):tables.append(dict(paper_id=paper['paper_id'],table_id=table.get('id'),sha256=pin(text.encode())['sha256']))
    require([r['paper_id'] for r in candidates]==POSITIVE and not tables,'Changed Squidpy candidate/table census')
    return dict(schema_version=1,status='PASS_FULL250_SQUIDPY_SOURCE_ROLES_WITH_EXPLICIT_UNREPORTED_CALLERS',papers_checked=250,
        identities=identities,roles=candidates,table_candidates=tables,source_primary_text_redistributed=False,
        canonical_catalog_modified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)
