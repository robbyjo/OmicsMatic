"""Read all frozen XML sources and the exact P190 supplement without publishing their text."""
from pathlib import Path
import copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,validate_supporting_evidence,words
from gsea_catalog.primary import identities,EXTRACTOR_SHA
from qtltools_catalog.decisions import ROLES,PDF_PIN,PDF_URL,PDF_PAGES

ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/qtltools-catalog-sources'
PATTERN=re.compile(r'(?<![A-Za-z0-9_])QTLtools(?![A-Za-z0-9_])',re.I)
VERSION=re.compile(r'(?i)(?<![A-Za-z0-9_])QTLtools(?:\s+MBV)?[\s(]*(?:version\s*|v\.?\s*)?(?P<edition>[0-9]+(?:\.[0-9]+)+)')

def require(ok,message):
    if not ok:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def pointer(block):return dict(paragraph_index=block['paragraph_index'],section=block['section'],paragraph_sha256=pin(block['text'].encode())['sha256'])

def versions(block):
    text=block.get('match_text',block['text'])
    return [dict(literal=m['edition'],paragraph_index=block['paragraph_index'],character_offset=m.start('edition'),package_and_edition_witness=m.group()) for m in VERSION.finditer(text)]

def auxiliary(path):
    try:import pypdf
    except ImportError as e:raise FileNotFoundError('Install permitted pypdf6.21.4 for this maintainer source audit: python -m pip install pypdf==6.21.4;otherwise skip this dependent audit') from e
    path=Path(path);require(path.stat().st_size==PDF_PIN['bytes'] and pin(path.read_bytes())==PDF_PIN,'Changed P190 publisher supplement')
    document=json.loads((ROOT/FOLDER/'AUXILIARY.json').read_bytes())
    require(document['pdf']==PDF_PIN and document['url']==PDF_URL and document['pages_total']==55 and document['manual_visual_pages']==[21,25],'Changed auxiliary identity or manual source scope')
    require(pypdf.__version__==document['extractor_version']=='6.21.4','Use recorded pypdf6.21.4 for exact source extraction')
    reader=pypdf.PdfReader(path);require(len(reader.pages)==55,'Different P190 supplement population')
    for page,heading in PDF_PAGES.items():
        text=reader.pages[page-1].extract_text();wanted=document['pages'][str(page)]
        require(pin(text.encode())==wanted['extracted_text'] and heading in text,'P190 full-page source text or heading differs')
    text=reader.pages[24].extract_text().replace('\u2212','-')
    for fragment in ['We used QTLTools','--permute','1000','--grp-best','adaptive permutation','prenormalized','Benjamini Hochberg']:
        require(fragment in text,'Missing P190 exact supplement witness: '+fragment)
    require(not VERSION.search(text),'Review newly reported P190 edition instead of retaining unknown')
    return document

def support(blocks,spec):
    records=[]
    for index in spec['paragraphs'][1:]:
        item=pointer(blocks[index])
        if not PATTERN.search(blocks[index].get('match_text',blocks[index]['text'])):
            require(index==28 and spec is ROLES['P219'],'Review new unnamed method-detail relationship')
            item.update(evidence_role='method-details',named_anchor=pointer(blocks[29]),shared_terms=['PEER','BMI'],relation_note='The adjacent named QTLtools mapping paragraph distinguishes its residual set from the BMI-adjusted residual set used for PEER construction here.')
        records.append(item)
    return records

