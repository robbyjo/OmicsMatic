# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check distinct main-article and complete supplementary DOCX evidence."""
from pathlib import Path
import gzip,hashlib,re,xml.etree.ElementTree as ET,zipfile
from collect_omics_review import paragraphs,publication_record
from collect_htseq_omics import tool_matches
from malt_source_registry import ENTRIES,IDS

ROOT=Path(__file__).resolve().parents[2]
PMCID='PMC11964931'
DOI='10.1038/s41586-024-08515-5'
XML_SHA='7970057057eb527abb957f1fb9b21306dc3c3eeb89dcdc4aa3b874df7ee15965'
DOC_SHA='cefa7be7b2bb5313cce9ddb23d5369020c2ef74a90b99daf78a889270db23451'
SUPPLEMENT_URL='https://media.springernature.com/full/springer-static/esm/art%3A10.1038%2Fs41586-024-08515-5/MediaObjects/41586_2024_8515_MOESM1_ESM.docx'
W='{http://schemas.openxmlformats.org/wordprocessingml/2006/main}'


def sha(raw):return hashlib.sha256(raw).hexdigest()


def supplement(path=None):
    path=Path(path) if path else ROOT/'build/literature-review/nfcore-runtime/eager-paper-supplement-4/supplement.docx'
    if not path.is_file():raise FileNotFoundError('Required action: supply the complete permitted P002 supplementary DOCX from '+SUPPLEMENT_URL+' at '+str(path)+'; verify SHA256 '+DOC_SHA+'. If unavailable, skip dependent supplementary-source curation/audit.')
    if path.stat().st_size!=10993402:raise ValueError('Complete P002 supplementary DOCX byte count differs; no dependent source review is admitted')
    raw=path.read_bytes();assert sha(raw)==DOC_SHA
    with zipfile.ZipFile(path) as z:body=z.read('word/document.xml')
    ps=ET.fromstring(body).findall('.//'+W+'p');assert len(ps)==863
    result={}
    for index in [235,236,237,238,246]:
        p=ps[index];text=re.sub(r'\s+',' ',''.join(n.text or '' for n in p.iter(W+'t'))).strip()
        result[index]=dict(text=text,paragraph_index=index,paragraph_index_basis='zero-based-all-Word-paragraphs-including-empty',paragraph_sha256=sha(text.encode()),paragraph_xml_sha256=sha(ET.tostring(p)),source_kind='supplementary-docx',source_url=SUPPLEMENT_URL,document_sha256=DOC_SHA,document_part='word/document.xml',document_part_sha256=sha(body),section='Supplementary information / '+('3.3 Candidate identification and RMA extraction' if index==246 else '3.2 Taxonomic identification using MALT'))
    assert result[235]['paragraph_xml_sha256']=='b547ee7df001521336cb4dba6e2609003a34b3185a6607c957e06d57fe3eb192'
    assert result[246]['paragraph_xml_sha256']=='81df3289c052782f9db7b53508bb1bc0deb9d2a7e80054ef61832ffe58cb459c'
    runs=ps[235].findall(W+'r');parts=[(''.join(n.text or '' for n in r.iter(W+'t')),r.find(W+'rPr/'+W+'vertAlign')) for r in runs]
    assert parts[0][0].endswith('MALT) v0.5.2') and parts[0][1] is None
    assert [(s,v.get(W+'val') if v is not None else None) for s,v in parts[1:3]]==[('6','superscript'),('1','superscript')]
    assert 'MaltExtract' in result[246]['text']
    return result


def pointer(row):return {k:v for k,v in row.items() if k!='text'}


def primary_paragraphs(paper):
    raw=gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
    assert sha(raw)==paper['xml_sha256'];xml=ET.fromstring(raw)
    assert next(n.text for n in xml.findall('front/article-meta/article-id') if n.get('pub-id-type')=='doi').lower()==paper['doi'].lower()
    return paragraphs(xml)


