#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Plan, admit and verify 50 Seurat source roles with exact cumulative evidence."""
import argparse
import json
from pathlib import Path
import os
import sys
import traceback
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[3]
sys.dont_write_bytecode = True
sys.path.insert(0, str(ROOT / 'tools/literature'))
if not __debug__:
    print('FAIL2: rerun without Python -O; retained scientific assertions must remain enabled.', file=sys.stderr)
    raise SystemExit(2)
from common_statistics.prism_catalog import manifest_audit
from seurat_catalog.model import META, DATA, COVERAGE, GUIDE, NOTE, ACTION, registry, transition, check
from seurat_catalog.primary import audit as source_audit, encoded, require
from seurat_catalog.evidence import (BASE, FOUNDATION, baseline, predecessor, qualification,
    verify_qualification, packet_read, packet_write, pin, rawpin, git, rna)

FOLDER = 'docs/literature/seurat-catalog-admission'
PROOF = DATA + '/seurat-catalog-validation.json'
COMPAT = DATA + '/seurat-catalog-shared-source-compatibility.json'
OWN = ['tools/literature/seurat_catalog/' + n for n in [
    'primary.py', 'model.py', 'reviews.py', 'P160-SUPPLEMENT.json', 'test_catalog.py',
    'README.md', 'evidence.py', 'catalog.py', 'test_admission.py']]
STATUS = 'PASS_SEURAT_CUMULATIVE_ADMISSION_50_SOURCE_ROLES_SCOPED_RNA_WITH_HISTORICAL_AUDIT_SKIPPED'
HISTORICAL_WARNING = ('SKIP3: the two historical full marker-verifier mains were skipped because their exact '
    'old canonical build/cli/omicsmatic-0.3.7.jar is unavailable here (164898617 bytes; SHA-256 '
    '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2). '
    'Restore that exact original file to rerun them, or leave them skipped. The published v0.3.7 JAR '
    'has different bytes. Neither the old expected hash nor verifier source was changed. '
    'Current RNA/native audits, complete retained marker-archive/source checks and exact frozen '
    'marker-method payload identity in the current JAR are separately checked; no fresh historical '
    'full-verifier success or R refit is claimed.')


def primary(cache, supplement_pdf, before):
    from metal_catalog.model import registry as old_registry
    definition = next(t for t in old_registry() if t['id'] == 'seurat')
    return source_audit(cache, json.loads(before[META[0]]), definition, supplement_pdf)


def clean_manifest():
    result = manifest_audit()
    require(not result['mismatches'] and not result['missing_entries'] and not result['extra_entries'],
            'Current catalog manifest differs')
    return result


