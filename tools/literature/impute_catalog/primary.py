"""Reconcile the full 250-paper cohort with versioned IMPUTE declarations and named methods."""
from pathlib import Path
import copy,gzip,hashlib,json,re,unicodedata,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,words,matching_words
from gsea_catalog.primary import EXTRACTOR_SHA

ROOT=Path(__file__).resolve().parents[3]
POSITIVE=['P076','P084','P116','P136','P140','P149','P180']
FOLDER='docs/literature/impute-catalog-sources'
PATTERN=re.compile(r'(?<![A-Za-z0-9_])IMPUTE[\s_-]*(?:(?:version|v)\.?\s*)?[245](?![A-Za-z0-9_])',re.I)
BARE=re.compile(r'(?<![A-Za-z0-9_])IMPUTE(?![A-Za-z0-9_])',re.I)

def require(value,message):
    if not value:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def census(cache,papers_raw):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Changed primary extractor')
    papers=json.loads(papers_raw);require(len(papers)==250,'Changed frozen cohort');identities=[];anchors=[];structures=[]
    for paper in papers:
        with gzip.open(Path(cache)/(paper['pmcid']+'.xml.gz'),'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==paper['xml_sha256'],'Changed XML: '+paper['paper_id'])
        tree=ET.fromstring(raw);dois=[x.text for x in tree.findall('front/article-meta/article-id') if x.get('pub-id-type')=='doi']
        require(len(dois)==1 and dois[0].lower()==paper['doi'].lower(),'Changed DOI identity')
        identities.append({k:paper[k] for k in ('paper_id','pmcid','doi','xml_sha256')});standard=paragraphs(tree);seen={r['text'] for r in standard}
        for row in standard:
            normalized=unicodedata.normalize('NFKC',row.get('match_text',row['text']));strict=list(PATTERN.finditer(normalized));bare=list(BARE.finditer(normalized))
            if strict or bare:anchors.append(dict(paper_id=paper['paper_id'],paragraph_index=row['paragraph_index'],section=row['section'],paragraph_sha256=pin(row['text'].encode())['sha256'],versioned_name_matches=sorted(set(m.group(0) for m in strict)),bare_name_matches=sorted(set(m.group(0) for m in bare))))
        for tag in ('p','table-wrap','code','preformat'):
            for ordinal,element in enumerate(tree.findall('.//body//'+tag),1):
                display=words(element)
                if (tag!='p' or display not in seen) and PATTERN.search(matching_words(element)):structures.append(dict(paper_id=paper['paper_id'],tag=tag,ordinal=ordinal,element_id=element.get('id'),text_sha256=pin(display.encode())['sha256']))
    return dict(status='ALL250_IMPUTE_NAME_CENSUS_NOT_SEMANTIC_ADMISSION',papers_checked=250,identities=identities,anchors=anchors,structures=structures,canonical_modified=False)

def frozen():
    result=json.loads((ROOT/FOLDER/'ROLES.json').read_bytes())
    require(result['status']=='SEVEN_IMPUTE_SOURCE_ROLES_ONE_SOFTWARE_DECLARATION_ADDITION_PRE_ADMISSION','Changed role draft kind')
    require([r['paper_id'] for r in result['roles']]==POSITIVE and not any(r['source_role_review_applied'] for r in result['roles']),'Changed draft roles')
    require(result['new_family_credit']==result['new_reviews']==0 and not result['canonical_modified'],'Draft falsely admitted')
    require([r['paper_id'] for r in result['roles'] if r['original_observation'] is None]==['P116'],'Changed missed-declaration correction')
    return result

def audit(cache,papers_raw):
    measured=census(cache,papers_raw);require(measured==json.loads((ROOT/FOLDER/'CENSUS.json').read_bytes()),'Changed all250 source census')
    roles=frozen();papers=json.loads(papers_raw)
    require(sorted({a['paper_id'] for a in measured['anchors'] if a['versioned_name_matches']})==POSITIVE,'Unreviewed named software candidate')
    for role in roles['roles']:
        paper=next(p for p in papers if p['paper_id']==role['paper_id']);uses=[u for u in paper['tools'] if u['tool_id']=='impute']
        require(uses==([] if role['original_observation'] is None else [role['original_observation']]),'Changed original observation')
        require(role['xml_sha256']==paper['xml_sha256'] and role['doi']==paper['doi'] and role['pmcid']==paper['pmcid'],'Changed role source identity')
        require([a for a in measured['anchors'] if a['paper_id']==role['paper_id'] and a['versioned_name_matches']]==role['anchors'],'Changed named source anchors')
        with gzip.open(Path(cache)/(paper['pmcid']+'.xml.gz'),'rb') as stream:source={r['paragraph_index']:r for r in paragraphs(ET.fromstring(stream.read()))}
        for context in role['reported_version_contexts']:
            anchor=next((a for a in role['anchors'] if a['paragraph_index']==context['paragraph_index']),None)
            require(anchor is not None and context['paragraph_sha256']==anchor['paragraph_sha256'],'Unbound version context')
            literal=context['reported_literal'];require(literal in source[context['paragraph_index']]['text'],'Version literal absent from exact primary paragraph')
            full=re.search(r'v\.?\s*(2\.[0-9]+\.[0-9]+)',literal)
            major=re.search(r'IMPUTE\s*([245])',literal,re.I)
            edition='4.1.2_r300.3' if literal=='IMPUTE4.1.2_r300.3' else full.group(1) if full else major.group(1) if major else None
            require(edition is not None and context['tool_edition']==edition,'Version interpreted differently from literal source')
    exclusions=json.loads((ROOT/FOLDER/'EXCLUSIONS.json').read_bytes())
    require([r['anchor'] for r in exclusions['rows']]==[a for a in measured['anchors'] if not a['versioned_name_matches']],'Unreviewed unversioned candidates')
    require(all(r['decision']=='not-an-additional-IMPUTE-software-declaration' and r['reason'] for r in exclusions['rows']),'Changed exclusion interpretation')
    return copy.deepcopy(roles),dict(primary_XML_checked=250,named_paragraphs_checked=sum(bool(a['versioned_name_matches']) for a in measured['anchors']),nonpositive_or_resource_contexts_checked=len(exclusions['rows']),new_missed_software_declaration='P116',original_scientific_analysis_reexecuted=False)
