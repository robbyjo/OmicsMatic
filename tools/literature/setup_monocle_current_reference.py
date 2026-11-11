# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install exact original Monocle3 1.4.27 into a new private library; admit no user analysis."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT, pin, save
from setup_chipseeker_reference import inventory


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--allow-install',action='store_true')
    args=parser.parse_args();out=args.out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists()
    out.mkdir(parents=True)
    libraries=[Path(p) for p in json.loads((ROOT/'build/literature-review/monocle-runtime/proxy-setup-1/process.json').read_bytes())['libraries']]
    libraries.insert(0,ROOT/'build/literature-review/monocle-runtime/reference-setup-1/R-lib')
    before=inventory(libraries);save(out/'BASES-BEFORE.json',before)
    action=('Read installer.stdout, installer.stderr and R-RESULT.json. Preserve this attempt. Where installation is permitted, '
        'provision the named dependency or private compiler/helper cause and retry into a NEW private library; do not alter '
        'existing libraries or substitute a Monocle edition silently. Skip dependent analysis until exact original namespace, '
        'typed workflow and scientific controls pass. Reference installation alone admits no user analysis.')
    result=dict(status='SKIP3_MONOCLE_REFERENCE_UNAVAILABLE',analysis_admitted=False,required_action=action)
    bindings={}
    try:
        if not args.allow_install:
            raise RuntimeError('Installation is not authorized; dependent Monocle analyses skipped')
        health_path=ROOT/'build/literature-review/monocle-runtime/compiler-health-3/HEALTH.json'
        health=json.loads(health_path.read_bytes())
        assert health['status']=='compiled-and-loaded-private-CXX20-R-extension-without-shell-warnings'
        compiler=Path(health['compiler_root']);host_bin=Path(health['make_root'])
        intake=ROOT/'build/literature-review/monocle-runtime/source-intake-1/1.4.27'
        receipt=json.loads((intake/'SOURCE.json').read_bytes())
        assert all(pin(intake/'source'/n)==v for n,v in receipt['source_members'].items())
        dependency_root=ROOT/'build/literature-review/monocle-runtime/current-dependency-intake-2'
        dependency_receipt=json.loads((dependency_root/'SOURCE.json').read_bytes())
        overrides={}
        extra_bindings=[dependency_root/'SOURCE.json']
        for name,key in [('BPCells','BPCells-win.binary'),('speedglm','speedglm-source')]:
            row=dependency_receipt['records'][key]
            for field in ['archive','description']:
                path=ROOT/row[field]['path'];assert pin(path)=={k:row[field][k] for k in ['bytes','sha256']};extra_bindings.append(path)
            overrides[name]=dict(package=name,version=row['version'],type=row['type'],url=row['url'],
                archive=str(ROOT/row['archive']['path']),description=str(ROOT/row['description']['path']))
        override_path=out/'DECLARED-AUTHOR-OVERRIDES.json';save(override_path,overrides);extra_bindings.append(override_path)
        script=ROOT/'tools/literature/install_monocle_current_reference.R'
        for path in [Path(__file__).resolve(),script,intake/'SOURCE.json',health_path,*extra_bindings]:
            bindings[path.relative_to(ROOT).as_posix()]=pin(path)
            target=out/'source-snapshot'/path.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes())
        save(out/'STARTED.json',dict(source_pins=bindings,libraries=list(map(str,libraries)),original_source=receipt,
            compiler_health=health,analysis_admitted=False))
        env=dict(os.environ,LC_ALL='C',LANG='C',LANGUAGE='en',MSYSTEM='MSYS',R_CUSTOM_TOOLS_SOFT=compiler.as_posix(),
            R_CUSTOM_TOOLS_PATH=(compiler/'bin').as_posix()+';'+host_bin.as_posix(),R_MAKEVARS_USER=health['Makevars'],TMPDIR=str(host_bin.parent.parent/'tmp'))
        env['PATH']=str(compiler/'bin')+os.pathsep+str(host_bin)+os.pathsep+env['PATH']
        command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out/'R-lib'),str(out),str(intake/'source'),str(override_path),*map(str,libraries)]
        with (out/'installer.stdout').open('xb') as a,(out/'installer.stderr').open('xb') as b:
            run=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=2400)
        save(out/'process.json',dict(command=command,exit_code=run.returncode))
        report=json.loads((out/'R-RESULT.json').read_bytes());result.update(report)
        if run.returncode or report['status']!='INSTALLED_ORIGINAL_MONOCLE3_REFERENCE_ONLY':
            raise RuntimeError(report.get('detail','Exact original namespace/constructor health did not pass'))
    except Exception as error:
        result.update(status='SKIP3_MONOCLE_REFERENCE_UNAVAILABLE',detail=str(error))
    finally:
        after=inventory(libraries);save(out/'BASES-AFTER.json',after)
        result.update(preexisting_libraries_unchanged=before==after,preexisting_file_count=sum(map(len,before.values())),source_pins=bindings)
        if before!=after:
            result.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED',detail='Inspect before/after inventories')
        if any(pin(ROOT/n)!=v for n,v in bindings.items()):
            result.update(status='FAIL2_REFERENCE_SETUP_SOURCE_CHANGED',detail='Setup source changed during attempt')
        save(out/'RESULT.json',result)
        (out/'WARNINGS.md').write_text('# '+result['status']+'\n\n'+result.get('detail','')+'\n\nRequired action: '+action+'\n',encoding='utf8')
    print(json.dumps({k:v for k,v in result.items() if k!='source_pins'}),flush=True)
    return 0 if result['status']=='INSTALLED_ORIGINAL_MONOCLE3_REFERENCE_ONLY' else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/library guards')
    raise SystemExit(main())
