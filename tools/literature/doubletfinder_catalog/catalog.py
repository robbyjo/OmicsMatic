#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply or verify the source-bound DoubletFinder catalog admission."""
from pathlib import Path
import argparse, json, sys, traceback, zipfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from doubletfinder_catalog.model import *
from doubletfinder_catalog.bridge import load_before, metadata_view, predecessor_pins, prior_admission
from doubletfinder_catalog.source import scan, author_sources
from doubletfinder_catalog.qualification import qualification
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta

ACTION = ('Read DOUBLET_FINDER_CATALOG_INTEGRATION.md and every original warning. '
          'Use the exact permitted original 2.0.6 R provider and NEW writable paths. '
          'SKIP3 skips dependent analysis for missing/forbidden prerequisites. FAIL2 admits no complete result, including recording failures. '
          'SCT convergence is not qualified; retain all 3,706 original warnings, complete conditions and raw logs. '
          'Resolve the named cause without changing historical hashes, identifiers, NA/NaN/Inf or deposited author code. '
          'The modified HICAT 1.0.9 workflow and reported DoubletFinder 3.0 edition remain unqualified. '
          'All five new software families remain missing. This gate fits no models and installs nothing; the full 250-paper goal remains open.')


def compatibility(before, after):
    row = delta(REVIEWS, before[REVIEWS], after[REVIEWS])
    if reverse_delta(after[REVIEWS], row) != before[REVIEWS]:
        raise ValueError('Exact complete predecessor reviews cannot be recovered')
    return dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='doubletfinder-five-primary-uses-and-five-new-families',
                scope='Twelve added reviews and one confirmed Scrublet source-summary correction; all other 1,167 reviews retained. Only exact reviewed-uses.json metadata may be replayed; scientific overlays forbidden.', paths=[row])


def load_catalog_registry(root=ROOT):
    from velociraptor_catalog.ui_revision2 import load_catalog_registry as previous
    old = previous(metadata_view(root, load_before(root)))
    if len(old) != 471:
        raise ValueError('Expected all 471 cumulative predecessor definitions')
    inventory = json.loads((root / META[0]).read_bytes())
    current = next(r for r in inventory if r['id'] == 'doubletfinder')
    for row in old:
        if row['id'] == 'doubletfinder':
            for key in ['current_coverage', 'existing_source', 'coverage_note']:
                row[key] = current[key]
    ids = {r['id'] for r in old}
    for definition in definitions(root):
        if definition['id'] in ids:
            raise ValueError('New named definition duplicates an existing interface')
        old.append(definition)
        ids.add(definition['id'])
    return old


