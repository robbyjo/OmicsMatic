#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently inspect complete original LDAK payloads and matching reader classes."""
from pathlib import Path
import argparse
import base64
import csv
import hashlib
import json
import math
import re
import sys
import traceback
import zipfile

sys.dont_write_bytecode = True
if not __debug__:
    raise RuntimeError('Payload audit assertions must remain enabled; rerun Python without -O')
ROOTS = ['org.jlinalg.cli.'+name for name in ['LdakWorkflowCli', 'LdakWorkflowFiles', 'LdakWorkflowRequest', 'LdakWorkflowProcess', 'LdakWorkflowInputs', 'LdakWorkflowResults', 'LdakWorkflowDiagnostics']]+[
    'org.jlinalg.genetics.LdakKinship', 'org.jlinalg.genetics.LdakBedDataset',
    'org.jlinalg.compute.BackendContext', 'org.jlinalg.compute.BackendPolicy',
    'jdistlib.accelerator.CpuComputeBackend', 'jdistlib.accelerator.MatrixTranspose',
    'jdistlib.Normal', 'jdistlib.math.MathFunctions', 'jdistlib.math.Deviance',
    'jdistlib.accelerator.ComputeBackend', 'jdistlib.accelerator.ComputeApi',
    'jdistlib.accelerator.ComputeCapabilities', 'jdistlib.accelerator.ComputeDeviceInfo']
BINARY = dict(bytes=43483032, sha256='af969a0e07c51d5b0cdb364d815406a518150cb16e0993c1f93fb7f3fe455245')
LICENSE = dict(bytes=35671, sha256='a1c2c9c0ab366c35da4515531bb402ce058a776ba034eb3d4c162ece06f9be84')


def pin(path):
    with Path(path).open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return dict(bytes=Path(path).stat().st_size, sha256=digest)


def properties(path):
    def unescape(text):
        return re.sub(r'\\u([0-9A-Fa-f]{4})|\\(.)', lambda match:chr(int(match[1], 16)) if match[1] else {'t':'\t', 'r':'\r', 'n':'\n', 'f':'\f'}.get(match[2], match[2]), text)
    result = {}
    for line in Path(path).read_text(encoding='iso-8859-1').splitlines():
        if not line or line.startswith(('#', '!')):
            continue
        key, separator, value = line.partition('=')
        assert separator and '\\' not in key and key not in result, str(path)
        result[key] = unescape(value)
    return result


def inventory(root):
    members = {}
    for path in sorted(root.rglob('*')):
        assert not path.is_symlink() and (path.is_file() or path.is_dir()), str(path)
        name = path.relative_to(root).as_posix()
        members[name+'/'] = dict(bytes=0, sha256='directory') if path.is_dir() else None
        if path.is_file():
            members.pop(name+'/')
            members[name] = pin(path)
    return members


def encoded_pins(members):
    result = {}
    for name, value in members.items():
        encoded = base64.urlsafe_b64encode(name.encode()).rstrip(b'=').decode()
        result['bytes.'+encoded] = str(value['bytes'])
        result['sha256.'+encoded] = value['sha256']
    return result


def classes(jar):
    result = {}
    with zipfile.ZipFile(jar) as saved:
        for name in saved.namelist():
            if not name.endswith('.class'):
                continue
            binary = name[:-6].replace('/', '.')
            if any(binary == root or binary.startswith(root+'$') for root in ROOTS):
                assert binary not in result
                result[binary] = hashlib.sha256(saved.read(name)).hexdigest()
    assert set(ROOTS) <= set(result) and len(result) == 48, len(result)
    return result


