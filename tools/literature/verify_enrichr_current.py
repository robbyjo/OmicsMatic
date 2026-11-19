"""Read exact current-service Enrichr qualification; no network/JVM execution."""
from pathlib import Path, PurePosixPath
import argparse
import csv
import hashlib
import io
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
from enrichr_workflow_transition import annotations, dispatcher, previous_abf, previous_enrichr_api

ROOT = Path(__file__).resolve().parents[2]
FOLDER = ROOT / 'docs/literature/enrichr-current-checks'
STATUS = 'PASS_ENRICHR_NORMAL_SOURCE_BUILD_WORKFLOW_AND_EXAMPLES'


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def require(value, message):
    if not value: raise ValueError(message)


def safe(name):
    p = PurePosixPath(name)
    require('\\' not in name and ':' not in name and not p.is_absolute() and p.parts
            and all(s not in ['.', '..'] for s in p.parts) and p.as_posix() == name, 'Unsafe member')
    return name


def payloads(folder):
    proof = json.loads((folder / 'QUALIFICATION.json').read_bytes())
    with zipfile.ZipFile(folder / 'evidence.zip') as z:
        require(len(z.namelist()) == len(set(z.namelist())) and set(z.namelist()) == set(proof['evidence_payloads']), 'Archive population differs')
        require(z.testzip() is None, 'Archive CRC failed')
        raw = {safe(n): z.read(n) for n in z.namelist()}
        for n, wanted in proof['evidence_payloads'].items(): require(pin(raw[n]) == wanted, 'Changed payload: ' + n)
    return proof, raw


