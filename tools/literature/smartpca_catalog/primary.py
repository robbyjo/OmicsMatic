# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit frozen all-250 primary bytes and retain six smartpca source roles; no admission."""
from pathlib import Path
import argparse,gzip,hashlib,json,re,unicodedata,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,words,matching_words
from gsea_catalog.primary import EXTRACTOR_SHA

ROOT=Path(__file__).resolve().parents[3]
NAMED=re.compile(r'(?<![A-Za-z0-9_])(?:smartpca|eigensoft)(?![A-Za-z0-9_])',re.I)
ANCHORS={'P019':[59,67],'P041':[55],'P045':[36],'P071':[22],'P180':[12,20],'P229':[11]}
ROLES={
 'P019':dict(summary='Computed MVP genetic principal components with reported EIGENSOFT v6; separately projected ROSMAP participants onto 1000 Genomes reference eigenvectors using smartpca for ancestry validation.',
  roles=[dict(context='MVP genetic principal components',reported_package_edition='6',reported_program_version=None),
         dict(context='ROSMAP reference-panel projection',reported_package_edition=None,reported_program_version=None)],
  reported_settings={},boundary='Do not assign the MVP v6 edition to the separate ROSMAP call. QC, genotype imputation, ancestry harmonization, quadratic discriminant assignment and GReX imputation are separate. Projection mode and complete caller settings remain unreported.'),
 'P041':dict(summary='Used PCA with the lsqproject feature in the reported EIGENSTRAT/EIGENSOFT 7.2.1 package to validate ancestry-masked genotypes alongside F4 ratios and ADMIXTURE.',
  roles=[dict(context='PCA reference projection during mask validation',reported_package_edition='7.2.1',reported_program_version=None)],
  reported_settings=dict(lsqproject=True),boundary='RFMIX local ancestry, construction of masks, F4 ratios, ADMIXTURE and the greater-than-95-percent masking exclusion are separate. Do not reinterpret the EIGENSTRAT package mention as its case-control association command.'),
 'P045':dict(summary='Ran unprojected and least-squares-projected smartpca analyses of pseudohaploidized data with reported shrinkmode YES and r2thresh 0.7.',
  roles=[dict(context='Unprojected and projected population PCA',reported_package_edition=None,reported_program_version=None)],
  reported_settings=dict(lsqproject=True,shrinkmode=True,r2thresh=.7),boundary='The edition, killr2 switch, complete LD rule and outlier settings are unreported. Do not infer that setting r2thresh alone enabled the source LD filter. The separately reported PLINK PCoA, ADMIXTURE and f-statistics remain distinct.'),
 'P071':dict(summary='Built PCA axes from selected present-day Human Origins populations and projected ancient individuals using lsqproject YES, numoutlieriter 0 and shrinkmode YES.',
  roles=[dict(context='Present-day-reference PCA and ancient-sample projection',reported_package_edition=None,reported_program_version=None)],
  reported_settings=dict(lsqproject=True,numoutlieriter=0,shrinkmode=True),boundary='Preserve separate Eurasian and East Asian reference populations. The 82-population starting panel, later ADMIXTURE population subset, PLINK LD pruning and model selection are separate; the exact smartpca edition is unreported.'),
 'P180':dict(summary='Used smartpca from reported EIGENSOFT 7.2.0 for modern-reference PCA with ancient samples projected by least squares and outlier removal off; separately computed pairwise FST with fstonly YES.',
  roles=[dict(context='Modern/ancient PCA projection',reported_package_edition='7.2.0',reported_program_version=None),
         dict(context='Pairwise population FST',reported_package_edition=None,reported_program_version=None,
              edition_context='The PCA paragraph names EIGENSOFT7.2.0; the separate FST paragraph does not restate an edition.')],
  reported_settings=dict(lsqproject=True,outlier_removal=False,fstonly=True,minimum_SNP_cutoff=100000),
  boundary='PCA and fstonly are separate calls, not simultaneous flags. Preserve the study SNP-coverage cutoff as preprocessing. A validated 7.2.1 selection does not establish 7.2.0 equivalence, original FST defaults, uncertainty or whole-study reproduction.'),
 'P229':dict(summary='Computed ancestry principal components with the literal reported SmartPCA v.13050 after explicit merging, allele/position matching, LD pruning and PCA-specific frequency selection.',
  roles=[dict(context='Ancestry PCA',reported_package_edition=None,reported_program_version='13050')],
  reported_settings=dict(PCA_MAF_greater_than=.05),boundary='13050 is a literal program version, not an inferred EIGENSOFT package release. The selected 7.2.1 binary reports 16000 and is not that historical executable. HGDP/1000G resource selection, merging, missingness restrictions and ADMIXTURE/K selection remain separate.')}

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def _source_audit(cache,papers_raw=None):
    assert pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA
    papers_raw=(ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes() if papers_raw is None else papers_raw;papers=json.loads(papers_raw);assert len(papers)==250
    identities=[];found={};roles=[];extra=[]
    for paper in papers:
        path=Path(cache)/(paper['pmcid']+'.xml.gz')
        with gzip.open(path,'rb') as stream:raw=stream.read(64*1024*1024+1)
        assert len(raw)<=64*1024*1024 and pin(raw)['sha256']==paper['xml_sha256'],paper['paper_id']
        tree=ET.fromstring(raw);dois=[x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi']
        assert len(dois)==1 and dois[0].lower()==paper['doi'].lower(),paper['paper_id']
        identity={k:paper[k] for k in ('paper_id','doi','pmcid','xml_sha256')};identities.append(identity)
        standard=paragraphs(tree);anchors=[]
        def matches(text):return NAMED.search(unicodedata.normalize('NFKC',text)) is not None
        for paragraph in standard:
            if matches(paragraph.get('match_text',paragraph['text'])):
                anchors.append(dict(paragraph_index=paragraph['paragraph_index'],section=paragraph['section'],paragraph_sha256=pin(paragraph['text'].encode())['sha256']))
        if anchors:found[paper['paper_id']]=anchors
        seen={row['text'] for row in standard}
        for tag in ('p','table-wrap','code','preformat'):
            for ordinal,element in enumerate(tree.findall('.//body//'+tag),1):
                display=words(element)
                if (tag!='p' or display not in seen) and matches(matching_words(element)):
                    extra.append(dict(paper_id=paper['paper_id'],tag=tag,ordinal=ordinal,element_id=element.get('id'),text_sha256=pin(display.encode())['sha256']))
        if paper['paper_id'] in ROLES:
            uses=[use for use in paper['tools'] if use['tool_id']=='smartpca'];assert len(uses)==1
            use=uses[0];binding=next(row for row in anchors if row['paragraph_index']==use['evidence_paragraph_index'])
            assert binding['paragraph_sha256']==use['evidence_paragraph_sha256']
            roles.append(dict(**identity,**ROLES[paper['paper_id']],anchors=anchors,
                original_observation_identity=pin(json.dumps(use,sort_keys=True,separators=(',',':')).encode()),
                original_extraction_version_reported_near_name=use['version_reported_near_name'],
                source_role_review_applied=False,original_author_environment_reproduced=False,whole_paper_reproduced=False))
    assert {key:[row['paragraph_index'] for row in value] for key,value in found.items()}==ANCHORS,'Changed full-cohort smartpca census'
    assert not extra,'New body/table/code candidate requires individual source review'
    return dict(status='PASS_FULL250_SMARTPCA_SIX_SOURCE_ROLES_NOT_CATALOG_ADMISSION',papers_checked=250,source_papers_identity=pin(papers_raw),
        extractor_sha256=EXTRACTOR_SHA,roles=roles,primary_identities=identities,additional_body_table_code_candidates=extra,
        new_reviews=0,new_family_credit=0,whole_goal_complete=False,full_primary_texts_redistributed=False)
POSITIVE=list(ANCHORS)

def require(condition,message):
    if not condition:raise ValueError(message)

def audit(cache,papers_raw=None):
    papers_raw=(ROOT/'docs/literature/omics-2023-2026/papers.json').read_bytes() if papers_raw is None else papers_raw
    result=_source_audit(cache,papers_raw)
    draft=json.loads((ROOT/'docs/literature/smartpca-native-development/ROLES.json').read_bytes())
    require(result==draft,'Changed draft census/source identities or original observations')
    papers=json.loads(papers_raw)
    for role in result['roles']:
        use=next(u for paper in papers if paper['paper_id']==role['paper_id'] for u in paper['tools'] if u['tool_id']=='smartpca')
        role['original_observation']=use
        require(pin(json.dumps(use,sort_keys=True,separators=(',',':')).encode())==role['original_observation_identity'],'Changed original observation')
        role['source_table_evidence']=[]
        role['scope_limit']=role['boundary']
        if role['paper_id']=='P180':
            role['reported_settings']={'PCA':dict(lsqproject=True,outlier_removal=False,minimum_SNP_cutoff=100000),'FST':dict(fstonly=True)}
            role['scope_limit']+=' The100,000-SNP cutoff belongs to PCA; its application to FST is not established. No edition/default/uncertainty is inferred for the FST call.'
    result['status']='PASS_FULL250_SMARTPCA_SIX_SOURCE_ROLES_CONTEXT_VERSIONS_NOT_ADMISSION'
    return result
