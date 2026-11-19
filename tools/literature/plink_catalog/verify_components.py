# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit retained PLINK component evidence; never run an original program."""
import argparse
import json
from pathlib import Path
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
from fixture_project import ROOT, PACKET, read_archive, fingerprint, require

STATUS = 'PASS_PLINK_COMPONENT_EVIDENCE_WITH_EXPLICIT_FROZEN_FIXTURES'
REPORTS = {
    'qc': ('component-runs-1', 'verify_plink_qc.py', 'passed', dict(negative_controls=6)),
    'score': ('component-runs-1', 'verify_genotype_score.py', 'passed',
        dict(reference_workflows=6, compared_original_fields=2007, compared_native_rows=670,
             actual_original_calls=12, packaged_java_calls=28, control_checks=22)),
    'glm': ('frozen-fixture-runs-1', 'verify_plink_upstream.py', 'passed',
        dict(workflows=20, canonical_rows=1920, frozen_report_rows=960, independent_OLS_rows=956,
             negative_controls=5, case_count=20, actual_JVMs=1, unreportable_rows=7)),
    'prune': ('frozen-fixture-runs-1', 'verify_plink_prune.py', 'passed', dict(negative_controls=12, actual_JVMs=1)),
    'pca': ('frozen-fixture-runs-1', 'verify_plink_pca.py',
        'passed-packaged-original-PCA-recipes-controls-and-independent-math', {}),
    'ci': ('frozen-fixture-runs-1', 'verify_plink_ci_upstream.py', 'passed',
        dict(api_rows=2880, typed_workflows=30, typed_rows=2880, negative_controls=7,
             suppressed_api_intervals=6, suppressed_typed_intervals=6, actual_JVMs=1)),
}


