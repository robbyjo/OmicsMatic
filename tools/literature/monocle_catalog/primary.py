"""Audit all250 frozen XML identities and nine existing Monocle roles without adding reviews."""
from pathlib import Path
import copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs
from gsea_catalog.primary import identities,EXTRACTOR_SHA
from monocle_catalog.decisions import ROLES

ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/monocle-catalog-sources'
PATTERN=re.compile(r'(?<![A-Za-z0-9_])(?:Monocle\s*[23]?)(?![A-Za-z0-9_])',re.I)

def require(ok,message):
    if not ok:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def pointer(block):
    return dict(paragraph_index=block['paragraph_index'],section=block['section'],paragraph_sha256=pin(block['text'].encode())['sha256'],matching_text_sha256=pin(block.get('match_text',block['text']).encode())['sha256'])

def edition(block,spec):
    expected=spec['reported_edition']
    if expected is None:return None
    text=block.get('match_text',block['text']);pattern=re.compile(r'(?i)(?<![A-Za-z0-9_])monocle\s*3(?:\s|[()vV.])*'+re.escape(expected)+r'(?![0-9])')
    matches=list(pattern.finditer(text));require(len(matches)==1,'Missing/ambiguous explicit Monocle package edition')
    match=matches[0];offset=match.start()+match.group().rfind(expected)
    return dict(literal=expected,paragraph_index=block['paragraph_index'],character_offset=offset,package_and_edition_witness=match.group())

def audit(cache):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Changed frozen XML paragraph extractor')
    sources=identities();candidates=[];blocks={}
    for identity in sources:
        path=Path(cache)/(identity['pmcid']+'.xml.gz');require(path.stat().st_size<=64*1024*1024,'Compressed primary source exceeds capacity')
        with gzip.open(path,'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==identity['xml_sha256'],'Primary XML identity differs')
        tree=ET.fromstring(raw);doi=[el.text for el in tree.findall('front/article-meta/article-id') if el.get('pub-id-type')=='doi'];require(len(doi)==1 and doi[0].lower()==identity['doi'].lower(),'Primary DOI differs')
        byindex={b['paragraph_index']:b for b in paragraphs(tree)};anchors=[]
        for block in byindex.values():
            text=block.get('match_text',block['text']);matches=list(PATTERN.finditer(text))
            if matches:anchors.append(dict(**pointer(block),matches=[dict(literal=m.group(),character_offset=m.start()) for m in matches]))
        if anchors:
            pid=identity['paper_id'];require(pid in ROLES,'Review new Monocle name candidate instead of dropping it');spec=ROLES[pid]
            candidates.append(dict(**identity,anchors=anchors,reviewed_paragraph_witnesses=[pointer(byindex[index]) for index in spec['paragraphs']],reported_edition_witness=edition(byindex[spec['paragraphs'][0]],spec)));blocks[pid]=byindex
    roles=[]
    for pid,spec in ROLES.items():
        identity=next(row for row in sources if row['paper_id']==pid);byindex=blocks[pid];anchor=byindex[spec['paragraphs'][0]];match=PATTERN.search(anchor.get('match_text',anchor['text']));require(match is not None,'Missing existing primary package witness')
        roles.append(dict(paper_id=pid,pmcid=identity['pmcid'],doi=identity['doi'],tool_id='monocle',source_xml_sha256=identity['xml_sha256'],analysis_role=spec['analysis_role'],usage_summary=spec['usage_summary'],implementation_scope_note=spec['scope'],binding=spec['binding'],qualified_component_commands=spec['commands'],primary_evidence=[pointer(byindex[index]) for index in spec['paragraphs']],source_name_witness=dict(literal=match.group(),character_offset=match.start(),paragraph_index=anchor['paragraph_index']),reported_edition=edition(anchor,spec),reported_edition_component_controls_available=spec['reported_edition'] is not None,paper_executed_commit_confirmed=False,author_pipeline_executed=False,whole_tool_qualified=False,whole_paper_reproduced=False))
    result=dict(schema_version=1,status='PASS_FRESH250_MONOCLE9_EXISTING_ROLE_SCOPE_REVIEW',source_identities=sources,pattern=PATTERN.pattern,candidates=candidates,roles=roles,papers_scanned=250,positive_papers=9,source_paragraph_pointers=15,new_observations=0,new_review_records=0,new_family_credit=0,canonical_metadata_modified=False,whole_goal_complete=False)
    validate(result);return result

def validate(result):
    require(result['schema_version']==1 and result['status']=='PASS_FRESH250_MONOCLE9_EXISTING_ROLE_SCOPE_REVIEW' and result['pattern']==PATTERN.pattern and result['source_identities']==identities(),'Different source census identity or scope')
    require(result['papers_scanned']==250 and result['positive_papers']==9 and len(result['candidates'])==9 and {row['paper_id'] for row in result['candidates']}==set(ROLES),'Different all250 positive source population')
    require([row['paper_id'] for row in result['roles']]==list(ROLES) and sum(len(row['primary_evidence']) for row in result['roles'])==result['source_paragraph_pointers']==15,'Changed ordered source-role/pointer population')
    for role in result['roles']:
        pid=role['paper_id'];spec=ROLES[pid];identity=next(row for row in identities() if row['paper_id']==pid);candidate=next(row for row in result['candidates'] if row['paper_id']==pid)
        require(role['tool_id']=='monocle' and role['pmcid']==identity['pmcid'] and role['doi']==identity['doi'] and role['source_xml_sha256']==identity['xml_sha256'],'Changed role identity')
        for source,target in [('analysis_role','analysis_role'),('usage_summary','usage_summary'),('scope','implementation_scope_note'),('binding','binding'),('commands','qualified_component_commands')]:require(role[target]==spec[source],'Changed reviewed source role, binding or caution')
        require(role['primary_evidence']==candidate['reviewed_paragraph_witnesses'] and [row['paragraph_index'] for row in role['primary_evidence']]==spec['paragraphs'],'Changed primary paragraph witnesses')
        witness=role['source_name_witness'];anchor=next(row for row in candidate['anchors'] if row['paragraph_index']==spec['paragraphs'][0]);require({key:witness[key] for key in ['literal','character_offset']} in anchor['matches'] and witness['paragraph_index']==spec['paragraphs'][0],'Changed literal source-name witness')
        require(role['reported_edition']==candidate['reported_edition_witness'] and (role['reported_edition'] is None)==(spec['reported_edition'] is None),'Invented or lost edition witness')
        if role['reported_edition']:require(role['reported_edition']['literal']==spec['reported_edition'] and role['reported_edition']['paragraph_index']==spec['paragraphs'][0],'Changed reported edition')
        require(role['reported_edition_component_controls_available']==(spec['reported_edition'] is not None) and not any(role[key] for key in ['paper_executed_commit_confirmed','author_pipeline_executed','whole_tool_qualified','whole_paper_reproduced']),'False source/author/whole-tool admission')
    require(result['new_observations']==result['new_review_records']==result['new_family_credit']==0 and not result['canonical_metadata_modified'] and not result['whole_goal_complete'],'Source review cannot award catalog credit')

def frozen():
    raw=(ROOT/FOLDER/'CENSUS.json').read_bytes();manifest=json.loads((ROOT/FOLDER/'CENSUS-PIN.json').read_bytes());require(pin(raw)==manifest['census'],'Changed frozen primary census');result=json.loads(raw);validate(result);return result
