#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit and seal an already installed private original runtime. Install nothing."""
from pathlib import Path
import argparse, os, subprocess, sys, traceback
sys.dont_write_bytecode = True
from Runtime import HERE, ACTION, Unavailable, pin, save, sources, inventory, source_tree, environment


def main():
    parser = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    for name in ['prefix', 'manager-workspace', 'source', 'out']:
        parser.add_argument('--' + name, required=True, type=Path)
    args = parser.parse_args()
    out = args.out.absolute()
    owned = False
    try:
        roots = [HERE, args.prefix.resolve(), args.manager_workspace.resolve(), args.source.resolve()]
        if out != out.resolve() or any(out.is_relative_to(p) or p.is_relative_to(out) for p in roots):
            raise ValueError('Use a NEW separate canonical provider output')
        out.mkdir(exist_ok=False)
        owned = True
        if sys.platform != 'linux':
            raise Unavailable('The selected original profile requires Linux/WSL')
        prefix, manager, source = roots[1:]
        source_tree(source)
        for root, stem in [(prefix, 'reference-'), (manager, 'conda-manager-')]:
            for name in ['pixi.toml', 'pixi.lock']:
                if pin(root / name) != pin(HERE / (stem + name)):
                    raise ValueError('Exact selected manifest/lock differs: ' + str(root / name))
        ready = dict(schema='omicsmatic-scrattchmapping02-original-runtime-v1',
                     provider=str(out), prefix=str(prefix), r_prefix=str(prefix / '.pixi/envs/default'),
                     manager_workspace=str(manager), manager_prefix=str(manager / '.pixi/envs/default'),
                     source=str(source), library=str(prefix / 'library'),
                     base_library=str(prefix / '.pixi/envs/default/lib/R/library'), runner_sources=sources())
        for name in ['home', 'tmp', 'audit']:
            (out / name).mkdir()
        control = dict(library=ready['library'], base_library=ready['base_library'], source=str(source),
                       profile=str(HERE / 'upstream-source-profile.json'), output=str(out / 'audit'),
                       audit=str(HERE / 'AuditRuntime.R'))
        save(out / 'control.json', control)
        command = [str(Path(ready['r_prefix']) / 'bin/Rscript'), '--vanilla',
                   str(HERE / 'AuditEntry.R'), str(out / 'control.json')]
        with (out / 'audit.stdout').open('xb') as stdout, (out / 'audit.stderr').open('xb') as stderr:
            child = subprocess.run(command, env=environment(ready, out), cwd=out,
                                   stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr)
        save(out / 'PROCESS.json', dict(command=command, exit_code=child.returncode))
        if child.returncode:
            raise Unavailable('Original namespace/dependency/Python audit failed; inspect audit logs')
        for key in ['r_prefix', 'library', 'manager_prefix']:
            print('INVENTORY', key, flush=True)
            save(out / (key + '-files.json'), inventory(Path(ready[key])))
        ready['inventory_files'] = {key: pin(out / (key + '-files.json'))
                                    for key in ['r_prefix', 'library', 'manager_prefix']}
        if sources() != ready['runner_sources']:
            raise ValueError('Adapter source changed during adoption; retain this attempt and retry NEW paths')
        (out / 'WARNINGS.md').write_text('# Runtime audited; analysis remains unqualified\n\n' + ACTION + '\n', encoding='utf-8')
        save(out / 'READY.json', ready)
        save(out / 'COMPLETED.json', dict(status='PASS_ORIGINAL_RUNTIME_AUDITED_NOT_ANALYSIS',
                                        provider=pin(out / 'READY.json'), analysis_executed=False,
                                        new_family_credit=0, whole_goal_complete=False))
        print('PASS_ORIGINAL_RUNTIME_AUDITED_NOT_ANALYSIS', flush=True)
        return 0
    except BaseException as error:
        code = 3 if isinstance(error, OSError) and not isinstance(error, FileExistsError) else 2
        label = ('SKIP3_RUNTIME_UNAVAILABLE' if code == 3 else 'FAIL2_RUNTIME_UNADMITTED') + ': ' + repr(error)
        if owned and (out / 'READY.json').exists():
            code = 2
            label = 'FAIL2_PROVIDER_FINALIZATION_UNADMITTED: ' + label
        print(label + '\n' + ACTION, flush=True)
        if owned:
            try:
                save(out / 'FAILURE.json', dict(status=label, traceback=traceback.format_exc(), required_action=ACTION))
                (out / 'WARNINGS.md').write_text('# ' + label + '\n\n' + ACTION + '\n', encoding='utf-8')
            except BaseException as recording:
                print('FAIL2_RECORDING_UNAVAILABLE', repr(recording), ACTION, flush=True)
                code = 2
        return code


if __name__ == '__main__':
    raise SystemExit(main())