def apply(root, out, runtime, wsl_distro):
    if any((root / n).exists() for n in [EVIDENCE, PROOF, COVERAGE, COMPATIBILITY]):
        raise ValueError('Admission evidence already exists; retain it and use verification')
    before = {n: (root / n).read_bytes() for n in META}
    if {n: rawpin(v) for n, v in before.items()} != predecessor_pins(root):
        raise ValueError('Current exact predecessor differs; preserve concurrent work')
    census = scan(root, json.loads(before[PAPERS]))
    after = transition(root, before)
    result = check_transition(root, before, after)
    inverse = compatibility(before, after)
    component = qualification(root, runtime, wsl_distro)
    evidence = root / EVIDENCE
    evidence.mkdir(exist_ok=False)
    (evidence / '.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(evidence / 'catalog-before.zip', 'x', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, raw in before.items():
            info = zipfile.ZipInfo(name, (2026, 11, 14, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, raw)
    (evidence / 'CENSUS.json').write_bytes(encoded(census))
    (evidence / 'QUALIFICATION.json').write_bytes(encoded(component))
    (evidence / 'AUTHOR_SOURCE_AUDIT.json').write_bytes(encoded(author_sources(root)))
    (evidence / 'WARNINGS.md').write_bytes((WARNING + '\n' + ACTION + '\n').encode())
    (root / COMPATIBILITY).write_bytes(encoded(inverse))
    (root / COVERAGE).write_bytes(encoded(dict(status='PRIMARY_ROLES_WITH_SELECTED206_COVERAGE_AND_EXPLICIT_GAPS',
        reviews=reviews(root), paper_role_coverage=next(r for r in json.loads(after[META[0]]) if r['id'] == 'doubletfinder')['paper_role_coverage'],
        missing_new_families=[d['id'] for d in definitions(root)], selected_environment_is_author_environment=False,
        reported_3_0_qualified=False, modified_hicat_1_0_9_qualified=False, SCT_convergence_qualified=False,
        new_upstream_family_credit=1, native_family_credit_added=0, whole_goal_complete=False)))
    source_names = sorted(p.relative_to(root).as_posix() for p in (root / MODULE).iterdir() if p.is_file()) + [GUIDE]
    evidence_names = sorted(p.relative_to(root).as_posix() for p in evidence.iterdir() if p.is_file() and p.name != 'catalog-before.zip')
    evidence_names += [COVERAGE, COMPATIBILITY, DATA + '/velociraptor-catalog-validation.json', 'docs/literature/velociraptor-catalog-ui-revision2/VALIDATION.json',
                       'docs/literature/doubletfinder206-native-development/VALIDATION.json', 'docs/literature/doubletfinder206-workflow-development/VALIDATION.json']
    for name, raw in before.items():
        if (root / name).read_bytes() != raw:
            raise ValueError('Concurrent metadata edit; no overwrite permitted: ' + name)
    (out / 'PREPARED.json').write_bytes(encoded(dict(before_files={n: rawpin(v) for n, v in before.items()}, after_files={n: rawpin(v) for n, v in after.items()}, **result)))
    for name, raw in after.items():
        (root / name).write_bytes(raw)
    proof = dict(status='PASS_DOUBLET_FINDER206_SCOPED_WORKFLOW_AND_PRIMARY_CATALOG_ADMISSION',
                 before_archive=pin(evidence / 'catalog-before.zip'), before_files={n: rawpin(v) for n, v in before.items()},
                 after_files={n: pin(root / n) for n in META}, source_pins={n: pin(root / n) for n in source_names},
                 evidence_pins={n: pin(root / n) for n in evidence_names}, **result,
                 admitted_tool_ids=['doubletfinder'], new_missing_tool_ids=[d['id'] for d in definitions(root)],
                 scientific_sources_overridden=False, scientific_verifiers_changed=False)
    (root / PROOF).write_bytes(encoded(proof))
    (out / 'APPLIED.json').write_bytes(encoded(proof))
    return proof


def verify(root=ROOT, rescan=False, runtime=None, wsl_distro=None):
    proof = json.loads((root / PROOF).read_bytes())
    if proof['status'] != 'PASS_DOUBLET_FINDER206_SCOPED_WORKFLOW_AND_PRIMARY_CATALOG_ADMISSION' or proof['new_scoped_integration_credit'] != 1 or proof['native_family_credit_added'] or proof['whole_goal_complete']:
        raise ValueError('Unsupported catalog or duplicate component credit')
    for key in ['source_pins', 'evidence_pins', 'after_files']:
        for name, wanted in proof[key].items():
            if pin(root / name) != wanted:
                raise ValueError('Qualified current catalog source/evidence differs: ' + name)
    before = load_before(root)
    if pin(root / EVIDENCE / 'catalog-before.zip') != proof['before_archive'] or {n: rawpin(raw) for n, raw in before.items()} != proof['before_files']:
        raise ValueError('Exact eight-input metadata predecessor differs')
    after = {n: (root / n).read_bytes() for n in META}
    result = check_transition(root, before, after)
    if json.loads((root / COMPATIBILITY).read_bytes()) != compatibility(before, after):
        raise ValueError('Exact complete predecessor review inverse differs')
    census = json.loads((root / EVIDENCE / 'CENSUS.json').read_bytes())
    if len(census) != 250 or rescan and scan(root, json.loads(before[PAPERS])) != census:
        raise ValueError('Complete fresh 250-source census differs')
    coverage = json.loads((root / COVERAGE).read_bytes())
    if coverage['reviews'] != reviews(root) or coverage['reported_3_0_qualified'] or coverage['modified_hicat_1_0_9_qualified'] or coverage['SCT_convergence_qualified']:
        raise ValueError('Reported versions, unresolved workflows or source roles were overstated')
    previous = prior_admission(root, before, rescan=rescan)
    component = qualification(root, runtime, wsl_distro)
    registry = load_catalog_registry(root)
    if len(registry) != 476:
        raise ValueError('Complete old definitions plus five new frontends required')
    return dict(status=proof['status'], **result, registry_definitions=len(registry), primary_sources_rescanned=rescan,
                component=component, predecessor=previous['status'], scientific_sources_overridden=False,
                scientific_verifiers_changed=False, original_model_fitting_executed=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--verify', action='store_true')
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--rescan-sources', action='store_true')
    parser.add_argument('--runtime', help='Exact permitted Linux provider path; required for complete R comparisons')
    parser.add_argument('--wsl-distro', help='Explicit permitted distribution on Windows; native Windows R execution skips')
    args = parser.parse_args()
    out, owned = args.out.resolve(), False
    try:
        if not out.is_relative_to(ROOT / 'build/literature-review/monocle-runtime'):
            raise ValueError('Use a NEW monocle-runtime output')
        out.mkdir(parents=True, exist_ok=False)
        owned = True
        result = apply(ROOT, out, args.runtime, args.wsl_distro) if args.apply else verify(rescan=args.rescan_sources, runtime=args.runtime, wsl_distro=args.wsl_distro)
        (out / ('COMPLETED.json' if args.verify else 'APPLIED.json')).write_bytes(encoded(result))
        (out / 'WARNINGS.md').write_bytes((WARNING + '\n' + ACTION + '\n').encode())
        print(result['status'], flush=True)
        return 0
    except BaseException as error:
        code = 3 if isinstance(error, OSError) and not isinstance(error, FileExistsError) else 2
        status = 'SKIP3_DOUBLET_FINDER_CATALOG_PREREQUISITE_UNAVAILABLE' if code == 3 else 'FAIL2_DOUBLET_FINDER_CATALOG_UNADMITTED'
        if owned:
            (out / 'FAILURE.json').write_bytes(encoded(dict(status=status, cause=repr(error), traceback=traceback.format_exc(), required_action=ACTION)))
            (out / 'WARNINGS.md').write_bytes((status + '\n' + repr(error) + '\n' + ACTION + '\n').encode())
        print(status, repr(error), ACTION, flush=True)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
