# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain private primary-method context for every Monocle observation; award no tool credit."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs
from catalog_fixed_cohort import verify_fixed_cohort
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists()
    out.mkdir(parents=True)
    data = ROOT/'docs/literature/omics-2023-2026'
    papers = json.loads((data/'papers.json').read_bytes())
    verify_fixed_cohort(ROOT, data, papers)
    checked = 0
    records = []
    for paper in papers:
        raw = gzip.decompress((ROOT/'build/literature-review/xml'/(paper['pmcid']+'.xml.gz')).read_bytes())
        assert hashlib.sha256(raw).hexdigest() == paper['xml_sha256']
        checked += 1
        uses = [t for t in paper['tools'] if t['tool_id'] == 'monocle']
        if not uses:
            continue
        assert len(uses) == 1
        use = uses[0]
        ps = paragraphs(ET.fromstring(raw))
        primary = next(p for p in ps if p['paragraph_index'] == use['evidence_paragraph_index'])
        assert hashlib.sha256(primary['text'].encode('utf8')).hexdigest() == use['evidence_paragraph_sha256']
        context = [p for p in ps if abs(p['paragraph_index']-primary['paragraph_index']) <= 2 or 'monocle' in p['match_text'].lower()]
        record = dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'], doi=paper['doi'], title=paper['title'],
            xml_sha256=paper['xml_sha256'], original_observation=use, primary=primary, private_context=context)
        save(out/(paper['paper_id']+'.json'), record)
        records.append(dict(paper_id=paper['paper_id'], pmcid=paper['pmcid'],
            paragraph_index=primary['paragraph_index'], paragraph_sha256=use['evidence_paragraph_sha256'],
            private_record=pin(out/(paper['paper_id']+'.json'))))
    assert checked == 250 and len(records) == 9
    save(out/'COMPLETED.json', dict(status='checked-nine-Monocle-primary-context-records', source_xml_hashes_checked=checked,
        records=records, source_pins={'tools/literature/audit_monocle_roles.py': pin(Path(__file__))},
        papers_pin=pin(data/'papers.json'), implementation_credit=False, private_fulltext_context_not_for_public_archive=True,
        whole_goal_complete=False))
    print(json.dumps(dict(status='checked-nine-Monocle-primary-context-records', source_xml_hashes_checked=checked, records=len(records), implementation_credit=False)))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable primary-source checks')
    main()