def verify(folder=FOLDER):
    folder = Path(folder)
    record = json.loads((folder / 'READBACK.json').read_bytes())
    for n, wanted in record['artifacts'].items():
        require('/' not in n and '\\' not in n and n not in ['.', '..'], 'Unsafe artifact')
        require(pin((folder / n).read_bytes()) == wanted, 'Changed packet: ' + n)
    q, raw = payloads(folder)
    require(q['status'] == STATUS and q['new_family_credit'] == 0 and not q['historical_client_parity'], 'Wrong scope')
    for n, wanted in q['sources'].items(): require(pin((ROOT / safe(n)).read_bytes()) == wanted, 'Changed source: ' + n)
    normal = json.loads(raw['normal-tests/COMPLETED.json']); live = json.loads(raw['normal-live/COMPLETED.json'])
    examples = json.loads(raw['examples/COMPLETED.json'])
    require(normal['candidate'] == live['candidate'] == examples['candidate'] == q['candidate'], 'Candidate identities differ')
    require(normal['strict_lint_preserved'] and live['sources_unchanged'] and live['offline_windows_linux_verify']
            and live['offline_linux_replay_windows_verify'] and examples['standard_replayed_tables_exact'], 'Qualification scope differs')
    for host in ['windows21', 'windows25', 'linux21']:
        total = {k: 0 for k in ['tests', 'failures', 'errors', 'skipped']}
        for n, value in raw.items():
            if n.startswith('normal-tests/reports-' + host + '/') and n.endswith('.xml'):
                node = ET.fromstring(value)
                for k in total: total[k] += int(node.get(k, '0'))
        require(total == normal['tests'][host] == dict(tests=42, failures=0, errors=0, skipped=0), 'Test disposition differs')
    require(len(examples['processes']) == 6 and all(p['exit_code'] == 0 for p in examples['processes']), 'Example process differs')
    compared = 0
    for mode in ['standard', 'custom']:
        terms = list(csv.DictReader(io.StringIO(raw['results/' + mode + '/terms.tsv'].decode()), delimiter='\t'))
        hits = list(csv.DictReader(io.StringIO(raw['results/' + mode + '/hits.tsv'].decode()), delimiter='\t'))
        for row in [r for r in live['comparisons'] if r['mode'] == mode]:
            prefix = 'results/' + mode + '/' + row['library'] + '/'
            source = raw[prefix + 'source.json']; generated = raw[prefix + 'parsed.json']
            require(pin(source) == row['source'] and pin(generated) == row['generated'], 'Wrong raw/result identity')
            source_rows = json.loads(source, parse_float=str, parse_int=str, parse_constant=str)[row['library']]
            expected = []; et = []; eh = []
            for r in source_rows:
                expected.append(dict(rank=int(r[0]), term=r[1], p_value_token=r[2], odds_ratio_token=r[3], combined_score_token=r[4], hit_genes=r[5], adjusted_p_value_token=r[6], old_p_value_token=r[7], old_adjusted_p_value_token=r[8]))
                index = str(next(t['library_index'] for t in terms if t['library'] == row['library']))
                et.append(dict(library_index=index, library=row['library'], rank=str(int(r[0])), term=r[1], p_value_token=r[2], odds_ratio_token=r[3], combined_score_token=r[4], adjusted_p_value_token=r[6], old_p_value_token=r[7], old_adjusted_p_value_token=r[8], reported_hit_entries=str(len(r[5]))))
                eh.extend(dict(library_index=index, library=row['library'], rank=str(int(r[0])), hit_index=str(i+1), gene=g) for i,g in enumerate(r[5]))
            require(json.loads(generated)['rows'] == expected and [t for t in terms if t['library'] == row['library']] == et
                    and [t for t in hits if t['library'] == row['library']] == eh
                    and len(expected) == row['rows'], 'Exact nine-field/TSV comparison differs')
            compared += len(expected)
    require(compared == 600, 'Wrong result population')
    older, oldraw = payloads(ROOT / 'docs/literature/enrichr-workflow-checks')
    require(older['status'] == 'PASS_ENRICHR_WORKFLOW_DEVELOPMENT_HANDOVER', 'Different old development packet')
    serial = json.loads(oldraw['serial/COMPLETED.json'])
    require(len(serial['class_payloads']) == 25 and not serial['scientific_bytecode_changed'], 'Wrong annotation scope')
    for n, wanted in serial['class_payloads'].items():
        require(pin(oldraw['serial/annotated/' + n]) == pin(oldraw['serial/original/' + n]) == wanted, 'Old class identity differs')
    compatibility = dict(dispatch=dispatcher(oldraw['dispatch/JLinAlgCli.java'], (ROOT / 'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_bytes()),
                         annotations=annotations(), previous_abf=previous_abf(oldraw['dispatch/JLinAlgCli.java']), previous_api=previous_enrichr_api())
    return dict(status=STATUS, normal_tests_per_JVM=42, live_source_rows_compared=compared,
                candidate=q['candidate'], sources_checked=len(q['sources']), evidence_payloads_checked=len(raw),
                retained_development_payloads_checked=len(oldraw), compatibility=compatibility,
                historical_client_parity=False, historical_library_parity=False,
                original_author_runtime_executed=False, fresh_service_execution=False,
                fresh_JVM_execution=False, new_family_credit=0, whole_paper_reproduced=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--packet', type=Path, default=FOLDER); args = parser.parse_args()
    if args.out.exists(): print('FAIL2: choose NEW output', file=sys.stderr); return 2
    args.out.mkdir(parents=True)
    try:
        result = verify(args.packet); (args.out/'COMPLETED.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
        print(STATUS + ': saved evidence only; statistical computation is upstream'); return 0
    except Exception as e:
        code = 3 if isinstance(e, (FileNotFoundError, PermissionError)) else 2
        action = 'Restore the exact named permitted dependency/evidence and retry NEW output; retain skips/failures. Do not refresh old hashes.'
        (args.out/'FAILURE.json').write_text(json.dumps(dict(status='SKIP3' if code==3 else 'FAIL2', error=repr(e), required_action=action), indent=2)+'\n', encoding='utf-8')
        print(('SKIP3' if code==3 else 'FAIL2') + ': ' + str(e) + '\n' + action, file=sys.stderr); return code


if __name__ == '__main__': raise SystemExit(main())
