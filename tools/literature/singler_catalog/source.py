# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Source-specific SingleR annotation roles and three distinct missing families."""
from pathlib import Path
import copy,gzip,hashlib,json,re,xml.etree.ElementTree as ET
from collect_omics_review import paragraphs,validate_supporting_evidence

MODULE='tools/literature/singler_catalog'
PROGRAM='tools/literature/singler_workflows/run.py'
NOTE=('Project availability: complete unchanged original SingleR annotation in explicitly selected 1.2.4, 1.8.1, 2.2.0, 2.6.0 and 2.8.0 source profiles, with complete returns, conditions and RNG. '
 'The runtime, original references, label vectors, caller settings and RNG must be supplied explicitly; project comparison environments are not recovered author environments. '
 'Original errors remain errors. Literal 2.41 is unresolved and its dependent analysis is SKIP3 without version substitution. '
 'Source-specific releases/references are not borrowed between analysis contexts. Saved training pointers are records, not reusable live native indexes. '
 'SKIP3 skips dependent analysis for unavailable/forbidden requirements; FAIL2 admits no complete result, including recording failure. '
 'Whole-tool validity, biological decisions and paper reproduction remain unqualified.')

def definitions(root):return json.loads((root/MODULE/'definitions.json').read_bytes())

def census(root):
    # The public reader retains hashes, links and licenses; primary paragraphs
    # remain research inputs under the original article terms.
    import importlib.util
    spec=importlib.util.spec_from_file_location('singler_primary_census',root/'tools/literature/singler_source_audit/audit.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    result=module.audit();result.pop('primary_anchor_texts')
    result['primary_paragraph_texts_retained_in_original_private_cache']=True
    return json.loads(json.dumps(result,ensure_ascii=False))

def reviews(root):
    value=json.loads((root/'tools/literature/singler_source_audit/reviews.json').read_bytes())
    rows=copy.deepcopy(value['reviews'])
    if [r['paper_id'] for r in rows]!=['P051','P082','P192','P233']:raise ValueError('Four exact source-specific readings required')
    for row in rows:row['implementation_scope_note']=NOTE
    paper=next(p for p in json.loads((root/'docs/literature/omics-2023-2026/papers.json').read_bytes()) if p['paper_id']=='P051')
    raw=gzip.decompress((root/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
    if hashlib.sha256(raw).hexdigest()!=paper['xml_sha256']:raise ValueError('P051 original XML differs')
    ps=paragraphs(ET.fromstring(raw));by_index={p['paragraph_index']:p for p in ps}
    facts={
      'celldex':([119],'1.14.0',
        'Retrieved reference profiles for SingleR annotation, described literally as MouseRNAseq ImmGen databases via celldex 1.14.0 in the PDAC/MI analysis. The human-neutrophil subsection and reference wording remain unchanged; exact reference functions and snapshots are not established.',
        {'reference_description_literal':'MouseRNAseq ImmGen databases','exact_reference_function':None,'reference_snapshot':None}),
      'spotlight':([114,119],'1.0.3',
        'Deconvolved spatial transcriptomic spots using seeded non-negative matrix factorization regression in SPOTlight 1.0.3. The first context uses a LungMap single-cell reference; the PDAC/MI context describes another single-cell reference. Both annotate spots at predicted neutrophil composition >=10%.',
        {'method':'seeded NMF regression','predicted_neutrophil_fraction_threshold':0.1,'comparator':'>=','reference_contexts':{'114':'LungMap','119':'single-cell reference described in this PDAC/MI paragraph'}}),
      'bd-rhapsody':([81,104],'1.9.1',
        'Ran the Rhapsody analysis pipeline 1.9.1 locally for mouse GRCm38/gencodevM19-20181206 alignment, quantification, automatic low-quality-cell filtering and doublet tagging/filtering. A separate context reports raw sequencing-file processing with the BD Rhapsody pipeline without a release. The subsequent manual QC and SingleR annotation are separate operations.',
        {'local_execution_at_81':True,'mouse_reference':'GRCm38','annotation_reference_literal':'gencodevM19-20181206','release_at_104':None,'manual_QC_is_separate':True})}
    for definition in definitions(root):
        tool=definition['id'];indices,version,summary,settings=facts[tool]
        anchors=[dict(paragraph_index=i,section=by_index[i]['section'],paragraph_sha256=hashlib.sha256(by_index[i]['text'].encode()).hexdigest()) for i in indices]
        rows.append(dict(paper_id='P051',pmcid=paper['pmcid'],doi=paper['doi'],tool_id=tool,primary_xml_sha256=paper['xml_sha256'],
          evidence_paragraph_index=indices[0],evidence_paragraph_sha256=anchors[0]['paragraph_sha256'],version_reported_near_name=version,
          usage_context='analysis-method-description',use_summary=summary,reported_settings=settings,supporting_evidence=anchors,
          named_anchor_classification=[dict(paragraph_index=i,kind='primary-software-analysis') for i in indices],
          uncertainties=['The exact executed source, full environment, original inputs and unreported caller settings are not established.'],
          implementation_scope_note='Missing separate software family. No runtime analysis or implementation credit. Supply the exact permitted source, prerequisites and tested workflow before using it; unavailable/forbidden requirements skip dependent analysis.'))
    registry={d['id']:d for d in definitions(root)}
    registry['singler']={'pattern':r'(?<![A-Za-z0-9_])SingleR(?![A-Za-z0-9_])'}
    for paper_id in ['P051','P082','P192','P233']:
        p=next(p for p in json.loads((root/'docs/literature/omics-2023-2026/papers.json').read_bytes()) if p['paper_id']==paper_id)
        raw=gzip.decompress((root/'build/literature-review/xml'/(p['pmcid']+'.xml.gz')).read_bytes())
        if hashlib.sha256(raw).hexdigest()!=p['xml_sha256']:raise ValueError('Original primary source differs: '+paper_id)
        validate_supporting_evidence(paragraphs(ET.fromstring(raw)),[r for r in rows if r['paper_id']==paper_id],registry)
    return rows
