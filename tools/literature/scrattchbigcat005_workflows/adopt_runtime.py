#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit an already installed unchanged BIGCAT profile; install and download nothing."""
from pathlib import Path
import argparse,subprocess,sys,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,profile,source_packages,pin,save,load,sources,inventory,source_tree,environment,check_artifacts,check_output,control

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    parser.add_argument('--profile',required=True)
    parser.add_argument('--workspace',required=True,type=Path)
    parser.add_argument('--source',required=True,action='append',help='Exactly one PACKAGE=/absolute/original/source path per declared source')
    parser.add_argument('--out',required=True,type=Path)
    args=parser.parse_args();out=args.out.absolute();owned=False
    try:
        selected=profile(args.profile);workspace=args.workspace.resolve();source_paths={}
        for value in args.source:
            name,path=value.split('=',1)
            if name in source_paths:raise ValueError('Duplicate original source: '+name)
            source_paths[name]=str(Path(path).resolve())
        out=check_output(out,[HERE,workspace,*source_paths.values()]);out.mkdir(parents=True,exist_ok=False);owned=True
        if sys.platform!='linux':raise Unavailable('Selected original runtime requires permitted Linux/WSL')
        source_tree(args.profile,source_paths)
        for supplied,expected in [('pixi.toml',selected['manifest']),('pixi.lock',selected['lock'])]:
            if pin(workspace/'runtime'/supplied)!=pin(HERE/expected):raise ValueError('Exact selected manifest/lock required')
        prefix=workspace/'runtime/.pixi/envs/default';library=workspace/'library'
        check_artifacts(prefix,args.profile)
        before=sources();prefix_files=inventory(prefix);library_files=inventory(library)
        ready=dict(schema='omicsmatic-original-bigcat005-provider-v1',profile=args.profile,
          workspace=str(workspace),r_prefix=str(prefix),library=str(library),source_paths=source_paths,
          provider=str(out),adapter_sources=before)
        for name in ['home','tmp']:(out/name).mkdir()
        declaration=control(ready,out,audit_output=str(out/'complete-original-namespace-audit.rds'))
        save(out/'control.json',declaration)
        command=[str(prefix/'bin/Rscript'),'--vanilla',str(HERE/'AuditEntry.R'),str(out/'control.json')]
        with (out/'audit.stdout').open('xb') as stdout,(out/'audit.stderr').open('xb') as stderr:
            child=subprocess.run(command,cwd=out,env=environment(ready,out),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
        save(out/'PROCESS.json',dict(command=command,exit_code=child.returncode))
        if child.returncode:raise Unavailable('Original profile namespace/source/capability audit failed; inspect complete audit logs')
        if sources()!=before or inventory(prefix)!=prefix_files or inventory(library)!=library_files:
            raise ValueError('Protected adapter/runtime changed during audit')
        source_tree(args.profile,source_paths)
        for name,files in [('r-prefix-files',prefix_files),('library-files',library_files)]:
            path=out/(name+'.json');save(path,files);ready[name]=pin(path)
        (out/'WARNINGS.md').write_text('# Original runtime audited; no analysis executed\n\n'+
          '\n'.join(selected['known_limits'])+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
        save(out/'READY.json',ready)
        save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_BIGCAT_RUNTIME_NOT_ANALYSIS',
          profile=args.profile,provider=pin(out/'READY.json'),source_packages=[p['name'] for p in source_packages(args.profile)],
          original_bigcat_namespace_functions=selected['namespace_functions'],original_arrow_functions=15,
          new_family_credit=0,whole_goal_complete=False))
        print('PASS_ORIGINAL_BIGCAT_RUNTIME_NOT_ANALYSIS',args.profile,flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        status='SKIP3_BIGCAT_RUNTIME_UNAVAILABLE' if code==3 else 'FAIL2_BIGCAT_RUNTIME_UNADMITTED'
        print(status,repr(error),ACTION,flush=True)
        if owned:
            try:
                save(out/'FAILURE.json',dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
                (out/'WARNINGS.md').write_text('# '+status+'\n\n'+repr(error)+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
            except BaseException as recording:code=2;print('FAIL2_RUNTIME_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':raise SystemExit(main())
