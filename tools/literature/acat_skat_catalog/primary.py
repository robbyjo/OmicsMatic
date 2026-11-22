"""Re-read the frozen corpus and exact supplement; publish pointers, not primary text."""
from pathlib import Path
import copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,words,validate_supporting_evidence
from gsea_catalog.primary import identities,EXTRACTOR_SHA
from acat_skat_catalog.decisions import ROLES,NEGATIVE_DECISIONS,PATTERNS
ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/acat-skat-catalog-sources'
DISCOVERY=re.compile(r'(?<![A-Za-z0-9_])(?:MetaSKAT|SKATBinary(?:_Robust)?|SKAT(?:[-\s]?O)?|ACAT(?:[-\s]?[OV])?|Cauchy[\s-]+combination)(?![A-Za-z0-9_])',re.I)
PDF_PIN=dict(bytes=3125933,sha256='468d4018f261f438654d873b539952dd157417910844d68de34be68e5d3678f0')
PDF_URL='https://ars.els-cdn.com/content/image/1-s2.0-S0002929725004033-mmc1.pdf'
def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def pointer(block):return dict(paragraph_index=block['paragraph_index'],section=block['section'],paragraph_sha256=pin(block['text'].encode())['sha256'])

def supplement(path):
    path=Path(path);require(pin(path.read_bytes())==PDF_PIN,'Changed P190 publisher supplement')
    try:import pypdf
    except ImportError as error:raise FileNotFoundError('Supply permitted pypdf==6.21.4 or skip this dependent source audit') from error
    require(pypdf.__version__=='6.21.4','Use exact pypdf6.21.4 for the source audit')
    reader=pypdf.PdfReader(path);require(len(reader.pages)==55,'Changed supplement page population')
    stamp=json.loads((ROOT/FOLDER/'AUXILIARY.json').read_bytes());require(stamp['pdf']==PDF_PIN and stamp['manual_visual_pages']==[31,32,36],'Changed inspected PDF identity/scope')
    for n in [31,32,36]:require(pin(reader.pages[n-1].extract_text().encode())==stamp['pages'][str(n)]['text'],'Changed full-page extracted source')
    text=reader.pages[31].extract_text();require('uniform' in text and 'scale' in text and 'ACAT' in text,'Missing selected uniform-weight/scale source witness')
    return stamp

