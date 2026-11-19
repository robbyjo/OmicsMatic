# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Cumulative immutable Seurat predecessor and PLINK component/source bindings."""
import json
from pathlib import Path
import zipfile
from seurat_catalog import catalog as previous
from seurat_catalog import evidence as old
from plink_catalog import primary
from plink_catalog.admission_model import META, DATA
from plink_catalog.verify_components import verify as component_audit

ROOT, require, encoded = primary.ROOT, primary.require, primary.encoded
pin, rawpin, git = old.pin, old.rawpin, old.git
BASE = '9ae51b21b01967b357f932d3a76688645fad1df0'
SOURCE_PACKET = 'docs/literature/plink-source-review'
COMPONENT_PACKET = 'docs/literature/plink-component-qualification'
PROTOTYPE = ['tools/literature/plink_catalog/' + n for n in ['reviews.py', 'primary.py', 'model.py',
    'test_primary.py', 'test_model.py', 'reproduce_proposal.py', 'P114-AUTHOR-CODE.json', 'README.md']]
FOUNDATION = sorted(set(old.FOUNDATION + previous.OWN + PROTOTYPE +
    list(json.loads((ROOT / COMPONENT_PACKET / 'QUALIFICATION.json').read_bytes())['source_pins'])))


def baseline():
    before = {n: git('show', BASE + ':' + n) for n in META}
    proof = json.loads(git('show', BASE + ':' + previous.PROOF))
    require(proof['after'] == {n: rawpin(raw) for n, raw in before.items()},
            'Actual baseline differs from completed Seurat admission')
    return before


def predecessor(cache, pdf, before, out):
    path = ROOT / previous.PROOF
    require(path.read_bytes() == git('show', BASE + ':' + previous.PROOF), 'Seurat predecessor proof changed')
    proof = json.loads(path.read_bytes())
    require(proof['status'] == previous.STATUS and proof['base_commit'] == old.BASE,
            'Different Seurat predecessor')
    require(proof['source_code'] == {n: pin(ROOT / n) for n in previous.OWN}, 'Seurat admission source changed')
    for n, wanted in proof['evidence'].items():
        require(pin(ROOT / n) == wanted, 'Seurat evidence changed: ' + n)
    for n, wanted in proof['foundation_sources'].items():
        require(pin(ROOT / n) == rawpin(git('show', old.BASE + ':' + n)) == wanted,
                'Seurat foundation source changed: ' + n)
    with zipfile.ZipFile(ROOT / previous.FOLDER / 'catalog-before.zip') as archive:
        require(len(archive.namelist()) == 8 and set(archive.namelist()) == set(META), 'Incomplete Seurat before archive')
        earlier = {n: archive.read(n) for n in META}
    require(earlier == old.baseline(), 'Seurat earlier bytes differ from actual Git predecessor')
    require((ROOT / previous.FOLDER / 'PREVIOUS-SHA256SUMS').read_bytes() ==
            git('show', old.BASE + ':' + DATA + '/SHA256SUMS'), 'Seurat earlier manifest differs')
    prior = old.predecessor(cache, earlier)
    require(prior == proof['predecessor_audit'], 'Seurat METAL/LDAK chain differs')
    census = previous.primary(cache, pdf, earlier)
    require(census == json.loads((ROOT / previous.COVERAGE).read_bytes()), 'Seurat XML/PDF source census differs')
    result = previous.check(earlier, before, census)
    require(proof['before'] == {n: rawpin(b) for n, b in earlier.items()} and
            proof['after'] == {n: rawpin(b) for n, b in before.items()}, 'Seurat stage bindings differ')
    require(all(proof.get(k) == v for k, v in result.items()), 'Seurat recorded result differs')
    qa = old.verify_qualification(proof['workflow_qualification'], ROOT / previous.FOLDER / 'qualification-evidence.zip',
                                  Path(out), proof['qualification_packet'])
    from catalog_source_integrity import reverse_delta, verify_source
    compat = json.loads((ROOT / previous.COMPAT).read_bytes())
    n = 'tools/literature/reviewed-uses.json'
    require(compat['schema_version'] == 1 and len(compat['paths']) == 1 and compat['paths'][0]['path'] == n
            and reverse_delta(before[n], compat['paths'][0]) == earlier[n], 'Seurat review inverse differs')
    verify_source(ROOT, ROOT / DATA, n, rawpin(earlier[n])['sha256'])
    return dict(status='PASS_UNCHANGED_SEURAT_METAL_LDAK_ON_ACTUAL_STAGE_BYTES', proof=pin(path), result=result,
        seurat_qualification_payloads=qa['qualification_packet_payloads'],
        historical_full_marker_audit=qa['public']['historical_full_marker_audit'],
        source_xml_identities_checked=250, source_PDF_pages_checked=7,
        original_R_reexecuted=False, scientific_source_overlay_used=False)


def source_census(cache, author_archive):
    proof_path = SOURCE_PACKET + '/QUALIFICATION.json'
    require((ROOT / proof_path).read_bytes() == git('show', BASE + ':' + proof_path), 'Frozen source qualification changed')
    proof = json.loads((ROOT / proof_path).read_bytes())
    for n, wanted in proof['source_pins'].items():
        require(pin(ROOT / n) == wanted, 'Frozen source auditor changed: ' + n)
    for n, wanted in proof['artifacts'].items():
        require(pin(ROOT / SOURCE_PACKET / n) == wanted, 'Frozen source packet changed: ' + n)
    census = primary.audit(cache, author_archive)
    require(census == json.loads((ROOT / SOURCE_PACKET / 'CENSUS.json').read_bytes()), 'Fresh PLINK source census differs')
    return census


def qualification():
    names = [p.relative_to(ROOT).as_posix() for folder in [SOURCE_PACKET, COMPONENT_PACKET]
             for p in (ROOT / folder).iterdir() if p.is_file()]
    artifacts = {}
    for n in sorted(names):
        require((ROOT / n).read_bytes() == git('show', BASE + ':' + n), 'Retained qualification changed: ' + n)
        artifacts[n] = pin(ROOT / n)
    report = component_audit()
    return dict(status='PASS_SOURCE_BOUND_PLINK_FAMILY_COMPONENT_CLOSURE', component_audit=report,
        artifacts=artifacts, original_programs_reexecuted=False,
        historical_fingerprints_refreshed=False, whole_tool_qualified=False, whole_paper_reproduced=False)
