#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit complete unchanged SingleR sources and a permitted source-built runtime."""
from pathlib import Path
import argparse,subprocess,sys,traceback
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,pin,load,save,profile,sources,inventory,source_tree,environment,check_output

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--edition',required=True);p.add_argument('--workspace',type=Path,required=True)
    p.add_argument('--out',type=Path,required=True)
    p.add_argument('--source',action='append',required=True,help='PACKAGE=/absolute/original/source; supply every selected profile package')
    a=p.parse_args();out=a.out.absolute();owned=False
    try:
        workspace=a.workspace.resolve();paths={}
        for entry in a.source:
            name,value=entry.split('=',1)
            if name in paths:raise ValueError('Duplicate source package')
            paths[name]=str(Path(value).resolve())
        out=check_output(out,[HERE,workspace,*paths.values()]);out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf-8')
        selected=profile(a.edition)
        if sys.platform!='linux':raise Unavailable('Original source-built SingleR execution requires permitted Linux/WSL')
        source_tree(a.edition,paths);before=sources(a.edition)
        prefix=workspace/'runtime/.pixi/envs/default';library=workspace/'library'
        for suffix in ['toml','lock']:
            if pin(workspace/'runtime'/('pixi.'+suffix))!=pin(HERE/'dependencies'/(a.edition+'.'+suffix)):
                raise ValueError('Exact selected dependency manifest/lock differs')
        prefix_files=inventory(prefix);library_files=inventory(library)
        ready=dict(schema='omicsmatic-original-singler-provider-v1',edition=a.edition,workspace=str(workspace),
          r_prefix=str(prefix),library=str(library),source_paths=paths,provider=str(out),adapter_sources=before)
        for name in ['home','tmp']:(out/name).mkdir()
        control=dict(edition=a.edition,r_version=selected['r_version'],library=str(library),
          base_library=str(prefix/'lib/R/library'),source_paths=paths,
          package_versions={n:v['version'] for n,v in selected['packages'].items()})
        save(out/'control.json',control)
        runner=out/'audit-entry.R'
        runner.write_text('a<-commandArgs(TRUE);c<-jsonlite::fromJSON(a[[1]],simplifyVector=FALSE);source(a[[2]]);singler.audit(c,a[[3]])\n',encoding='utf-8')
        command=[str(prefix/'bin/Rscript'),'--vanilla',str(runner),str(out/'control.json'),str(HERE/'AuditNamespace.R'),str(out/'complete-namespace-audit.rds')]
        with (out/'audit.stdout').open('xb') as so,(out/'audit.stderr').open('xb') as se:
            child=subprocess.run(command,cwd=out,env=environment(ready,out),stdin=subprocess.DEVNULL,stdout=so,stderr=se)
        save(out/'PROCESS.json',dict(command=command,exit_code=child.returncode))
        if child.returncode:raise ValueError('Complete original namespace/DLL audit failed; inspect complete logs')
        if inventory(prefix)!=prefix_files or inventory(library)!=library_files or sources(a.edition)!=before:
            raise ValueError('Protected code/runtime changed during adoption audit')
        source_tree(a.edition,paths)
        for name,value in [('r-prefix-files',prefix_files),('private-library-files',library_files)]:
            save(out/(name+'.json'),value);ready[name]=pin(out/(name+'.json'))
        save(out/'READY.json',ready)
        save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_SINGLER_PROVIDER_NOT_ANALYSIS',edition=a.edition,
          provider=pin(out/'READY.json'),source_or_dependency_algorithms_patched=False,new_family_credit=0,whole_goal_complete=False))
        print('PASS_ORIGINAL_SINGLER_PROVIDER_NOT_ANALYSIS',a.edition,flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        label='SKIP3_SINGLER_RUNTIME_UNAVAILABLE' if code==3 else 'FAIL2_SINGLER_RUNTIME_UNADMITTED'
        print(label,repr(error),ACTION,flush=True)
        if owned:
            try:
                save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
                (out/'WARNINGS.md').write_text(label+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf-8')
            except BaseException as recording:print('FAIL2_RUNTIME_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
        return code

if __name__=='__main__':raise SystemExit(main())
