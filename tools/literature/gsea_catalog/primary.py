"""Audit the 250 frozen primary XML sources and exact author supplements."""
from pathlib import Path
from collections import Counter
import gzip,hashlib,io,json,re,shlex,xml.etree.ElementTree as ET,zipfile
from collect_omics_review import paragraphs
from gsea_catalog.decisions import ROLES,EXCLUSIONS,EXTRA_CONTEXT,ADDITIONS,ORIGINAL

ROOT=Path(__file__).resolve().parents[3]
FOLDER='docs/literature/gsea-catalog-sources'
CENSUS=FOLDER+'/CENSUS.json'
PATTERN=re.compile(r'(?<![A-Za-z0-9_])(?:preranked[- ]?GSEA|GSEA|fgsea|GSEApy|GSEAPreranked|ssGSEA|gseGO|gseKEGG|gsePathway)(?![A-Za-z0-9_])|gene[- ]sets? enrichment',re.I)
EXTRACTOR_SHA='64198266fd706c13a1a4e624a2d68e9779477081c5b8644f2fade3114c780396'

def require(ok,message):
    if not ok:raise ValueError(message)

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False)+'\n').encode()

def identities():
    rows=json.loads((ROOT/FOLDER/'IDENTITIES.json').read_bytes())
    require(len(rows)==250 and [r['paper_id'] for r in rows]==['P%03d'%i for i in range(1,251)],'Different ordered primary collection')
    require(len({r['pmcid'] for r in rows})==len({r['doi'] for r in rows})==250,'Duplicate frozen primary identity')
    # Preserve the frozen source's capitalization variants; do not rewrite labels.
    require(Counter(r['journal'] for r in rows)=={'Nature':71,'Nature Genetics':103,'Nature genetics':1,
        'American Journal of Human Genetics':72,'American journal of human genetics':3},'Different literal frozen journals')
    require(all('2023-10-23'<=r['first_publication_date']<='2026-10-23' for r in rows),'Frozen collection window differs')
    return rows

def auxiliary(pdf=None):
    aux=json.loads((ROOT/FOLDER/'AUXILIARY.json').read_bytes());record=aux['p096'];raw=(ROOT/FOLDER/'P096-author-code.zip').read_bytes()
    require(pin(raw)==record['archive'],'Changed author code archive')
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        require(len(z.namelist())==len(set(z.namelist()))==31 and set(z.namelist())==set(record['members']),'Author-source population differs')
        files={n:z.read(n) for n in z.namelist()}
        for n,w in record['members'].items():require(pin(files[n])==w,'Changed author source: '+n)
        require(z.testzip() is None,'Corrupt author-code archive')
    metadata=json.loads(files['record.json']);require(metadata['id']==13321265 and metadata['metadata']['license']=={'id':'cc-by-4.0'},'Different author record/license')
    require(len(metadata['files'])==29 and record['code_executed'] is False,'Author source audit must not execute scripts')
    for item in metadata['files']:
        require(item['checksum']=='md5:'+hashlib.md5(files['scripts/'+item['key']]).hexdigest(),'Published author checksum differs')
    script=files['scripts/Script_20.sh'].decode('utf-8-sig');commands=[line for line in script.splitlines() if line.strip().startswith('/GSEA_directory/gsea-cli.sh GSEAPreranked ')]
    require(len(commands)==1,'Different author GSEAPreranked invocation')
    words=shlex.split(commands[0]);options=record['script20_options']
    for flag,value in options.items():require(words.count(flag)==1 and words[words.index(flag)+1]==value,'Author option differs: '+flag)
    require('c5.go.bp.v2023.1.Hs.symbols.gmt' in words[words.index('-gmx')+1] and record['executable_edition_unresolved'],'Collection label must not resolve an executable edition')
    for n in ['visually_reviewed','no_GSEA_keywords_in_extracted_document']:
        require(aux['p077'][n] is True,'Supplementary-source review missing')
    require(aux['p077']['GSEA_execution_options_resolved'] is False,'Unstated GSEA options cannot be recovered by inference')
    if pdf is not None:
        data=Path(pdf).read_bytes();require(pin(data)==aux['p077']['pdf'],'Different exact P077 supplementary PDF')
        try:
            import pypdf
        except ImportError as error:
            raise FileNotFoundError('P077 source audit needs permitted pypdf=='+aux['p077']['extractor_version']+'; install it or keep the affected fresh source audit SKIP3.') from error
        if pypdf.__version__!=aux['p077']['extractor_version']:
            raise FileNotFoundError('Fresh P077 source audit requires permitted pypdf=='+aux['p077']['extractor_version']+' for exact page witnesses; supply that version or retain SKIP3.')
        reader=pypdf.PdfReader(io.BytesIO(data));require(len(reader.pages)==57,'Different supplement page population')
        for row in aux['p077']['audited_pages']:
            text=reader.pages[row['page']-1].extract_text();require(pin(text.encode())==row['text'],'Changed supplementary page witness')
        require(not any(re.search(r'GSEA|gene[- ]set enrichment',page.extract_text(),re.I) for page in reader.pages),'Supplement text audit differs')
    return aux

