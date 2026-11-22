"""Read-only admission gate for frozen original service and fresh offline CLI evidence."""
from pathlib import Path
import hashlib
import json
import zipfile
from gprofiler_workflow_qualification.verify_checkpoint import ROOT, verify as protocol_verify, pin, require

FOLDER = 'docs/literature/gprofiler-protocol-validation'


def verify(jar=None):
    report = protocol_verify(jar)
    folder = ROOT / FOLDER
    status = json.loads((folder / 'STATUS.json').read_bytes())
    require(status['status'] == 'PASS_GPROFILER_FRESH_OFFLINE_PUBLIC_CLI_AND_PROTOCOL_AUDIT', 'Wrong qualification gate')
    for name, identity in status['sources'].items():
        require(pin((ROOT / name).read_bytes()) == identity, 'Qualification reader source changed: ' + name)
    require(pin((folder / 'evidence.zip').read_bytes()) == status['zip'], 'Fresh qualification ZIP changed')
    with zipfile.ZipFile(folder / 'evidence.zip') as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())) and set(archive.namelist()) == set(status['files']),
                'Fresh qualification file inventory differs')
        files = {n: archive.read(n) for n in archive.namelist()}
    require(all(pin(raw) == status['files'][name] for name, raw in files.items()), 'Fresh qualification object differs')
    actual = json.loads(files['PROTOCOL-AUDIT.json'])
    require(actual['actual_runtime_executed'] and actual['actual_producer_checked'] and
            actual['protocol_audited_bundles'] == report['protocol_audited_bundles'] and
            actual['selected_class_fingerprints'] == report['selected_class_fingerprints'] == 28,
            'Fresh actual CLI/protocol scope differs')
    checks = actual['actual_offline_CLI_checks']
    require(len(checks) == 42 and checks == json.loads(files['RESULT.json']), 'Fresh actual CLI census differs')
    require(sum(c['exit_code'] == 0 for c in checks) == sum(c['exit_code'] == 2 for c in checks) == 21 and
            all(c['exit_code'] == c['expected'] for c in checks), 'Fresh actual CLI controls failed')
    for check in checks:
        require(check['log_prefix'] == hashlib.sha256(check['root'].encode()).hexdigest()[:16], 'CLI log identity differs')
        for suffix in ['.stdout', '.stderr']:
            require(check['log_prefix'] + suffix in files, 'Missing actual native CLI log')
        if check['exit_code'] == 0:
            require(b'Verified' in files[check['log_prefix'] + '.stdout'], 'Missing actual successful VERIFY')
        else:
            require(b'FAIL2' in files[check['log_prefix'] + '.stderr'], 'Missing actual native refusal')
    report.update(status=status['status'], saved_fresh_offline_cli_checks=42,
                  saved_fresh_valid_checks=21, saved_fresh_invalid_checks=21,
                  actual_runtime_executed_this_read=False,
                  fresh_runtime_receipt_source_bound=True, whole_goal_complete=False)
    return report
