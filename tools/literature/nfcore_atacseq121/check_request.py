#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise source/input/schema refusals on a NEW controlled clone; no analysis or installation.

Linux Python only. Compile RequestProbe.java against the selected packaged JAR
with javac --release 17 first. This checks typed request contracts, not runtime,
image admission or executor-overlay generation; those need actual workflow controls.
"""
import argparse
import copy
import json
import os
import shutil
import subprocess
import sys
import traceback
from pathlib import Path
from prepare import Number, encode, pin, unique


def save(path, value):
    path.write_text(encode(value)+'\n', encoding='utf-8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ['jar', 'probe-classes', 'request', 'engine-root', 'out']:
        parser.add_argument('--'+name, required=True, type=Path)
    parser.add_argument('--java', type=Path, default=Path('/usr/bin/java'))
    args = parser.parse_args()
    if sys.platform != 'linux':
        print('SKIP3 run this control with Linux Python, including WSL; no analysis performed', file=sys.stderr)
        return 3
    out = args.out.absolute()
    out.mkdir(parents=True, exist_ok=False)
    results = []
    try:
        jar = args.jar.resolve(strict=True)
        request = json.loads(args.request.read_bytes(), parse_float=Number, object_pairs_hook=unique)
        original_recipe = Path(request['engine_recipe'])
        recipe = json.loads(original_recipe.read_bytes(), parse_float=Number, object_pairs_hook=unique)
        bundle = Path(recipe['resources']['bundle']['path'])
        originals = {str(path): pin(path) for path in bundle.rglob('*') if path.is_file()}
        originals.update({str(args.request): pin(args.request), str(original_recipe): pin(original_recipe), str(jar): pin(jar)})
        probe_files = list(args.probe_classes.rglob('*.class'))
        if len(probe_files) != 1 or probe_files[0].name != 'RequestProbe.class':
            raise ValueError('Require only the compiled development probe in the extra classpath')
        originals[str(probe_files[0])] = pin(probe_files[0])
        cloned = out/'materialized'
        shutil.copytree(bundle, cloned, copy_function=shutil.copy2)
        original_design = (cloned/Path(recipe['resources']['design']['path']).relative_to(bundle)).read_bytes()
        recipe['workflow']['path'] = str(cloned/Path(recipe['workflow']['path']).relative_to(bundle))
        for row in recipe['resources'].values():
            row['path'] = str(cloned/Path(row['path']).relative_to(bundle))
        expected_overlay = out/'expected-original-overlay.config'
        expected_overlay.write_bytes((Path(recipe['workflow']['path'])/'runtime/host.config').read_bytes())
        suite = [
            ('valid', 0, ''), ('unknown-parameter', 2, 'Unknown original'), ('literal-input', 2, ''),
            ('unbound-input', 2, ''), ('input-traversal', 2, ''), ('string-boolean', 2, 'typed scalar'),
            ('fractional-integer', 2, 'typed scalar'), ('igenome', 3, 'outside'), ('notification', 3, 'outside'),
            ('skip-preseq', 3, 'outside'), ('wrong-publication', 2, 'choice'), ('symlink-publication', 3, 'copy'),
            ('other-engine', 3, 'Nextflow21.04.1'), ('other-pipeline', 3, 'family/version'),
            ('literal-output', 2, 'owned complete'), ('no-observed-tasks', 2, 'observed tasks'),
            ('omitted-config', 2, 'config'), ('unexpected-host-tool', 2, 'host-PATH'),
            ('incomplete-output-family', 2, 'publication family'), ('paired-mode-mismatch', 2, 'single_end'),
            ('zero-genome-size', 2, 'positive'), ('bad-genome-size', 2, 'genome-size'),
            ('custom-name', 3, 'outside'), ('unsafe-scalar', 3, 'scalar syntax'),
            ('CPU-capacity', 2, 'CPU cap'), ('missing-genome-size', 2, 'genome-size'),
            ('unbound-read', 2, 'outside the complete captured'), ('same-physical-mates', 2, 'one physical'),
            ('changed-source', 2, 'source differs'), ('extra-source', 2, 'extra/missing code'),
            ('nonexecutable-helper', 3, 'executable mode'), ('changed-overlay', 2, 'overlay differs'),
        ]
        for label, expected, fragment in suite:
            case = out/label
            case.mkdir()
            q, r = copy.deepcopy(request), copy.deepcopy(recipe)
            p = r['parameters']
            if label == 'unknown-parameter': p['macs_gsie'] = 'hs'
            elif label == 'literal-input': p['blacklist'] = '/outside/blacklist.bed'
            elif label == 'unbound-input': p['blacklist'] = {'$input': 'UNDECLARED'}
            elif label == 'input-traversal': p['blacklist'] = {'$input': 'bundle', 'path': '../outside.bed'}
            elif label == 'string-boolean': p['keep_dups'] = 'false'
            elif label == 'fractional-integer': p['fingerprint_bins'] = Number('100.5')
            elif label == 'igenome': p['genome'] = 'GRCh38'
            elif label == 'notification': p['email'] = 'unrequested@example.org'
            elif label == 'skip-preseq': p['skip_preseq'] = True
            elif label == 'wrong-publication': p['publish_dir_mode'] = 'invented'
            elif label == 'symlink-publication': p['publish_dir_mode'] = 'symlink'
            elif label == 'other-engine': r['engine_profile'] = 'linux-x86_64-nextflow-24.04.4'
            elif label == 'other-pipeline': q['pipeline_profile'] = 'nfcore-atacseq-2.0'
            elif label == 'literal-output': p['outdir'] = '/outside/output'
            elif label == 'no-observed-tasks': r['require_tasks'] = False
            elif label == 'omitted-config': r['workflow']['configs'] = ['runtime/host.config']
            elif label == 'unexpected-host-tool': r['required_tools'].append(dict(name='samtools', path='/usr/bin/samtools'))
            elif label == 'incomplete-output-family': r['outputs'] = r['outputs'][:1]
            elif label == 'paired-mode-mismatch': p['single_end'] = True
            elif label == 'zero-genome-size': p['macs_gsize'] = 0
            elif label == 'bad-genome-size': p['macs_gsize'] = 'invented'
            elif label == 'custom-name': p['name'] = 'custom'
            elif label == 'unsafe-scalar': p['seq_center'] = 'lab;command'
            elif label == 'CPU-capacity': p['max_cpus'] = q['executor_cpus']+1
            elif label == 'missing-genome-size': del p['macs_gsize']
            elif label in ['unbound-read', 'same-physical-mates']:
                rows = original_design.decode('utf-8').splitlines()
                cells = rows[1].split(',')
                if label == 'unbound-read': cells[2] = '/outside/unrecorded.fq.gz'
                else: cells[3] = cells[2]
                rows[1] = ','.join(cells)
                design = cloned/(label+'.csv')
                design.write_text('\n'.join(rows)+'\n', encoding='utf-8', newline='\n')
                r['resources']['design']['path'] = str(design)
            elif label in ['changed-source', 'extra-source', 'nonexecutable-helper', 'changed-overlay']:
                workflow = cloned/('workflow-'+label)
                shutil.copytree(Path(recipe['workflow']['path']), workflow, copy_function=shutil.copy2)
                r['workflow']['path'] = str(workflow)
                if label == 'changed-source':
                    with (workflow/'main.nf').open('ab') as stream: stream.write(b'\n// controlled changed source\n')
                elif label == 'extra-source': (workflow/'unreported.groovy').write_text('// controlled extra source\n')
                elif label == 'nonexecutable-helper': (workflow/'bin/check_design.py').chmod(0o644)
                else:
                    with (workflow/'runtime/host.config').open('ab') as stream: stream.write(b'\n// changed runtime overlay\n')
            recipe_path = case/'engine-recipe.json'
            save(recipe_path, r)
            q['engine_recipe'] = str(recipe_path)
            save(case/'request.json', q)
            command = [str(args.java), '-Xmx512m', '-cp', str(args.probe_classes)+':'+str(jar), 'org.jlinalg.cli.RequestProbe', str(case/'request.json'), str(args.engine_root), str(expected_overlay)]
            process = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=120)
            (case/'stdout').write_bytes(process.stdout)
            (case/'stderr').write_bytes(process.stderr)
            record = dict(case=label, command=command, exit_code=process.returncode, expected_exit=expected)
            save(case/'PROCESS.json', record)
            if process.returncode != expected or fragment not in process.stderr.decode('utf-8', errors='replace') or expected == 0 and b'PASS_TYPED_SOURCE_INPUT_CONTRACT_ONLY_NO_ANALYSIS' not in process.stdout or expected != 0 and not process.stderr:
                raise ValueError('Unexpected contract-control outcome: '+label+'; inspect retained logs')
            results.append(record)
            print('PASS', label, flush=True)
        if any(pin(Path(name)) != value for name, value in originals.items()):
            raise ValueError('Original source/data/request, artifact or compiled probe changed')
        save(out/'COMPLETED.json', dict(status='PASS_TYPED_ATAC_REQUEST_CONTROLS_ONLY', cases=results,
            originals_unchanged=originals, scientific_analysis_executed=False, runtime_image_admitted_by_this_probe=False,
            new_catalog_integrations=0, scope=__doc__))
        return 0
    except Exception as error:
        save(out/'FAILURE.json', dict(status='FAIL2_REQUEST_CONTROLS_NO_RESULT', error=repr(error), traceback=traceback.format_exc(), completed_cases=results))
        print('FAIL2', str(error), file=sys.stderr, flush=True)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