def inspect(bundle, implementation):
    assert not (bundle/'FAILURE.txt').exists() and not (bundle/'SKIPPED.txt').exists()
    completed = properties(bundle/'COMPLETED.properties')
    assert completed['profile'] == 'original-ldak52-captured-workflow-v1'
    assert completed['original_engine_executed'] == 'true'
    assert pin(bundle/'manifest.properties')['sha256'] == completed['manifest_sha256']
    members = inventory(bundle)
    for name in ['manifest.properties', 'COMPLETED.properties']:
        members.pop(name)
    assert properties(bundle/'manifest.properties') == encoded_pins(members), str(bundle)
    assert properties(bundle/'implementation.properties') == implementation, str(bundle)
    assert pin(bundle/'runtime/ldak5.2.linux') == BINARY
    assert pin(bundle/'runtime/LICENSE-UPSTREAM.txt') == LICENSE
    science = {name:value for name, value in members.items() if not name.endswith('/') and ((name.startswith('stages/') and Path(name).name.startswith('result.') and not name.endswith('.progress')) or name.startswith('native/') or name in ['summary.tsv', 'DIAGNOSTICS.tsv'])}
    assert properties(bundle/'scientific.properties') == encoded_pins(science), str(bundle)
    request = properties(bundle/'request.properties')
    assert request['execution'] == 'allow' and request['mode'] == completed['mode']
    stages = []
    for folder in sorted((bundle/'stages').iterdir()):
        assert folder.is_dir()
        started = properties(folder/'PROCESS-STARTED.properties')
        finished = properties(folder/'PROCESS-FINISHED.properties')
        arguments = [started['argument.'+str(i)] for i in range(int(started['arguments']))]
        assert arguments[0].endswith('/runtime/ldak5.2.linux') and arguments[-2:] == ['--max-threads', '1']
        assert started['binary_sha256'] == BINARY['sha256']
        assert started['environment_policy'] == 'remove-LD_PRELOAD-LD_LIBRARY_PATH-OMP_*-MKL_*;OMP_NUM_THREADS=1;MKL_NUM_THREADS=1;LC_ALL=C'
        assert finished['exit_code'] == '0' and all(finished[k] == 'false' for k in ['timed_out', 'budget_exceeded', 'interrupted'])
        assert int(finished['pid']) > 0 and 0 <= int(finished['elapsed_ms']) <= int(request['wall_ms'])
        assert 'Version 5.2 - Help pages' in (folder/'original.stdout').read_text(encoding='utf-8')
        assert (folder/'original.stderr').is_file()
        stages.append(dict(name=folder.name, argument=arguments[1], pid=int(finished['pid']), elapsed_ms=int(finished['elapsed_ms'])))
    with (bundle/'summary.tsv').open(encoding='utf-8', newline='') as stream:
        fits = list(csv.DictReader(stream, delimiter='\t'))
    assert len(fits) == int(completed['analyses']) and fits
    for fit in fits:
        assert fit['analysis'] == request['mode']
        for key in ['reported_h2', 'reported_sd', 'reported_sample_metric', 'null_logl', 'alt_logl']:
            assert math.isfinite(float(fit[key]))
        assert float(fit['reported_sd']) >= 0
    if request['mode'] == 'sumher':
        assert len(stages) == 1 and stages[0]['argument'] == '--sum-hers'
        with (bundle/'native/input-audit.tsv').open(encoding='utf-8', newline='') as stream:
            counts = {row['metric']:int(row['value']) for row in csv.DictReader(stream, delimiter='\t')}
        assert counts['tagging_regression_rows'] == counts['declared_regression_predictors']
        assert counts['declared_reference_predictors'] >= counts['declared_regression_predictors']
        assert 0 < counts['declared_heritability_predictors'] <= counts['declared_reference_predictors']
        assert 0 < counts['reported_datapoints'] <= counts['supplied_summary_rows']
        assert counts['reference_categories'] == int(completed['estimated_components'])
        estimates = (bundle/'stages/sumher/result.hers').read_text(encoding='ascii').splitlines()
        labels = [line.split()[0] for line in estimates[1:] if line.strip()]
        assert len(labels) == len(set(labels)) == counts['reference_categories']+1 and 'Her_All' in labels
    else:
        alphas = request['alpha_grid'].split(',')
        assert [fit['alpha'] for fit in fits] == alphas
        assert len(stages) == len(alphas)*2 + int(request['thinning'] == 'YES')
        assert sum(s['argument'] == '--calc-kins-direct' for s in stages) == len(alphas)
        assert sum(s['argument'] == '--reml' for s in stages) == len(alphas)
        counts = None
        assert int(completed['compared_kinship_entries']) > 0
    parent = None
    if completed['genuine_replay'] == 'true':
        lineage = properties(bundle/'PARENT.properties')
        assert properties(bundle/'PARENT-SCIENCE.properties') == properties(bundle/'scientific.properties')
        assert pin(bundle/'PARENT-SCIENCE.properties')['sha256'] == lineage['expected_science_manifest_sha256']
        parent = lineage['parent_manifest_sha256']
    else:
        assert completed['genuine_replay'] == 'false'
        assert not (bundle/'PARENT.properties').exists()
    return dict(path=str(bundle), manifest_sha256=completed['manifest_sha256'], files=sum(not name.endswith('/') for name in members), method_classes=len(implementation), mode=request['mode'], stages=stages, fits=fits, input_counts=counts, science_files=len(science), parent_manifest_sha256=parent, native_kinship_entries=int(completed['compared_kinship_entries']))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--root', action='append', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=False)
    try:
        implementation = classes(args.jar)
        bundles = []
        for root in args.root:
            bundles.extend(marker.parent for marker in root.rglob('COMPLETED.properties'))
        assert bundles and len(set(p.resolve() for p in bundles)) == len(bundles)
        rows = [inspect(bundle.resolve(), implementation) for bundle in sorted(bundles)]
        manifests = {row['manifest_sha256'] for row in rows}
        assert all(row['parent_manifest_sha256'] is None or row['parent_manifest_sha256'] in manifests for row in rows), 'Missing captured replay parent'
        result = dict(status='PASS_INDEPENDENT_COMPLETE_PAYLOAD_READER_CLASS_FAMILIES_ORIGINAL_PROCESSES_AND_REPLAY_LINKS', reader=pin(args.jar), class_fingerprints=implementation, bundles=rows, original_processes=sum(len(row['stages']) for row in rows), whole_goal_complete=False)
        (args.out/'COMPLETED.json').write_text(json.dumps(result, indent=2)+'\n', encoding='utf-8')
        print(result['status']+'; bundles='+str(len(rows))+'; classes='+str(len(implementation)), flush=True)
        return 0
    except Exception:
        (args.out/'FAILURE.txt').write_text(traceback.format_exc(), encoding='utf-8')
        print('FAIL2: retain the independent payload discrepancy; no qualification.', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