def manifest_bytes(updates):
    """Legacy refresh semantics on a virtual after-state, before any mutation."""
    data = ROOT / DATA
    old = (data / 'SHA256SUMS').read_text().splitlines()
    files = {p.name: rawpin(updates.get(p.relative_to(ROOT).as_posix(), p.read_bytes()))['sha256']
             for p in data.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
    for name, raw in updates.items():
        if Path(name).parent.as_posix() == DATA and Path(name).name != 'SHA256SUMS':
            files[Path(name).name] = rawpin(raw)['sha256']
    order = []
    for line in old:
        _, name = line.split('  ', 1)
        order.append(name)
        if name not in files:
            target = (data / name).resolve()
            require(name.startswith('../') and target.is_relative_to(ROOT / 'docs/literature'),
                    'Invalid retained external manifest member')
            files[name] = pin(target)['sha256']
    require(len(order) == len(set(order)) and set(order) <= set(files), 'Manifest loses prior entries')
    order += sorted(set(files) - set(order))
    return ''.join(files[n] + '  ' + n + '\n' for n in order).encode()


def install(root, out, expected, updates, post_write):
    """Own exact writes, retain failed new files, and preserve concurrent edits."""
    root, out = Path(root).resolve(), Path(out).resolve()
    require(set(expected) == set(updates), 'Every owned write needs its predecessor identity')
    targets = {n: root / n for n in updates}
    for name, path in targets.items():
        require(path.resolve().is_relative_to(root) and path != root, 'Metadata target escapes workspace')
        actual = path.read_bytes() if path.exists() else None
        require(actual == expected[name], 'Metadata changed before write: ' + name)
    written = {}
    try:
        for name, raw in updates.items():
            path = targets[name]
            require((path.read_bytes() if path.exists() else None) == expected[name],
                    'Concurrent metadata change: ' + name)
            temporary = out / 'owned-temporary' / (uuid.uuid4().hex + '.tmp')
            temporary.parent.mkdir(parents=True, exist_ok=True)
            written[name] = raw
            with temporary.open('xb') as stream:
                stream.write(raw)
            # Recheck immediately before replacing the owned destination.
            require((path.read_bytes() if path.exists() else None) == expected[name],
                    'Concurrent metadata change before replace: ' + name)
            os.replace(temporary, path)
        return post_write()
    except Exception:
        restored, retained, concurrent = [], [], []
        for name, raw in reversed(list(written.items())):
            path = targets[name]
            if not path.exists() or path.read_bytes() != raw:
                concurrent.append(name)
                continue
            old = expected[name]
            if old is None:
                dest = out / 'retained-failed-metadata' / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                require(not dest.exists(), 'Failed metadata retention destination exists')
                path.rename(dest)
                retained.append(name)
            else:
                path.write_bytes(old)
                restored.append(name)
        (out / 'ROLLBACK.json').write_bytes(encoded(dict(restored=restored,
            failed_new_metadata_retained=retained, concurrent_changes_preserved=concurrent)))
        raise


def verify(cache, supplement_pdf, out, candidate=None, parent=None, java=None):
    proof = json.loads((ROOT / PROOF).read_bytes())
    require(proof['status'] == STATUS and proof['base_commit'] == BASE, 'Different or missing Seurat admission')
    require(proof['source_code'] == {n: pin(ROOT / n) for n in OWN}, 'Admitted verifier source changed')
    for n, wanted in proof['evidence'].items():
        require(pin(ROOT / n) == wanted, 'Seurat admission evidence changed: ' + n)
    for n, wanted in proof['foundation_sources'].items():
        require(pin(ROOT / n) == rawpin(git('show', BASE + ':' + n)) == wanted,
                'Preserved source foundation changed: ' + n)
    with zipfile.ZipFile(ROOT / FOLDER / 'catalog-before.zip') as archive:
        require(len(archive.namelist()) == 8 and set(archive.namelist()) == set(META), 'Incomplete Seurat before archive')
        before = {n: archive.read(n) for n in META}
    require(before == baseline(), 'Retained Seurat predecessor differs from actual Git bytes')
    require((ROOT / FOLDER / 'PREVIOUS-SHA256SUMS').read_bytes() ==
            git('show', BASE + ':' + DATA + '/SHA256SUMS'), 'Predecessor manifest differs')
    require(json.loads((ROOT / FOLDER / 'REGISTRY.json').read_bytes()) == registry(), 'Seurat registry differs')
    prior = predecessor(cache, before)
    require(prior == proof['predecessor_audit'], 'Unchanged cumulative predecessor audit differs')
    qa = verify_qualification(proof['workflow_qualification'], ROOT / FOLDER / 'qualification-evidence.zip',
                              out, proof['qualification_packet'])
    census = primary(cache, supplement_pdf, before)
    require(census == json.loads((ROOT / COVERAGE).read_bytes()), 'Current XML/PDF role census differs')
    after = {n: (ROOT / n).read_bytes() for n in META}
    result = check(before, after, census)
    require(proof['before'] == {n: rawpin(b) for n, b in before.items()} and
            proof['after'] == {n: rawpin(b) for n, b in after.items()}, 'Metadata fingerprints differ')
    require(all(proof.get(k) == v for k, v in result.items()), 'Recorded Seurat counts differ')
    from catalog_source_integrity import reverse_delta, verify_source
    compat = json.loads((ROOT / COMPAT).read_bytes())
    require(compat['schema_version'] == 1 and compat['status'] == 'passed-dispatch-catalog-only-source-deltas'
            and len(compat['paths']) == 1, 'Seurat compatibility scope differs')
    name = 'tools/literature/reviewed-uses.json'
    require(compat['paths'][0]['path'] == name and reverse_delta(after[name], compat['paths'][0]) == before[name],
            'Seurat review compatibility inverse differs')
    verify_source(ROOT, ROOT / DATA, name, rawpin(before[name])['sha256'])
    current_reader = dict(java_capture_verifications_now=0, prior_JAR_members_checked_now=0)
    if any(p is not None for p in [candidate, parent, java]):
        require(all(p is not None for p in [candidate, parent, java]), 'Supply candidate, parent and Java together')
        folder = out / 'current-reader'
        folder.mkdir()
        current_reader = rna.audit(folder, candidate, parent, java)
        require(pin(candidate) == proof['workflow_qualification']['reader']
                and pin(parent) == proof['workflow_qualification']['parent'], 'Current reader lineage differs')
    report = dict(status=STATUS, **result, current_manifest=clean_manifest(),
                  source_xml_hashes_checked=250, source_PDF_pages_checked=7,
                  qualification_packet_payloads=qa['qualification_packet_payloads'],
                  current_reader=current_reader, historical_full_marker_audit=qa['public']['historical_full_marker_audit'],
                  original_R_reexecuted=False, scientific_engines_changed=False,
                  earlier_sources_or_verifiers_overlaid=False)
    (out / 'COMPLETED.json').write_bytes(encoded(report))
    return report


def admit(args, out):
    before = baseline()
    require(all((ROOT / n).read_bytes() == raw for n, raw in before.items()),
            'Canonical metadata differs from exact predecessor; preserve current edits')
    old_manifest = clean_manifest()
    foundation = {n: pin(ROOT / n) for n in FOUNDATION}
    for n, wanted in foundation.items():
        require(wanted == rawpin(git('show', BASE + ':' + n)), 'Preserved foundation changed: ' + n)
    prior = predecessor(args.primary_cache, before)
    census = primary(args.primary_cache, args.supplement_pdf, before)
    q, payload = qualification(out, args.candidate_jar, args.parent_jar, args.java, args.marker_artifact_root)
    after = transition(before, census)
    result = check(before, after, census)
    for n, obj in [('PLAN.json', result), ('SOURCE-CENSUS.json', census), ('QUALIFICATION.json', q),
                   ('PREDECESSOR-CHECK.json', prior)]:
        (out / n).write_bytes(encoded(obj))
    for n, raw in after.items():
        target = out / 'preview' / n
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
    if not args.apply:
        (out / 'COMPLETED.json').write_bytes(encoded(dict(status='PASS_REVIEWABLE_SEURAT_ADMISSION_PLAN', **result)))
        return result
    require((ROOT / GUIDE).is_file(), 'Complete the public source crosswalk before admission')
    folder = ROOT / FOLDER
    require(not folder.exists() and all(not (ROOT / n).exists() for n in [PROOF, COVERAGE, COMPAT]),
            'Admission paths exist; preserve them and use a separate transition')
    folder.mkdir()
    (folder / '.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(folder / 'catalog-before.zip', 'x', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for n, raw in before.items():
            archive.writestr(n, raw)
    (folder / 'PREVIOUS-SHA256SUMS').write_bytes((ROOT / DATA / 'SHA256SUMS').read_bytes())
    (folder / 'REGISTRY.json').write_bytes(encoded(registry()))
    (folder / 'PREDECESSOR-CHECK.json').write_bytes(encoded(prior))
    (folder / 'WARNINGS.md').write_bytes(('# Seurat admission warnings and required actions\n\n' +
        HISTORICAL_WARNING + '\n\n' + NOTE + '\n\n' + ACTION +
        '\n\nThe packet contains selected project sources, method payloads and receipts, not full source papers, the author PDF, complete runtimes or a biological validation.\n').encode())
    packet = packet_write(folder, payload)
    with zipfile.ZipFile(folder / 'verifier-source.zip', 'x', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for n in OWN:
            archive.writestr(n, (ROOT / n).read_bytes())
    from wire_signac_catalog import delta
    from catalog_source_integrity import reverse_delta
    name = 'tools/literature/reviewed-uses.json'
    change = delta(name, before[name], after[name])
    require(reverse_delta(after[name], change) == before[name], 'Review inverse differs')
    extras = {COVERAGE: encoded(census), COMPAT: encoded(dict(schema_version=1,
        status='passed-dispatch-catalog-only-source-deltas', tool_id='seurat',
        scope='49 source-reviewed appends including one PDF-backed new observation. Exact review-JSON inverse only; no scientific source overlay or historical hash refresh.',
        paths=[change]))}
    proof = dict(status=STATUS, **result, base_commit=BASE,
        source_code={n: pin(ROOT / n) for n in OWN}, foundation_sources=foundation,
        before={n: rawpin(raw) for n, raw in before.items()}, after={n: rawpin(raw) for n, raw in after.items()},
        predecessor_audit=prior, workflow_qualification=q, qualification_packet=packet,
        predecessor_current_manifest=old_manifest,
        evidence={p.relative_to(ROOT).as_posix(): pin(p) for p in folder.rglob('*') if p.is_file()},
        historical_full_marker_audit=q['public']['historical_full_marker_audit'],
        scientific_engines_changed=False, historical_fingerprints_refreshed=False,
        earlier_sources_or_verifiers_overlaid=False, website_deployed=False)
    proof['evidence'].update({n: rawpin(raw) for n, raw in extras.items()})
    proof['evidence'][GUIDE] = pin(ROOT / GUIDE)
    extras[PROOF] = encoded(proof)
    updates = {**after, **extras}
    updates[DATA + '/SHA256SUMS'] = manifest_bytes(updates)
    expected = {**before, **{n: None for n in extras}, DATA + '/SHA256SUMS': (ROOT / DATA / 'SHA256SUMS').read_bytes()}
    require(clean_manifest() == old_manifest, 'Manifest changed before admission')
    def post():
        check(before, {n: (ROOT / n).read_bytes() for n in META}, census)
        return clean_manifest()
    audit = install(ROOT, out, expected, updates, post)
    (out / 'COMPLETED.json').write_bytes(encoded(dict(status=STATUS, **result, current_manifest=audit)))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION + ' ' + HISTORICAL_WARNING)
    mode = parser.add_mutually_exclusive_group(required=True)
    for name in ['plan', 'apply', 'verify']:
        mode.add_argument('--' + name, action='store_true')
    parser.add_argument('--primary-cache', type=Path, required=True)
    parser.add_argument('--supplement-pdf', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--candidate-jar', type=Path)
    parser.add_argument('--parent-jar', type=Path)
    parser.add_argument('--java', type=Path)
    parser.add_argument('--marker-artifact-root', type=Path, default=ROOT)
    args = parser.parse_args()
    out = None
    try:
        require(sys.version_info >= (3, 11), 'Python 3.11+ is required')
        target = args.out.resolve()
        require(target.is_relative_to(ROOT / 'build') and target != ROOT / 'build', 'Choose NEW workspace build output')
        target.mkdir(parents=True, exist_ok=False)
        out = target
        result = (verify(args.primary_cache, args.supplement_pdf, out, args.candidate_jar, args.parent_jar, args.java)
                  if args.verify else admit(args, out))
        print('WARNING: ' + HISTORICAL_WARNING, file=sys.stderr, flush=True)
        print(json.dumps(result), flush=True)
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        failure = dict(status='SKIP3_DEPENDENT_ADMISSION_SKIPPED' if code == 3 else 'FAIL2_NO_COMPLETE_ADMISSION',
                       error=repr(error), detail=traceback.format_exc(), required_action=ACTION,
                       historical_audit_warning=HISTORICAL_WARNING)
        if out is not None:
            (out / 'FAILURE.json').write_bytes(encoded(failure))
        print(failure['status'] + ': ' + failure['detail'] + '\n' + ACTION, file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
