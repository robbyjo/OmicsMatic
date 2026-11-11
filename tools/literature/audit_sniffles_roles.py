# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit all 250 primary articles and draft six Sniffles roles without catalog credit."""
import argparse
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

from setup_deseq2_reference import ROOT, pin, save
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort
from rsubread_catalog import load_catalog_registry

ROLES = {
    'P033': dict(version='2.0.2',
        summary='Called indels and structural variants described as larger than 20 bp with Sniffles2 2.0.2, guided by tandem-repeat annotations, within a custom local STR genotyping workflow.',
        note='The paper names --tandem-repeats but does not freeze the annotation or complete command. Its >20 bp statement alone does not establish a --minsvlen value. Clair3 short-variant calls, haplotype-specific bcftools consensus, the 50-bp flanks and Tandem Repeat Finder characterization are separate steps.'),
    'P102': dict(version='1.0.7', support=[36],
        summary='Called long-read structural variants with Sniffles 1.0.7 after NGMLR mapping to ZS11, applied the stated quality/support/size/duplicate filters, and used the Sniffles population-calling workflow after separate SURVIVOR merging.',
        note='Preserve the legacy C++ edition. The preceding paragraph describes removing UNRESOLVED/IMPRECISE calls, fewer than four supporting reads, calls shorter than 50 bp and duplicates; retain its wording without inferring unspecified field/duplicate rules. SURVIVOR 1.0.3 parameters 10 1 1 1 0 50 belong to a separate merge. The population recipe and complete commands are not frozen; Sniffles2 SNF combining is not evidence of implementing this legacy workflow.'),
    'P129': dict(version='2.0.7',
        summary='Called structural variants with Sniffles2 2.0.7 on tumor and normal ONT alignments separately, contributing to a two-of-four-caller consensus.',
        note='Sniffles was not run in paired somatic mode in this statement. SAVANA supplied the paired mode; cuteSV, SVIM and Sniffles ran separately on tumor and normal. The orientation and 1-kb padded-overlap consensus merge is separate from Sniffles. Supplementary Note 4 is referenced, and the main paragraph does not specify complete Sniffles settings.'),
    'P164': dict(version='2.0.7', support=[36],
        summary='Used default Sniffles 2.0.7 SV calls from HiFi and ONT alignments for assembly evaluation and a hybrid assembly-correction workflow, with the technologies called independently.',
        note='The polishing paragraph explicitly describes independent HiFi and ONT call sets. Do not infer pooled calling from the evaluation paragraph\'s term hybrid. Iris polishes insertion/deletion sequences, Jasmine merges the two call sets, Merfin filters, and bcftools incorporates corrections; these are separate tools, not Sniffles operations.'),
    'P247': dict(version='2.4', support=[83, 84],
        summary='Generated phased structural variants with Sniffles 2.4 from HiFi reads haplotagged after WhatsHap phasing, for comparison with Locityper and a Sniffles-plus-DeepVariant call set.',
        note='The method paragraph supplies version 2.4 beyond the results anchor. WhatsHap 2.3 performs short-variant phasing and read haplotagging; Sniffles consumes those assignments. RTG vcfmerge combines the outputs, while bcftools consensus reconstructs haplotypes and the workflow removes conflicting calls and symbolic alternatives except <DEL>. Do not attribute those operations to Sniffles or infer an unreported complete calling command.'),
    'P248': dict(version='2.3.2',
        summary='Called structural variants with Sniffles 2.3.2 on minimap2 alignments of HiFi and CLR accessions, as one of four read-based callers in an SV graph-pangenome workflow.',
        note='The stated downstream retention conditions are pass flag, read depth at least four and length below 20 kb. The source does not identify the depth field, so do not silently equate it with Sniffles SUPPORT. SURVIVOR merges five caller-derived sets using 1-kb distance and agreement by at least two callers on SV type; Syri/MUMmer assembly calls and retention of larger MUMmer SVs are separate. Sniffles settings are otherwise unreported.'),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    out = parser.parse_args().out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/sniffles-runtime')
    out.mkdir(parents=True)
    data = ROOT / 'docs/literature/omics-2023-2026'
    paths = [data / 'papers.json', data / 'tool-inventory.json', data / 'summary.json',
             ROOT / 'tools/literature/reviewed-uses.json']
    before = {p.relative_to(ROOT).as_posix(): pin(p) for p in paths}
    papers = json.loads((data / 'papers.json').read_bytes())
    verify_fixed_cohort(ROOT, data, papers)
    registry = {r['id']: r for r in load_catalog_registry(ROOT)}
    reviews = json.loads(paths[-1].read_bytes())['reviews']
    assert len(reviews) == 1003
    census, records, drafts = [], [], []
    observations = 0
    for paper in papers:
        raw = gzip.decompress((ROOT / 'build/literature-review/xml' / (paper['pmcid'] + '.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        xml = ET.fromstring(raw)
        doi = next(q.text for q in xml.findall('front/article-meta/article-id') if q.get('pub-id-type') == 'doi')
        assert doi.lower() == paper['doi'].lower()
        ps = paragraphs(xml)
        by = {p['paragraph_index']: p for p in ps}
        cache = json.loads((ROOT / 'build/literature-review/extracted' / (paper['pmcid'] + '.json')).read_bytes())
        cached = {t['tool_id']: t for t in cache['tools']}
        for tool in paper['tools']:
            anchor = by[tool['evidence_paragraph_index']]
            assert anchor['text'] == cached[tool['tool_id']]['evidence_text']
            assert hashlib.sha256(anchor['text'].encode()).hexdigest() == tool['evidence_paragraph_sha256']
            observations += 1
        matches = [p for p in ps if re.search(registry['sniffles']['pattern'], p['match_text'], re.I)]
        current = [t for t in paper['tools'] if t['tool_id'] == 'sniffles']
        assert len(current) <= 1
        census.append(dict(paper_id=paper['paper_id'], matching_paragraphs=len(matches), catalogued=bool(current)))
        if not current:
            assert not matches
            continue
        tool = current[0]
        role = ROLES[paper['paper_id']]
        assert role['version'] in ' '.join(p['text'] for p in matches)
        review = dict(pmcid=paper['pmcid'], doi=paper['doi'], tool_id='sniffles',
                      evidence_paragraph_sha256=tool['evidence_paragraph_sha256'],
                      use_summary=role['summary'], implementation_scope_note=role['note'],
                      version_reported_near_name=role['version'])
        support = []
        for index in role.get('support', []):
            paragraph = by[index]
            support.append(dict(paragraph_index=index, section=paragraph['section'],
                                paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest()))
        if support:
            review['supporting_evidence'] = support
        prior = [r for r in reviews if r['pmcid'] == paper['pmcid'] and r['tool_id'] == 'sniffles']
        assert not prior
        drafts.append(dict(paper_id=paper['paper_id'], review=review))
        def pointer(paragraph):
            return dict(paragraph_index=paragraph['paragraph_index'], section=paragraph['section'],
                        paragraph_sha256=hashlib.sha256(paragraph['text'].encode()).hexdigest())
        permissions=xml.find('front/article-meta/permissions')
        license_links=sorted({value for node in permissions.iter() for value in node.attrib.values()
                              if value.startswith('https://creativecommons.org/licenses/')}) if permissions is not None else []
        records.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'],
                            xml_sha256=paper['xml_sha256'], anchor=tool['evidence_paragraph_index'],
                            article_url='https://doi.org/'+paper['doi'], article_license_links=license_links,
                            matching_paragraphs=[pointer(p) for p in matches],
                            supporting_paragraphs=[pointer(by[i]) for i in role.get('support', [])], review=review,
                            original_paragraph_text_redistributed=False))
    assert len(papers) == 250 and observations == 2465
    assert {r['paper_id'] for r in records} == set(ROLES) and len(drafts) == 6
    assert all(pin(ROOT / p) == value for p, value in before.items())
    save(out / 'role-drafts.json', dict(status='six-primary-bound-Sniffles-role-drafts-only', reviews=drafts))
    save(out / 'source-audit.json', dict(status='read-only-six-Sniffles-primary-role-drafts',
                                       papers=records, source_census=census, catalog_changed=False,
                                       implementation_credit=False))
    save(out / 'COMPLETED.json', dict(status='passed250-primary-XML-DOI-and2465-anchors-with-six-Sniffles-drafts',
        source_papers=250, observations=2465, roles=6, prior_roles=0, new_role_drafts=6,
        supporting_pointers=sum(len(d['review'].get('supporting_evidence', [])) for d in drafts),
        catalog_before=before, role_drafts=pin(out / 'role-drafts.json'), source_audit=pin(out / 'source-audit.json'),
        catalog_changed=False, implementation_credit=False, whole_goal_complete=False,
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(),
                     ROOT / 'tools/literature/collect_omics_review.py']}))
    print('Audited 250 primary XML/DOIs and 2465 anchors; drafted six Sniffles roles. Catalog unchanged.')


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable primary-source and unchanged-catalog checks')
    main()