def anchor(block):
    text=block['text']
    return dict(paragraph_index=block['paragraph_index'],section=block['section'],paragraph_sha256=pin(text.encode())['sha256'],
        matched_tokens=[dict(literal=m.group(),start=m.start(),end=m.end()) for m in PATTERN.finditer(text)])

def audit(cache,pdf):
    require(pin((ROOT/'tools/literature/collect_omics_review.py').read_bytes())['sha256']==EXTRACTOR_SHA,'Changed frozen paragraph extractor')
    sources=identities();aux=auxiliary(pdf);candidates=[];roles=[];seen=set()
    require(set(ROLES).isdisjoint(EXCLUSIONS) and len(ROLES)==len(EXCLUSIONS)==28,'Different curated decision population')
    require(set(ORIGINAL).isdisjoint(ADDITIONS) and set(ROLES)==set(ORIGINAL)|set(ADDITIONS),'Different reviewed role additions')
    for source in sources:
        pid=source['paper_id'];path=Path(cache)/(source['pmcid']+'.xml.gz')
        with gzip.open(path,'rb') as f:raw=f.read(64*1024*1024+1)
        require(len(raw)<=64*1024*1024 and pin(raw)['sha256']==source['xml_sha256'],'Changed primary XML: '+pid)
        root=ET.fromstring(raw);dois=[n.text for n in root.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi']
        require(len(dois)==1 and dois[0].lower()==source['doi'].lower(),'Different primary DOI: '+pid)
        blocks=paragraphs(root);by_index={b['paragraph_index']:b for b in blocks};hits=[b for b in blocks if PATTERN.search(b['text'])]
        if not hits:continue
        seen.add(pid);require(pid in ROLES or pid in EXCLUSIONS,'Review newly named enrichment candidate: '+pid)
        selected=[anchor(b) for b in hits]
        additional=[anchor(by_index[i]) for i in EXTRA_CONTEXT.get(pid,[]) if i not in {b['paragraph_index'] for b in hits}]
        decision='ranked-GSEA-reported' if pid in ROLES else EXCLUSIONS[pid][0]
        candidates.append(dict(paper_id=pid,pmcid=source['pmcid'],doi=source['doi'],disposition=decision,
            reason=ROLES[pid][5] if pid in ROLES else EXCLUSIONS[pid][1],anchors=selected,additional_context=additional))
        if pid not in ROLES:continue
        primary,support,provider,versions,summary,caution=ROLES[pid];indices=[primary]+support
        require(len(indices)==len(set(indices)) and set(indices)<=set(by_index),'Invalid curated source paragraphs: '+pid)
        pointers=[anchor(by_index[i]) for i in indices]
        witnesses=[]
        for value in versions:
            locations=[(i,by_index[i]['text'].find(value)) for i in indices if value in by_index[i]['text']]
            require(bool(locations),'Reported provider version is absent: '+pid+' '+value)
            version_provider='clusterProfiler' if pid=='P020' else provider
            i,offset=locations[0];witnesses.append(dict(provider=version_provider,literal=value,paragraph_index=i,character_offset=offset))
        matching=[v for v in versions if provider=='Broad GSEA' and v in ['4.3.2','4.3.3','4.4.0']]
        roles.append(dict(paper_id=pid,pmcid=source['pmcid'],doi=source['doi'],source_xml_sha256=source['xml_sha256'],
            role='Ranked gene-set enrichment',reported_provider=provider,reported_provider_versions=witnesses,
            primary_evidence=pointers[0],supporting_evidence=pointers[1:],usage_summary=summary,
            required_caution=caution,existing_observation=pid in ORIGINAL,proposed_observation=pid in ADDITIONS,
            evidence_kind='analysis-result-statement-operation-unresolved' if pid=='P042' else 'analysis-method-description',
            matching_Broad_source_profiles=matching,native_Broad_profile_counterpart_qualified=bool(matching),
            other_provider_backend_qualified_by_Broad=False,author_executable_edition_unresolved=not bool(matching),
            exact_author_inputs_recovered=False,historical_author_seed_recovered=False,author_runtime_executed=False,
            complete_author_workflow_qualified=False,whole_paper_reproduced=False,
            auxiliary_sources=['P096-author-code.zip'] if pid=='P096' else ['P077-supplement-pages-14-15'] if pid=='P077' else []))
    require(seen==set(ROLES)|set(EXCLUSIONS),'Expected candidate missing from frozen primary collection')
    result=dict(schema_version=1,status='PASS_250_PRIMARY_SOURCES_56_CANDIDATES_28_RANKED_ROLES_WITH_PROVIDER_DISTINCTIONS',
        pattern=PATTERN.pattern,papers_scanned=250,source_identities=sources,candidates=candidates,roles=roles,
        positive_roles=28,negative_or_unresolved_candidates=28,existing_observations=19,proposed_additions=list(ADDITIONS),
        auxiliary_source_records=aux,supplementary_PDF_freshly_checked=True,canonical_metadata_modified=False,new_family_credit=0,
        new_canonical_reviews=0,whole_goal_complete=False)
    validate(result);return result

def validate(result):
    require(result['schema_version']==1 and result['status']=='PASS_250_PRIMARY_SOURCES_56_CANDIDATES_28_RANKED_ROLES_WITH_PROVIDER_DISTINCTIONS','Different source audit status/schema')
    require(result['source_identities']==identities() and result['auxiliary_source_records']==auxiliary(),'Different source/auxiliary identities')
    require(result['papers_scanned']==250 and result['positive_roles']==28 and result['negative_or_unresolved_candidates']==28,'Different source audit population')
    require([r['paper_id'] for r in result['roles']]==sorted(ROLES) and {c['paper_id'] for c in result['candidates']}==set(ROLES)|set(EXCLUSIONS),'Different ordered role/candidate population')
    require(result['proposed_additions']==ADDITIONS and result['existing_observations']==19,'Different source additions')
    require(not result['canonical_metadata_modified'] and result['new_family_credit']==result['new_canonical_reviews']==0,'Source-only audit cannot award credit')
    for r in result['roles']:
        pid=r['paper_id'];spec=ROLES[pid]
        require(r['primary_evidence']['paragraph_index']==spec[0] and [a['paragraph_index'] for a in r['supporting_evidence']]==spec[1],'Different role anchors: '+pid)
        require(r['reported_provider']==spec[2] and [v['literal'] for v in r['reported_provider_versions']]==spec[3] and r['usage_summary']==spec[4] and r['required_caution']==spec[5],'Different curated provider/role: '+pid)
        matching=[v for v in spec[3] if spec[2]=='Broad GSEA' and v in ['4.3.2','4.3.3','4.4.0']]
        require(r['matching_Broad_source_profiles']==matching and r['author_executable_edition_unresolved']==(not bool(matching)),'False provider/executable-edition attribution: '+pid)
        require(r['existing_observation']==(pid in ORIGINAL) and r['proposed_observation']==(pid in ADDITIONS),'False observation credit: '+pid)
        require(not r['other_provider_backend_qualified_by_Broad'] and not r['author_runtime_executed'] and not r['complete_author_workflow_qualified'] and not r['whole_paper_reproduced'],'False provider/author completion claim')
        require(r['native_Broad_profile_counterpart_qualified']==(pid in ['P077','P220']),'False edition/provider match: '+pid)

def frozen():
    result=json.loads((ROOT/CENSUS).read_bytes());validate(result);return result
