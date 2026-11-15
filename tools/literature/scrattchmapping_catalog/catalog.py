#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply or verify the scoped mapping catalog admission with exact historical metadata."""
from pathlib import Path
import argparse, json, sys, traceback, zipfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from scrattchmapping_catalog.model import *
from scrattchmapping_catalog.bridge import load_before, metadata_view, predecessor_pins, prior_admission
from scrattchmapping_catalog.source import scan
from scrattchmapping_catalog.qualification import qualification
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta

ACTION = ('Read SCRATTCH_MAPPING02_CATALOG_INTEGRATION.md and all original warnings. Use exact permitted mapping and preceding DoubletFinder providers and NEW writable paths. '
 'SKIP3 skips dependent analysis for unavailable/forbidden prerequisites. FAIL2 admits no complete result, including recording failure. '
 'Preserve original inputs, identifiers, NA/NaN/Inf, complete RDS objects, conditions, raw logs, partials and historical hashes. '
 'P058 edition stays unknown; P059 v0.2 does not establish its exact calls or metric. Complete saved conditions are compared without claiming live C pointer identity. '
 'HICAT/BIGCAT and R/Python Arrow remain separate missing interfaces; dependency installation adds no family credit. '
 'Cell Ranger/ARC stay excluded. DoubletFinder SCT convergence and its reported 3.0 edition remain unqualified. '
 'This gate fits no models and installs nothing. The full 250-paper objective remains open.')


def compatibility(before, after):
    row = delta(REVIEWS, before[REVIEWS], after[REVIEWS])
    if reverse_delta(after[REVIEWS], row) != before[REVIEWS]:
        raise ValueError('Exact preceding reviewed-use bytes cannot be recovered')
    return dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='scrattchmapping02-scoped-prepared-reference-coverage',
                scope='Two existing implementation coverage notes only; all primary facts and 1178 other reviews unchanged. Only exact reviewed-uses metadata is reversible; scientific overlays forbidden.', paths=[row])


def load_catalog_registry(root=ROOT):
    from doubletfinder_catalog.catalog import load_catalog_registry as previous
    old = previous(metadata_view(root, load_before(root)))
    if len(old) != 476:
        raise ValueError('All 476 preceding cumulative definitions required')
    current = next(row for row in json.loads((root / META[0]).read_bytes()) if row['id'] == TOOL)
    indices = [i for i, row in enumerate(old) if row['id'] == TOOL]
    if len(indices) != 1:
        raise ValueError('Exactly one existing mapping interface required')
    for key in ['current_coverage', 'existing_source', 'coverage_note']:
        old[indices[0]][key] = current[key]
    return old


def apply(root, out, runtime, wsl_distro):
    if any((root / name).exists() for name in [EVIDENCE, PROOF, COVERAGE, COMPATIBILITY]):
        raise ValueError('Admission evidence already exists; retain it and use verification')
    before = {name: (root / name).read_bytes() for name in META}
    if {name: rawpin(raw) for name, raw in before.items()} != predecessor_pins(root):
        raise ValueError('Exact preceding metadata differs; preserve concurrent work')
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
    (evidence / 'WARNINGS.md').write_bytes((WARNING + '\n' + ACTION + '\n').encode())
    (root / COMPATIBILITY).write_bytes(encoded(inverse))
    coverage = dict(status='PRIMARY_MAPPING_ROLES_WITH_SELECTED02_PREPARED_REFERENCE_COVERAGE', reviews=reviews(root),
        paper_role_coverage=next(row for row in json.loads(after[META[0]]) if row['id'] == TOOL)['paper_role_coverage'],
        P058_edition_unknown_preserved=True, P059_reported_edition='0.2', author_calls_and_metric_unreported=True,
        scientific_reference_discovery_qualified=False, MapMyCells_service_qualified=False,
        selected_environment_is_author_environment=False, live_execution_context_identity_claimed=False,
        new_upstream_family_credit=1, native_family_credit_added=0, whole_goal_complete=False)
    (root / COVERAGE).write_bytes(encoded(coverage))
    source_names = sorted(p.relative_to(root).as_posix() for p in (root / MODULE).iterdir() if p.is_file()) + [GUIDE, 'tools/literature/scrattchmapping02/verify_checkpoint.py']
    evidence_names = sorted(p.relative_to(root).as_posix() for p in evidence.iterdir() if p.is_file() and p.name != 'catalog-before.zip')
    evidence_names += [COVERAGE, COMPATIBILITY, DATA + '/doubletfinder-catalog-validation.json',
                      'docs/literature/scrattchmapping02-workflow-validation/QUALIFICATION.json',
                      'docs/literature/scrattchmapping02-workflow-validation/original-runtime-and-workflow-evidence.zip']
    for name, raw in before.items():
        if (root / name).read_bytes() != raw:
            raise ValueError('Concurrent metadata edit; no overwrite permitted: ' + name)
    (out / 'PREPARED.json').write_bytes(encoded(dict(before_files={n: rawpin(v) for n, v in before.items()}, after_files={n: rawpin(v) for n, v in after.items()}, **result)))
    for name, raw in after.items():
        (root / name).write_bytes(raw)
    proof = dict(status='PASS_MAPPING02_SCOPED_WORKFLOW_AND_PRIMARY_CATALOG_ADMISSION',
        before_archive=pin(evidence / 'catalog-before.zip'), before_files={n: rawpin(v) for n, v in before.items()},
        after_files={n: pin(root / n) for n in META}, source_pins={n: pin(root / n) for n in source_names},
        evidence_pins={n: pin(root / n) for n in evidence_names}, admitted_tool_ids=[TOOL], **result,
        scientific_sources_overridden=False, preceding_scientific_verifiers_changed=False)
    (root / PROOF).write_bytes(encoded(proof))
    return proof


