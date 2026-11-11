# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the complete GENESIS root/workflow evidence and replay its saved scientific bundles without R."""
import argparse
import json
import re
import shutil
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import unpack, byte_pin
from catalog_source_integrity import verify_source

HOME = ROOT / 'docs/literature/genesis-tools-development'
STATUS = 'PASS_GENESIS242_TYPED_ROOT_AND_COMPLETE_SYNTHETIC_WORKFLOW_CONTROLS'
PREFIX = 'build/literature-review/genesis-runtime/'
ACTION = 'Java17+ missing or forbidden: SKIP saved verification; supply a permitted --java executable or leave verification skipped. This helper installs nothing and runs no R or model fits. FAIL2 admits no verified result: retain the attempt and resolve the named source/archive/scientific cause before retrying NEW paths. Saved scientific replay uses the matching standalone worker; root dispatch was tested separately. No independent estimator refit, producer authenticity, historical-paper or whole-goal certificate.'


class Unavailable(RuntimeError):
    pass


def inspect(home=HOME):
    home = Path(home); proof = json.loads((home / 'VALIDATION.json').read_bytes())
    assert proof['status'] == STATUS and proof['root_command_available'] and proof['workflow_ready_for_catalog']
    assert not proof['new_catalog_credit'] and not proof['whole_goal_complete'] and not proof['whole_package_implemented']
    data = ROOT / 'docs/literature/omics-2023-2026'
    for name, value in proof['source_pins'].items():
        verify_source(ROOT, data, name, value['sha256'])
    assert pin(home / 'controls.zip') == proof['archive']; files = unpack(home / 'controls.zip', proof)
    for name, value in proof['source_pins'].items():
        assert byte_pin(files['current-source/' + name]) == value
    for name, value in proof['artifacts'].items():
        assert pin(home / name) == value
    for name, value in proof['predecessor_evidence_files'].items():
        assert pin(ROOT / name) == value, ('Changed predecessor scientific evidence', name)
    from verify_genesis_partition_evidence import inspect as partition
    from verify_genesis_workflow_evidence import inspect as original_workflows
    from verify_genesis_extended_evidence import inspect as extended
    partition(); original_workflows(); extended()
    branch = json.loads(files[PREFIX + 'extended-61-branch-controls-1/COMPLETED.json'])
    assert branch['status'] == 'PASS61_EXTENDED_GENESIS_DECLARED_BRANCH_DISPOSITIONS_53_POSITIVE_AND8_REFUSALS'
    assert len(branch['records']) == 61 and all(r['matches_declared_disposition'] and not r['project_analysis_admitted'] for r in branch['records'])
    assert (branch['original_successes'], branch['native_component_successes'], branch['native_component_refusals'], branch['original_errors']) == (58, 53, 5, 3)
    root = json.loads(files[PREFIX + 'root-examples-validation-2/COMPLETED.json'])
    assert root['status'] == 'PASS_GENESIS_ROOT13_LITERAL_ANALYSES11_ADMITTED2_REFUSED33_SAVED_REPLAYS12_HELPS1_SKIP'
    assert (root['original_analysis_attempts'], root['admitted_original_analyses'], root['expected_scientific_refusals'], root['saved_replays'], root['root_help_checks'], root['prerequisite_skips']) == (13, 11, 2, 33, 12, 1)
    assert len(proof['saved_bundle_paths']) == 11
    for case in proof['saved_bundle_paths']:
        prefix = PREFIX + case + '/'; done = json.loads(files[prefix + 'COMPLETED.json'])
        assert done['result_admitted'] and done['original_analysis_executed']
        assert {n.removeprefix(prefix): byte_pin(v) for n, v in files.items() if n.startswith(prefix) and n != prefix + 'COMPLETED.json'} == done['files']
    for compiler in [21, 25]:
        build = json.loads(files[PREFIX + f'root-build{compiler}-1/COMPLETED.json'])
        assert build['status'] == 'PASS_GENESIS_ROOT_PREDECESSOR_SCIENTIFIC_BYTES_UNCHANGED'
        with zipfile.ZipFile(home / f'genesis-root-javac{compiler}.jar') as current, zipfile.ZipFile(ROOT / f'docs/literature/snprelate-tools-development/snprelate-root-javac{compiler}.jar') as previous, zipfile.ZipFile(ROOT / f'docs/literature/genesis-extended-workflow-development/genesis-workflows-javac{compiler}.jar') as standalone:
            for name in previous.namelist():
                if name != 'org/jlinalg/cli/JLinAlgCli.class':
                    assert current.read(name) == previous.read(name), ('Changed predecessor scientific class/resource', name)
            for name in standalone.namelist():
                if name.endswith('.class') or name.startswith('org/jlinalg/omics/genesis/'):
                    assert current.read(name) == standalone.read(name), ('Changed GENESIS scientific class/resource', name)
            assert current.read('org/jlinalg/cli/JLinAlgCli.class') == files[PREFIX + f'root-build{compiler}-1/classes/org/jlinalg/cli/JLinAlgCli.class']
    assert proof['canonical_executable_unchanged']['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    return proof, files


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--home', type=Path, default=HOME); p.add_argument('--out', type=Path, required=True)
    p.add_argument('--java', type=Path); p.add_argument('--jar-compiler', choices=['21', '25'], default='21'); a = p.parse_args(); out = a.out.resolve(); home = a.home.resolve()
    if out.exists() or out.is_relative_to(home) or home.is_relative_to(out):
        print('FAIL2: choose NEW output separate from the evidence. ' + ACTION); return 2
    out.mkdir(parents=True); state = dict(status='FAIL2_GENESIS_TOOLS_SAVED_REPLAY', original_R_analysis_executed=False, downloads_or_installation=False, new_catalog_credit=False, whole_goal_complete=False, required_action=ACTION); code = 2
    try:
        proof, files = inspect(home); java = a.java or (Path(shutil.which('java')) if shutil.which('java') else None)
        if java is None or not java.is_file():
            raise Unavailable('Java17+ is missing; supply a permitted executable')

        def run(command, label, timeout):
            try:
                result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=timeout)
            except OSError as error:
                raise Unavailable('Java could not be launched: ' + repr(error)) from error
            (out / (label + '.stdout')).write_bytes(result.stdout); (out / (label + '.stderr')).write_bytes(result.stderr)
            save(out / (label + '.process.json'), dict(command=command, exit_code=result.returncode)); return result

        version = run([str(java), '-version'], 'java-version', 30); match = re.search(rb'version\s+"(\d+)', version.stdout + version.stderr)
        if version.returncode or not match or int(match[1]) < 17:
            raise Unavailable('A working Java17+ is required; inspect retained version output')
        controls = out / 'retained-controls'; controls.mkdir(); selected = {}
        for name, raw in files.items():
            if any(name.startswith(PREFIX + case + '/') for case in proof['saved_bundle_paths']):
                target = controls / name.removeprefix(PREFIX); target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(raw); selected[str(target)] = byte_pin(raw)
        jar = ROOT / f'docs/literature/genesis-extended-workflow-development/genesis-workflows-javac{a.jar_compiler}.jar'
        before = pin(jar); assert before == proof['predecessor_evidence_files'][jar.relative_to(ROOT).as_posix()]
        records = []
        for i, case in enumerate(proof['saved_bundle_paths']):
            result = run([str(java), '-Xmx2g', '-jar', str(jar), '--verify', str(controls / case)], 'saved-' + str(i), 180)
            assert result.returncode == 0, (case, result.stderr[-6000:]); records.append(dict(case=case, exit_code=result.returncode)); print('PASS_SAVED_GENESIS', case, flush=True)
        assert pin(jar) == before and all(pin(Path(n)) == value for n, value in selected.items())
        state.update(status='PASS11_GENESIS_TOOLS_SAVED_SCIENTIFIC_REPLAYS_WITHOUT_R', records=records, proof=pin(home / 'VALIDATION.json'), archive=proof['archive']); code = 0
    except Unavailable as error:
        state.update(status='SKIP3_GENESIS_TOOLS_JAVA_UNAVAILABLE', cause=repr(error)); code = 3
    except Exception as error:
        state['cause'] = repr(error); (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause', 'Saved native scientific contracts only; no original execution or independent estimator refit.') + '\n\nREQUIRED ACTION: ' + ACTION + '\n', encoding='utf8', newline='\n'); save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', '')[:6000], flush=True); return code


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact source, predecessor scientific or complete bundle checks')
    raise SystemExit(main())