def audit(cache,pdf):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Changed frozen XML paragraph extractor')
    aux=auxiliary(pdf);sources=identities();candidates=[];selected={};tables=[]
    for identity in sources:
        path=Path(cache)/(identity['pmcid']+'.xml.gz');require(path.stat().st_size<=64*1024*1024,'Compressed source exceeds capacity')
        with gzip.open(path,'rb') as stream:raw=stream.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==identity['xml_sha256'],'Primary XML identity differs: '+identity['paper_id'])
        tree=ET.fromstring(raw);doi=[e.text for e in tree.findall('front/article-meta/article-id') if e.get('pub-id-type')=='doi'];require(len(doi)==1 and doi[0].lower()==identity['doi'].lower(),'Primary DOI differs')
        blocks={b['paragraph_index']:b for b in paragraphs(tree)};anchors=[]
        for block in blocks.values():
            text=block.get('match_text',block['text']);matches=list(PATTERN.finditer(text))
            if matches:anchors.append(dict(**pointer(block),matching_text_sha256=pin(text.encode())['sha256'],matches=[dict(literal=m.group(),character_offset=m.start()) for m in matches],versions=versions(block)))
        # Tables are inspected separately; never lose an edition merely because
        # the frozen paragraph extractor omits table contents.
        for table in tree.iter('table-wrap'):
            text=words(table)
            if PATTERN.search(text):tables.append(dict(paper_id=identity['paper_id'],table_id=table.get('id'),text_sha256=pin(text.encode())['sha256']))
        if not anchors:continue
        pid=identity['paper_id'];require(pid in ROLES,'Review a new QTLtools candidate instead of dropping it')
        candidates.append(dict(**identity,anchors=anchors));selected[pid]=(blocks,tree)
    require([r['paper_id'] for r in candidates]==list(ROLES) and not tables,'QTLtools paper/table population changed;review before admission')
    roles=[]
    for pid,spec in ROLES.items():
        identity=next(r for r in sources if r['paper_id']==pid);blocks,tree=selected[pid];block=blocks[spec['paragraphs'][0]];text=block.get('match_text',block['text']);match=PATTERN.search(text);require(match is not None,'Primary anchor does not name QTLtools')
        found=versions(block);reported=next((w for w in found if w['literal']==spec['version']),None) if spec['version'] else None
        require((reported is not None)==(spec['version'] is not None),'Missing expected explicit primary edition')
        if spec['version'] is None:require(not found,'New explicit primary edition requires review')
        operation_witnesses=[]
        for operation in spec['operations']:
            ob=blocks[operation['paragraph']];available=versions(ob);edition=operation['edition']
            witness=next((w for w in available if w['literal']==edition),None) if edition else None
            require((witness is not None)==(edition is not None),'Missing operation-specific reported edition')
            if edition is None:require(not available,'Do not carry unknown edition past an explicit operation witness')
            operation_witnesses.append(dict(**copy.deepcopy(operation),edition_witness=witness,paper_command_or_binary_reproduced=False))
        record=dict(paper_id=pid,pmcid=identity['pmcid'],doi=identity['doi'],tool_id='qtltools',source_xml_sha256=identity['xml_sha256'],analysis_role=spec['analysis_role'],usage_summary=spec['summary'],implementation_scope_note=spec['scope'],usage_context=spec['context'],binding=spec['binding'],primary_evidence=pointer(block),supporting_evidence=support(blocks,spec),source_name_witness=dict(literal=match.group(),character_offset=match.start(),paragraph_index=block['paragraph_index']),reported_edition=reported,operations=operation_witnesses,auxiliary_evidence=[dict(url=PDF_URL,pdf=PDF_PIN,pages=[21,25],sections=['Note S1.5','Note S1.7'],source_scope='Explicit QTLtools run in supplement;edition unstated;fixed-count/adaptive-wording discrepancy retained')] if pid=='P190' else [],paper_executed_commit_confirmed=False,author_pipeline_executed=False,whole_tool_qualified=False,whole_paper_reproduced=False)
        if pid=='P227':
            require('1.2.27' in block['text'] and '1.2.27' not in text and '1.2' in text,'Citation/version distinction changed')
            citations=[dict(rid=e.get('rid'),label=words(e)) for e in tree.iter('xref') if e.get('ref-type')=='bibr' and words(e)=='27'];require(citations,'Missing bibliography-label27 witness')
            record['citation_version_distinction']=dict(raw_flattened='1.2.27',citation_pruned_edition='1.2',bibliography_label='27',xref_witnesses=citations)
        review=dict(tool_id='qtltools',supporting_evidence=record['supporting_evidence'])
        validate_supporting_evidence(list(blocks.values()),[review],{'qtltools':dict(pattern=PATTERN.pattern)})
        roles.append(record)
    result=dict(schema_version=1,status='PASS_FRESH250_QTLTOOLS9_INDIVIDUALLY_REVIEWED_SOURCE_ROLES',source_identities=sources,extractor_sha256=EXTRACTOR_SHA,pattern=PATTERN.pattern,candidates=candidates,roles=roles,auxiliary=aux,papers_scanned=250,positive_papers=9,named_paragraph_pointers=sum(len(r['anchors']) for r in candidates),reviewed_XML_pointers=sum(1+len(r['supporting_evidence']) for r in roles),reviewed_PDF_pages=2,table_candidates=tables,new_observations=0,proposed_new_reviews=9,new_family_credit=0,canonical_metadata_modified=False,whole_goal_complete=False)
    validate(result);return result

def validate(result):
    require(result['schema_version']==1 and result['status']=='PASS_FRESH250_QTLTOOLS9_INDIVIDUALLY_REVIEWED_SOURCE_ROLES' and result['source_identities']==identities() and result['pattern']==PATTERN.pattern,'Different source identity or audit scope')
    require(result['papers_scanned']==250 and result['positive_papers']==len(result['roles'])==9 and [r['paper_id'] for r in result['roles']]==list(ROLES),'Different nine-paper role population')
    require(result['named_paragraph_pointers']==result['reviewed_XML_pointers']==19 and result['reviewed_PDF_pages']==2 and result['table_candidates']==[],'Changed paragraph/table/supplement scope')
    require(result['new_observations']==result['new_family_credit']==0 and result['proposed_new_reviews']==9 and not result['canonical_metadata_modified'] and not result['whole_goal_complete'],'Source review cannot admit metadata or family credit')
    for role in result['roles']:
        spec=ROLES[role['paper_id']];require(role['usage_summary']==spec['summary'] and role['implementation_scope_note']==spec['scope'] and role['binding']==spec['binding'],'Changed individually reviewed source interpretation')
        require(role['primary_evidence']['paragraph_index']==spec['paragraphs'][0] and [r['paragraph_index'] for r in role['supporting_evidence']]==spec['paragraphs'][1:],'Changed reviewed source anchors')
        require((role['reported_edition']['literal'] if role['reported_edition'] else None)==spec['version'],'Invented/lost primary edition')
        require(not any(role[k] for k in ['paper_executed_commit_confirmed','author_pipeline_executed','whole_tool_qualified','whole_paper_reproduced']),'False paper/tool execution qualification')
        require([dict((k,v) for k,v in op.items() if k not in ['edition_witness','paper_command_or_binary_reproduced']) for op in role['operations']]==spec['operations'] and not any(op['paper_command_or_binary_reproduced'] for op in role['operations']),'Changed operation-specific edition boundaries')

def frozen():
    raw=(ROOT/FOLDER/'CENSUS.json').read_bytes();stamp=json.loads((ROOT/FOLDER/'CENSUS-PIN.json').read_bytes());require(pin(raw)==stamp['census'],'Changed frozen source census');result=json.loads(raw);validate(result);return result
