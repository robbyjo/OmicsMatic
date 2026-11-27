# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the unchanged original plugin directly for the supplied synthetic control.

This independent reference does not call the Java bridge, install software,
download dependencies, assert biological validity or admit an nf-core pipeline.
"""
import argparse
import json
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ['engine', 'plugins', 'java-home', 'project', 'out']:
        parser.add_argument('--' + option, type=Path, required=True)
    args = parser.parse_args()
    root = args.out.absolute()
    if root.exists() or root.parent.resolve() != root.parent:
        raise SystemExit('Use a NEW output directory with a canonical existing parent.')
    bindings = {}
    for name in ['engine', 'plugins', 'java_home', 'project']:
        path = getattr(args, name).absolute()
        if path.resolve() != path or not path.is_dir():
            raise SystemExit('Require canonical existing ' + name)
        bindings[name] = path
    root.mkdir()
    rows = []
    for label, changes, expected in [('valid', {}, 0), ('invalid_probability', {'threshold': 1.01}, 1), ('invalid_identifier', {'specimen': 'A;invalid'}, 1)]:
        directory = root / label
        directory.mkdir()
        parameters = dict(specimen='sample_A', threshold=0.05, outdir=str(directory / 'published'))
        parameters.update(changes)
        (directory / 'params.json').write_text(json.dumps(parameters) + '\n', encoding='utf-8', newline='\n')
        environment = dict(PATH='/usr/bin:/bin', LANG='C.UTF-8', LC_ALL='C.UTF-8', TZ='UTC', NXF_HOME=str(directory / 'nxf-home'), NXF_JAVA_HOME=str(bindings['java_home']), NXF_BIN=str(bindings['engine'] / 'nextflow-24.04.4-one.jar'), CAPSULE_CACHE_DIR=str(directory / 'capsule'), CAPSULE_LOCAL_REPO=str(bindings['engine'] / 'repository'), NXF_PLUGINS_DIR=str(bindings['plugins']), NXF_PLUGINS_DEFAULT='false', NXF_OFFLINE='true', NXF_DISABLE_CHECK_LATEST='true', NXF_OPTS='-Xmx512m -Dcapsule.offline=true')
        command = ['/usr/bin/bash', str(bindings['engine'] / 'nextflow'), '-C', str(bindings['project'] / 'nextflow.config'), '-log', str(directory / 'nextflow.log'), 'run', str(bindings['project'] / 'main.nf'), '-params-file', str(directory / 'params.json'), '-with-trace', str(directory / 'trace.tsv'), '-work-dir', str(directory / 'work'), '-ansi-log', 'false']
        with (directory / 'stdout').open('xb') as out, (directory / 'stderr').open('xb') as err:
            process = subprocess.run(command, cwd=directory, env=environment, stdin=subprocess.DEVNULL, stdout=out, stderr=err, timeout=180)
        row = dict(label=label, expected_exit=expected, exit_code=process.returncode, command=command, environment=environment)
        rows.append(row)
        (directory / 'RESULT.json').write_text(json.dumps(row, indent=2) + '\n', encoding='utf-8', newline='\n')
        if process.returncode != expected:
            raise SystemExit('FAIL2: original ' + label + ' differs; inspect retained logs. No reference result admitted.')
        if expected == 0 and (directory / 'published/validated.tsv').read_bytes() != b'sample_A\t0.05\n':
            raise SystemExit('FAIL2: original complete control output differs; preserve evidence.')
    (root / 'COMPLETED.json').write_text(json.dumps(dict(controls=rows,original_plugin_control_only=True,new_family_credit=0,whole_paper_reproduced=False), indent=2) + '\n', encoding='utf-8', newline='\n')


if __name__ == '__main__':
    main()
