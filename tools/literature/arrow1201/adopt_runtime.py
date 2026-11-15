#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit an already installed permitted locked runtime; install and download nothing."""
from pathlib import Path
import argparse, subprocess, sys, traceback
sys.dont_write_bytecode = True
from Runtime import HERE, ACTION, Unavailable, pin, save, load, sources, inventory, source_tree, environment, check_artifacts

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    parser.add_argument('--workspace',required=True,type=Path)
    parser.add_argument('--source',required=True,type=Path)
    parser.add_argument('--out',required=True,type=Path)
    args=parser.parse_args()
    out=args.out.absolute(); owned=False
    try:
        workspace, source=args.workspace.resolve(),args.source.resolve()
        if out != out.resolve() or any(out.is_relative_to(p) or p.is_relative_to(out) for p in [HERE,workspace,source]):
            raise ValueError('Use a NEW canonical provider output separate from source and installed runtime')
        out.mkdir(parents=True,exist_ok=False); owned=True
        if sys.platform != 'linux':
            raise Unavailable('This selected original provider requires Linux/WSL')
        for name in ['pixi.toml','pixi.lock']:
            if pin(workspace/name) != pin(HERE/('reference-'+name)):
                raise ValueError('Exact selected manifest/lock required')
        source_tree(source)
        prefix=workspace/'.pixi/envs/default'
        check_artifacts(prefix)
        before=inventory(prefix)
        ready=dict(schema='omicsmatic-original-arrow1201-provider-v1',workspace=str(workspace),
            r_prefix=str(prefix),source=str(source),provider=str(out),adapter_sources=sources())
        for name in ['home','tmp']:
            (out/name).mkdir()
        command=[str(prefix/'bin/Rscript'),'--vanilla',str(HERE/'AuditEntry.R'),
                 str(HERE/'AuditRuntime.R'),str(source),str(out/'original-source-audit.rds')]
        with (out/'audit.stdout').open('xb') as stdout,(out/'audit.stderr').open('xb') as stderr:
            child=subprocess.run(command,cwd=out,env=environment(ready,out),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
        save(out/'PROCESS.json',dict(command=command,exit_code=child.returncode))
        if child.returncode:
            raise Unavailable('Original Arrow source/edition/capability audit failed; inspect raw audit logs')
        if inventory(prefix) != before or sources() != ready['adapter_sources']:
            raise ValueError('Runtime or adapter changed during original audit')
        source_tree(source)
        save(out/'r-prefix-files.json',before)
        ready['prefix_inventory']=pin(out/'r-prefix-files.json')
        save(out/'READY.json',ready)
        save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_ARROW1201_RUNTIME_NOT_ANALYSIS',
            provider=pin(out/'READY.json'),original_source_regular_files=1619,original_namespace_functions=13,
            new_family_credit=0,whole_goal_complete=False))
        (out/'WARNINGS.md').write_text('# Runtime audited; no analysis executed\n\n'+ACTION+'\n',encoding='utf-8')
        print('PASS_ORIGINAL_ARROW1201_RUNTIME_NOT_ANALYSIS',flush=True)
        return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        status='SKIP3_ARROW1201_RUNTIME_UNAVAILABLE' if code==3 else 'FAIL2_ARROW1201_RUNTIME_UNADMITTED'
        print(status,repr(error),ACTION,flush=True)
        if owned:
            try:
                save(out/'FAILURE.json',dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
                (out/'WARNINGS.md').write_text('# '+status+'\n\n'+repr(error)+'\n\n'+ACTION+'\n',encoding='utf-8')
            except BaseException as recording:
                code=2;print('FAIL2_PROVIDER_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':
    raise SystemExit(main())