def audit(cache,pdf):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Changed frozen paragraph extractor')
    aux=supplement(pdf);sources=identities();selected={};candidates=[];tables=[]
    for identity in sources:
        path=Path(cache)/(identity['pmcid']+'.xml.gz')
        require(path.stat().st_size<=64*1024*1024,'Compressed XML exceeds capacity')
        with gzip.open(path,'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==identity['xml_sha256'],'Primary XML identity differs: '+identity['paper_id'])
        tree=ET.fromstring(raw);doi=[e.text for e in tree.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi']
        require(len(doi)==1 and doi[0].lower()==identity['doi'].lower(),'Primary DOI differs')
        blocks={b['paragraph_index']:b for b in paragraphs(tree)};anchors=[]
        for block in blocks.values():
            text=block.get('match_text',block['text']);matches=list(DISCOVERY.finditer(text))
            if matches:anchors.append(dict(**pointer(block),matching_text_sha256=pin(text.encode())['sha256'],matches=[dict(literal=m.group(),character_offset=m.start()) for m in matches]))
        for table in tree.iter('table-wrap'):
            text=words(table)
            if DISCOVERY.search(text):tables.append(dict(paper_id=identity['paper_id'],table_id=table.get('id'),text_sha256=pin(text.encode())['sha256']))
        if anchors:candidates.append(dict(**identity,anchors=anchors));selected[identity['paper_id']]=blocks
    require(len(candidates)==19 and sum(len(r['anchors']) for r in candidates)==95,'Changed candidate census; review new source matches')
    require(len(tables)==1 and tables[0]['paper_id']=='P220' and tables[0]['table_id']=='tbl1','Changed table census; review before admission')
    roles=[]
    for spec in ROLES:
        blocks=selected[spec['paper_id']];first=blocks[spec['paragraphs'][0]];text=first.get('match_text',first['text']);match=re.search(PATTERNS[spec['tool_id']],text,re.I)
        require(match is not None,'Named role anchor missing: '+spec['paper_id']+'/'+spec['tool_id']);support=[]
        for n in spec['paragraphs'][1:]:
            item=pointer(blocks[n])
            if n in spec['linked_details']:item.update(evidence_role='method-details',named_anchor=pointer(first),shared_terms=spec['linked_details'][n],relation_note='Individually inspected details of the same named gene-test workflow; this paragraph supplies the declared provider, filtering, or combination step rather than another tool execution.')
            support.append(item)
        try:validate_supporting_evidence(list(blocks.values()),[dict(tool_id=spec['tool_id'],supporting_evidence=support)],{k:dict(pattern=v) for k,v in PATTERNS.items()})
        except ValueError as error:raise ValueError(spec['paper_id']+'/'+spec['tool_id']+': '+str(error)) from error
        provider=copy.deepcopy(spec['provider'])
        if provider:
            source=blocks[provider['paragraph_index']];source_text=source.get('match_text',source['text']);witness=re.search(provider.pop('pattern'),source_text,re.I)
            require(witness is not None,'Provider/edition witness missing: '+spec['paper_id'])
            provider.update(evidence=pointer(source),character_offset=witness.start(),witness_sha256=pin(witness.group().encode())['sha256'],relationship='selected-burden-workflow-not-an-executed-SKAT-test' if spec['usage_context']=='comparator-not-established-as-executed' else 'reported-provider-context')
        roles.append(dict(paper_id=spec['paper_id'],tool_id=spec['tool_id'],primary_evidence=pointer(first),supporting_evidence=support,source_name_witness=dict(literal=match.group(),character_offset=match.start()),usage_summary=spec['usage_summary'],implementation_scope_note=spec['implementation_scope_note'],usage_context=spec['usage_context'],provider=provider,reported_tool_edition=spec['reported_tool_edition'],auxiliary_evidence=[dict(url=PDF_URL,pdf=PDF_PIN,pages=[31,32,36],sections=['Note S3.3','Note S5.1'],source_scope='Uniform-weight Cauchy aggregation and correlated-junction simulation;software edition unstated.')] if spec['paper_id']=='P190' else [],recovered_observation=spec['paper_id']=='P127' and spec['tool_id']=='skato',author_pipeline_executed=False,paper_executed_commit_confirmed=False,whole_tool_qualified=False,whole_paper_reproduced=False))
    negative=[]
    for decision in NEGATIVE_DECISIONS:
        row=copy.deepcopy(decision);row['evidence']=[pointer(selected[row['paper_id']][n]) for n in row.pop('paragraphs')];negative.append(row)
    result=dict(schema_version=1,status='PASS_FRESH250_ACAT_SKAT22_SOURCE_ROLES',source_identities=sources,extractor_sha256=EXTRACTOR_SHA,discovery_pattern=DISCOVERY.pattern,candidates=candidates,table_candidates=tables,roles=roles,negative_decisions=negative,auxiliary=aux,papers_scanned=250,positive_papers=19,named_paragraph_pointers=95,reviewed_PDF_pages=3,new_observations=1,proposed_new_reviews=22,new_family_credit=0,canonical_metadata_modified=False,whole_goal_complete=False)
    validate(result);return result

def validate(result):
    require(result['schema_version']==1 and result['status']=='PASS_FRESH250_ACAT_SKAT22_SOURCE_ROLES' and result['source_identities']==identities(),'Changed source identities/scope')
    require(result['papers_scanned']==250 and result['positive_papers']==19 and result['named_paragraph_pointers']==95 and result['reviewed_PDF_pages']==3,'Different source audit scope')
    require(len(result['roles'])==22 and len({(r['paper_id'],r['tool_id']) for r in result['roles']})==22,'Duplicate/missing role')
    require(result['new_observations']==1 and result['proposed_new_reviews']==22 and result['new_family_credit']==0 and not result['canonical_metadata_modified'] and not result['whole_goal_complete'],'Source audit cannot admit family credit or finish the goal')
    for row,spec in zip(result['roles'],ROLES,strict=True):
        for key in ['paper_id','tool_id','usage_summary','implementation_scope_note','usage_context','reported_tool_edition']:require(row[key]==spec[key],'Changed inspected interpretation: '+key)
        require(row['primary_evidence']['paragraph_index']==spec['paragraphs'][0] and [p['paragraph_index'] for p in row['supporting_evidence']]==spec['paragraphs'][1:],'Changed inspected support pointers')
        require(all(not row[k] for k in ['author_pipeline_executed','paper_executed_commit_confirmed','whole_tool_qualified','whole_paper_reproduced']),'False execution/reproduction claim')
    require([(r['paper_id'],r['tool_id']) for r in result['roles'] if r['recovered_observation']]==[('P127','skato')],'Different recovered observation')

def frozen():
    raw=(ROOT/FOLDER/'CENSUS.json').read_bytes();require(pin(raw)==json.loads((ROOT/FOLDER/'CENSUS-PIN.json').read_bytes())['census'],'Changed frozen census');result=json.loads(raw);validate(result);return result