def verify(packet=PACKET):
    packet = Path(packet)
    proof = json.loads((packet / 'QUALIFICATION.json').read_bytes())
    require(proof['status'] == STATUS and proof['new_family_credit'] == 0
            and proof['full_catalog_admitted'] is False and proof['whole_goal_complete'] is False,
            'False component, catalog or goal claim')
    for name, wanted in proof['source_pins'].items():
        require(fingerprint((ROOT / name).read_bytes()) == wanted, 'Changed qualified source: ' + name)
    for name, wanted in proof['predecessor_artifacts'].items():
        require(fingerprint((ROOT / name).read_bytes()) == wanted, 'Changed predecessor artifact: ' + name)
    for name, wanted in proof['fixture_artifacts'].items():
        require(fingerprint((packet / name).read_bytes()) == wanted, 'Changed fixture packet: ' + name)
    manifest = json.loads((packet / 'FIXTURES.json').read_bytes())
    fixtures = read_archive(manifest, packet / 'fixtures.zip')
    require(len(fixtures) == 125 and len(manifest['restored']) == 41,
            'Qualified fixture population differs')
    require(sum(r['reason'] == 'exact-retained-validated-README' for r in manifest['restored']) == 1,
            'Exactly one historical documentation fixture is restored')
    archive = packet / 'evidence.zip'
    require(fingerprint(archive.read_bytes()) == proof['archive'], 'Changed component evidence archive')
    wanted = proof['members']
    files, total = {}, 0
    require(0 < len(wanted) <= 10000, 'Invalid component evidence population')
    with zipfile.ZipFile(archive) as source:
        require(len(source.namelist()) == len(set(source.namelist())) == len(wanted)
                and set(source.namelist()) == set(wanted), 'Component evidence population differs')
        for item in source.infolist():
            name = item.filename
            require(not name.startswith('/') and '..' not in Path(name).parts
                    and not any(c in name for c in '\\:\0') and not item.is_dir(), 'Unsafe evidence member')
            require(item.file_size <= 16 * 1024 * 1024, 'Oversize evidence member')
            total += item.file_size
            require(total <= 128 * 1024 * 1024, 'Oversize component evidence')
            raw = source.read(item)
            require(fingerprint(raw) == wanted[name], 'Changed component evidence member: ' + name)
            files[name] = raw
    def obj(name):
        return json.loads(files[name])
    first = obj('records/component-runs-1/COMPLETED.json')
    require(first['status'] == 'INCOMPLETE_COMPONENT_CHECKS_RETAINED' and first['all_inputs_preserved'],
            'The first fixture-identity failures must remain visible')
    require({r['component']: r['exit_code'] for r in first['records']} == dict(
        **{'native-king': 0, 'king232': 0, 'historical-king': 0, 'glm': 1, 'qc': 0,
           'prune': 1, 'pca': 1, 'ci': 1, 'score': 0}), 'Different initial component outcomes')
    frozen = obj('records/frozen-fixture-runs-1/COMPLETED.json')
    require(frozen['status'] == 'PASS_UNCHANGED_PRODUCERS_WITH_EXACT_FROZEN_FIXTURES'
            and frozen['fixture_project_unchanged'] and frozen['original_working_files_unchanged']
            and frozen['old_verifiers_or_expected_hashes_changed'] is False
            and {r['component']: r['exit_code'] for r in frozen['records']} == dict(glm=0, prune=0, pca=0, ci=0),
            'Incomplete frozen-fixture component checks')
    require(first['candidate'] == frozen['candidate'] == proof['candidate'], 'Current tested JAR differs')
    for stage in [first, frozen]:
        prefix = 'records/' + ('component-runs-1' if stage is first else 'frozen-fixture-runs-1') + '/'
        for row in stage['records']:
            for stream in ['stdout', 'stderr']:
                require(fingerprint(files[prefix + row['component'] + '.' + stream]) == row[stream],
                        'Changed recorded producer console')
    reports = {}
    for name, (stage, script, expected_status, counts) in REPORTS.items():
        report = obj('records/' + stage + '/' + name + '/verification.json')
        require(report['status'] == expected_status and report['jar_sha256'] == proof['candidate']['sha256']
                and report['producer_sha256'] == proof['source_pins']['tools/literature/' + script]['sha256']
                and all(report[k] == v for k, v in counts.items()), 'Incomplete qualified component: ' + name)
        reports[name] = report
    require(len(reports['qc']['workflows']) == 25 and len(reports['prune']['workflows']) == 7
            and len(reports['pca']['workflows']) == 11 and len(reports['pca']['negative_controls']) == 12,
            'Different workflow/control populations')
    for model in ['firth', 'firth-fallback', 'logistic']:
        options = obj('records/frozen-fixture-runs-1/glm/0-plink2-separated-' + model + '.json')
        require(options['model'] == model, 'Missing original separated/Firth comparison')
    king = obj('records/king232/COMPLETED.json')
    historical = obj('records/historical-king/COMPLETED.json')
    require(king['status'] == 'PASS_OFFLINE_FROZEN_KING232_COMPLETE_SYNTHETIC_REPLAY'
            and len(king['records']) == 26 and not king['original_KING_executed'], 'KING232 replay differs')
    require(historical['status'] == 'PASS_OFFLINE_HISTORICAL_KING_COMPLETE_SYNTHETIC_AND_EXACT_CUTOFF_REPLAY'
            and len(historical['records']) == 58 and historical['promoted_source_discrepancies_rejected']
            and not historical['original_KING_or_R_analysis_executed'], 'Historical KING rejection replay differs')
    return dict(status='PASS_RETAINED_PLINK_COMPONENT_EVIDENCE_AND_EXACT_FIXTURES',
        evidence_files=len(files), fixture_files=len(fixtures), explicit_fixture_restorations=41,
        original_programs_reexecuted_now=False, current_JAR_executed_now=False,
        current_working_checkout_legacy_verifiers_all_pass=False,
        earlier_failures_retained=True, new_family_credit=0, full_catalog_admitted=False, whole_goal_complete=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--packet', type=Path, default=PACKET)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.packet), indent=2))
    except FileNotFoundError as error:
        print('SKIP3_COMPONENT_EVIDENCE:', str(error),
              'Required action: restore the named exact source/evidence prerequisite or leave this dependent audit skipped.',
              file=sys.stderr)
        raise SystemExit(3)
    except Exception as error:
        print('FAIL2_COMPONENT_EVIDENCE:', repr(error),
              'Required action: preserve the failure, restore exact named sources/evidence and retry. No catalog admission.',
              file=sys.stderr)
        raise SystemExit(2)