def observations(paper,ps,sp):
    assert (paper['paper_id'],paper['pmcid'],paper['doi'],paper['xml_sha256'])==('P002',PMCID,DOI,XML_SHA)
    by={p['paragraph_index']:p for p in ps};anchor=by[29];preferred={k:sha(anchor['text'].encode()) for k in ['malt','hops']}
    candidates=tool_matches(ps,ENTRIES,preferred);assert {r['tool_id'] for r in candidates}=={'malt','hops'} and all(r['evidence_paragraph_index']==29 for r in candidates)
    row=sp[246];candidates.append(dict(tool_id='maltextract',tool='MaltExtract',analysis_role=ENTRIES[2]['role'],evidence_section=row['section'],evidence_paragraph_index=246,evidence_text=row['text'],evidence_sentence=row['text'],matched_text='MaltExtract',extraction_score=0,usage_context='analysis-method-description',review_status='candidate-reviewed-supplementary-source',version_reported_near_name=None,evidence_selection='exact-reviewed-supplementary-paragraph'))
    main_support=dict(paragraph_index=7,paragraph_sha256=sha(by[7]['text'].encode()),section=by[7]['section'])
    common=dict(pmcid=PMCID,doi=DOI)
    reviews=[
      dict(**common,tool_id='malt',evidence_paragraph_sha256=preferred['malt'],use_summary='Used MALT within eager/HOPS for ancient-pathogen screening against a stated 2017 database. Separately built a 91-spirochete custom index and screened 36214 sequencing datasets with MALT 0.5.2, BlastN/SemiGlobal, identity 90%, up to 25 alignments per query and 100000 per reference, followed by target extraction and a separate bacterial rescreen.',implementation_scope_note='The main article does not report the eager-screening MALT version. The 0.4.1 development adapter is a software control, not proof of the authors environment or the separate 0.5.2 role. The supplement reports 0.5.2 followed by superscript citation 61, not 0.5.261. Custom reference dates, accession mapping, aligned-read output and 130G/16-worker execution remain separate requirements; current small controls do not reproduce the historical analysis.',version_reported_near_name=None,supporting_evidence=[main_support],supplementary_evidence=[pointer(sp[i]) for i in [235,236,237,238]]),
      dict(**common,tool_id='hops',evidence_paragraph_sha256=preferred['hops'],use_summary='Used the HOPS workflow to assess pathogen candidates from MALT screening and damage-related profiles. Candidates with too few reads to assess remained unassessable; this did not establish absence.',implementation_scope_note='HOPS version and extraction/filter settings are unreported in the main Methods paragraph. Native evidence statistics are scoped components; complete HOPS, MaltExtract and paper-specific authenticity assessment remain open. Preserve the stated 2017 database requirements and distinguish the custom 0.5.2 screening stage.',version_reported_near_name=None,supporting_evidence=[main_support]),
      dict(**common,tool_id='maltextract',evidence_paragraph_sha256=row['paragraph_sha256'],use_summary='Used MaltExtract on custom-screening RMA6 output to identify Treponema pallidum subsp. pallidum candidates. Extraction was followed by manual summary/damage assessment and a separate screen against 7508 bacterial genomes.',implementation_scope_note='This evidence is supplementary DOCX section 3.3, not a main-JATS paragraph. MaltExtract version and filter/resource settings are unreported. Qualify the original provider and explicit choices; do not infer defaults from the outdated standalone README or substitute the 0.4.1 RMA reassignment command.',version_reported_near_name=None,evidence_source_kind='supplementary-docx',supplementary_primary_source=pointer(row)),
    ]
    extracted=dict(paper,tools=candidates);published=publication_record(extracted,reviews)['tools']
    for t in published:
        review=next(r for r in reviews if r['tool_id']==t['tool_id'])
        if 'supplementary_evidence' in review:t['supplementary_evidence']=review['supplementary_evidence']
        if t['tool_id']=='maltextract':
            t.update(evidence_source_kind='supplementary-docx',evidence_source_url=SUPPLEMENT_URL,evidence_document_sha256=DOC_SHA,supplementary_primary_source=pointer(row),evidence_locator=SUPPLEMENT_URL+' (supplement section 3.3; Word paragraph index246, zero-based including empty paragraphs)')
    return published,reviews,candidates
