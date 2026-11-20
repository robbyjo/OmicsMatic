"""Read retained Enrichr evidence across the exact additive Scanpy CLI route.

The original Enrichr owner/reader is unchanged and is not executed on the new
dispatcher. This reader checks its immutable source/evidence pins against an
explicit inverse for that single file. No source file or module is overlaid,
rewritten or monkeypatched. Historical-owner execution remains a separate gate.
"""
from pathlib import Path
import argparse
import json
import sys
import xml.etree.ElementTree as ET

from verify_enrichr_current import payloads, pin, require, safe, STATUS as ENRICHR_STATUS
from enrichr_workflow_transition import (
    annotations, dispatcher as enrichr_dispatcher, previous_abf, previous_enrichr_api,
)

ROOT = Path(__file__).resolve().parents[2]
BASE = 'acec8eba62b9991fea10a7180e7a83b9cd039fe0'
DISPATCH = 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
BEFORE = dict(bytes=54455, sha256='94a6d56178f325716f3e2214cdc61b2b00c2edbe99e745f9d9b0c79eb00d6403')
ROUTE = (b'            if (arguments.length > 0 && arguments[0].equals("scanpy-tools"))\n'
         b'                return ScanpyWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n')
HELP = b'              java -jar omicsmatic-<version>.jar scanpy-tools --help\n'
ANCHOR = b'            if (arguments.length > 0 && arguments[0].equals("enrichr-tools"))\n'
HELP_ANCHOR = b'              java -jar omicsmatic-<version>.jar enrichr-tools --help\n'
ACTION = ('SKIP3: restore the exact named permitted readable source/evidence and retry NEW output; '
          'skip its dependent audit until restored. FAIL2: preserve changed evidence/diagnostics, '
          'correct the cause and retry NEW output. Do not refresh old hashes. '
          'This saved-proof reader does not run the historical owner or fresh scientific models.')


def dispatcher_before(after):
    """Accept exactly the reviewed three added lines and reproduce the old bytes."""
    require(after.count(ROUTE) == after.count(HELP) == 1, 'Missing/duplicate Scanpy route/help')
    before = after.replace(ROUTE, b'', 1).replace(HELP, b'', 1)
    require(pin(before) == BEFORE, 'Dispatcher differs beyond the exact three additive lines')
    require(before.count(ANCHOR) == before.count(HELP_ANCHOR) == 1, 'Ambiguous original anchors')
    require(after == before.replace(ANCHOR, ROUTE + ANCHOR, 1)
            .replace(HELP_ANCHOR, HELP + HELP_ANCHOR, 1), 'Scanpy route/help inserted outside reviewed anchors')
    return before


def verify():
    current = (ROOT / DISPATCH).read_bytes()
    before = dispatcher_before(current)
    folder = ROOT / 'docs/literature/enrichr-current-checks'
    readback = json.loads((folder / 'READBACK.json').read_bytes())
    for name, wanted in readback['artifacts'].items():
        require('/' not in name and '\\' not in name and name not in ['.', '..'], 'Unsafe artifact')
        require(pin((folder / name).read_bytes()) == wanted, 'Changed original Enrichr packet: ' + name)
    proof, raw = payloads(folder)
    require(proof['status'] == ENRICHR_STATUS and proof['new_family_credit'] == 0
            and not proof['historical_client_parity'], 'Wrong original Enrichr scope')
    require(DISPATCH in proof['sources'], 'Original dispatcher pin missing')
    for name, wanted in proof['sources'].items():
        source = before if name == DISPATCH else (ROOT / safe(name)).read_bytes()
        require(pin(source) == wanted, 'Changed retained source outside explicit dispatcher inverse: ' + name)
    normal = json.loads(raw['normal-tests/COMPLETED.json'])
    live = json.loads(raw['normal-live/COMPLETED.json'])
    examples = json.loads(raw['examples/COMPLETED.json'])
    require(normal['candidate'] == live['candidate'] == examples['candidate'] == proof['candidate'],
            'Retained Enrichr candidate identities differ')
    for host in ['windows21', 'windows25', 'linux21']:
        suites = [ET.fromstring(value) for name, value in raw.items()
                  if name.startswith('normal-tests/reports-' + host + '/') and name.endswith('.xml')]
        totals = {key: sum(int(s.get(key, 0)) for s in suites)
                  for key in ['tests', 'failures', 'errors', 'skipped']}
        require(totals == normal['tests'][host] == dict(tests=42, failures=0, errors=0, skipped=0),
                'Saved original Enrichr JVM disposition differs')
    require(sum(row['rows'] for row in live['comparisons']) == 600, 'Retained live row count differs')
    require(normal['strict_lint_preserved'] and live['sources_unchanged'] and
            live['offline_windows_linux_verify'] and live['offline_linux_replay_windows_verify']
            and examples['standard_replayed_tables_exact'], 'Retained qualification scope differs')
    older, oldraw = payloads(ROOT / 'docs/literature/enrichr-workflow-checks')
    require(older['status'] == 'PASS_ENRICHR_WORKFLOW_DEVELOPMENT_HANDOVER', 'Wrong development evidence')
    serial = json.loads(oldraw['serial/COMPLETED.json'])
    require(len(serial['class_payloads']) == 25 and not serial['scientific_bytecode_changed'], 'Wrong serial scope')
    for name, wanted in serial['class_payloads'].items():
        require(pin(oldraw['serial/annotated/' + name]) == pin(oldraw['serial/original/' + name]) == wanted,
                'Retained class bytes changed: ' + name)
    compatibility = dict(
        preceding_enrichr_dispatch=enrichr_dispatcher(oldraw['dispatch/JLinAlgCli.java'], before),
        annotations=annotations(), previous_abf=previous_abf(oldraw['dispatch/JLinAlgCli.java']),
        previous_enrichr_api=previous_enrichr_api())
    return dict(status='PASS_SCANPY_EXACT_DISPATCH_INVERSE_AND_RETAINED_ENRICHR_PROOF',
                base_commit=BASE, dispatcher=dict(path=DISPATCH, before=pin(before), after=pin(current),
                added_lines=3, removed_old_lines=0), source_pins_checked=len(proof['sources']),
                saved_payloads_checked=len(raw), development_payloads_checked=len(oldraw),
                retained_tests_per_JVM=42, retained_live_rows_recorded=600,
                original_reader_or_owner_modified=False, original_reader_or_owner_executed=False,
                original_reader_accepts_current_dispatcher=False,
                saved_live_numeric_comparisons_reexecuted=False,
                old_fingerprints_refreshed=False, scientific_source_overlay=False,
                whole_program_semantic_equivalence_claimed=False, fresh_JVM_execution=False,
                fresh_service_execution=False, compatibility=compatibility, new_family_credit=0,
                whole_goal_complete=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if args.out.exists():
        print('FAIL2: preserve existing evidence and choose NEW output. ' + ACTION, file=sys.stderr)
        return 2
    args.out.mkdir(parents=True)
    try:
        result = verify()
        (args.out / 'COMPLETED.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print(result['status']); return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        result = dict(status='SKIP3' if code == 3 else 'FAIL2', error=str(error),
                      required_action=ACTION, results_admitted=False)
        (args.out / 'FAILURE.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print(result['status'] + ': ' + str(error) + '\n' + ACTION, file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