def verify(root=ROOT, rescan=False, runtime=None, prior_runtime=None, wsl_distro=None):
    proof = json.loads((root / PROOF).read_bytes())
    if proof['status'] != 'PASS_MAPPING02_SCOPED_WORKFLOW_AND_PRIMARY_CATALOG_ADMISSION' or proof['new_scoped_integration_credit'] != 1 or proof['native_family_credit_added']:
        raise ValueError('Unsupported or duplicate mapping family credit')
    for key in ['source_pins', 'evidence_pins', 'after_files']:
        for name, expected in proof[key].items():
            if pin(root / name) != expected:
                raise ValueError('Qualified catalog source/evidence differs: ' + name)
    before = load_before(root)
    if pin(root / EVIDENCE / 'catalog-before.zip') != proof['before_archive'] or {n: rawpin(v) for n, v in before.items()} != proof['before_files']:
        raise ValueError('Exact eight-input historical catalog differs')
    after = {name: (root / name).read_bytes() for name in META}
    result = check_transition(root, before, after)
    if json.loads((root / COMPATIBILITY).read_bytes()) != compatibility(before, after):
        raise ValueError('Exact two-note historical inverse differs')
    census = json.loads((root / EVIDENCE / 'CENSUS.json').read_bytes())
    if len(census) != 250 or rescan and scan(root, json.loads(before[PAPERS])) != census:
        raise ValueError('Complete primary source census differs')
    coverage = json.loads((root / COVERAGE).read_bytes())
    if coverage['reviews'] != reviews(root) or not coverage['P058_edition_unknown_preserved'] or coverage['P059_reported_edition'] != '0.2':
        raise ValueError('Primary source edition/role claims changed')
    if coverage['scientific_reference_discovery_qualified'] or coverage['MapMyCells_service_qualified'] or coverage['selected_environment_is_author_environment'] or coverage['live_execution_context_identity_claimed']:
        raise ValueError('Unsupported source, service, environment or live-context claim')
    previous = prior_admission(root, before, rescan=rescan, runtime=prior_runtime, wsl_distro=wsl_distro)
    component = qualification(root, runtime, wsl_distro)
    registry = load_catalog_registry(root)
    if len(registry) != 476:
        raise ValueError('Complete cumulative registry population changed')
    return dict(status=proof['status'], **result, registry_definitions=len(registry), primary_sources_rescanned=rescan,
        component=component, predecessor=previous['status'], scientific_sources_overridden=False,
        preceding_scientific_verifiers_changed=False, original_model_fitting_executed=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--verify', action='store_true')
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--rescan-sources', action='store_true')
    parser.add_argument('--runtime', help='Exact permitted mapping R provider')
    parser.add_argument('--prior-runtime', help='Exact permitted preceding DoubletFinder R provider for all cumulative gates')
    parser.add_argument('--wsl-distro', help='Explicit permitted WSL distribution on Windows')
    args = parser.parse_args()
    out, owned = args.out.resolve(), False
    try:
        if not out.is_relative_to(ROOT / 'build/literature-review/monocle-runtime'):
            raise ValueError('Use a NEW monocle-runtime output')
        out.mkdir(parents=True, exist_ok=False)
        owned = True
        result = apply(ROOT, out, args.runtime, args.wsl_distro) if args.apply else verify(rescan=args.rescan_sources, runtime=args.runtime, prior_runtime=args.prior_runtime, wsl_distro=args.wsl_distro)
        (out / ('APPLIED.json' if args.apply else 'COMPLETED.json')).write_bytes(encoded(result))
        (out / 'WARNINGS.md').write_bytes((WARNING + '\n' + ACTION + '\n').encode())
        print(result['status'], flush=True)
        return 0
    except BaseException as error:
        code = 3 if isinstance(error, OSError) and not isinstance(error, FileExistsError) else 2
        status = 'SKIP3_MAPPING_CATALOG_PREREQUISITE_UNAVAILABLE' if code == 3 else 'FAIL2_MAPPING_CATALOG_UNADMITTED'
        print(status, repr(error), ACTION, flush=True)
        if owned:
            try:
                (out / 'FAILURE.json').write_bytes(encoded(dict(status=status, cause=repr(error), traceback=traceback.format_exc(), required_action=ACTION)))
                (out / 'WARNINGS.md').write_bytes((status + '\n' + repr(error) + '\n' + ACTION + '\n').encode())
            except BaseException as recording:
                code = 2
                print('FAIL2_CATALOG_RECORDING_UNAVAILABLE', repr(recording), ACTION, flush=True)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
