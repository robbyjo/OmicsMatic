# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the public original-runtime binding boundary with actual R probes and real skip/overwrite controls."""
import argparse,json,os,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--packages',type=Path)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_RSUBREAD_RUNTIME_BINDING_VALIDATION',analysis_admitted=False,implementation_credit=False,full_adapter_available=False,whole_goal_complete=False,runs={})
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());assert build['status']=='passed-JDK-only-Rsubread-original-runtime-binding-API-build';jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        base=ROOT/'build/literature-review/rsubread-runtime';java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
        profiles=[('2260','reference-setup-3',Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe'),Path('C:/Program Files/R/R-4.6.1')),
                  ('2232-R441','r441-reported-build-1',base/'r441-setup-1/R441/root/bin/x64/Rscript.exe',base/'r441-setup-1/R441/root')]
        for edition,setup,rscript,rhome in profiles:
            original=json.loads((base/setup/'COMPLETED.json').read_bytes());lib=(a.packages/edition/'R-lib').resolve() if a.packages else ROOT/original['private_library']['path']
            request=dict(schema_version=1,mode='BIND',edition=edition,out=str(out/(edition+'-bound')),rscript=str(rscript),r_home=str(rhome),r_library=str(lib))
            path=out/(edition+'.request.json');save(path,request);command=[java,'-Xmx256m','-jar',str(jar),str(path)]
            run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=240,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
            (out/(edition+'.stdout')).write_bytes(run.stdout);(out/(edition+'.stderr')).write_bytes(run.stderr);save(out/(edition+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==0,(edition,run.stderr.decode(errors='replace'))
            receipt=json.loads((out/(edition+'-bound/COMPLETED.json')).read_bytes());assert receipt['status']=='completed-original-runtime-bound-only' and receipt['runtime_bound'] and not receipt['analysis_admitted']
            binding=json.loads((out/(edition+'-bound/binding.json')).read_bytes());assert len(binding['guarded_symbols'])>=10 and len(binding['runtime_files'])>1000
            state['runs'][edition]=dict(receipt=pin(out/(edition+'-bound/COMPLETED.json')),runtime_files=len(binding['runtime_files']),guarded_symbols=len(binding['guarded_symbols']))
        template=json.loads((out/'2260.request.json').read_bytes())
        negatives=[('forbidden',dict(allow_execution=False),3),('missing-executable',dict(rscript=str(out/'unavailable.exe')),3),('mismatched-edition',dict(edition='2232-R441'),3),('missing-private-library',dict(r_library=str(out/'missing-library')),3)]
        for name,changes,expected in negatives:
            request=dict(template,**changes);request['out']=str(out/name);path=out/(name+'.request.json');save(path,request);command=[java,'-Xmx256m','-jar',str(jar),str(path)]
            run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=120,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
            (out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==expected,(name,run.stderr.decode(errors='replace'))
            receipt=json.loads((out/name/'COMPLETED.json').read_bytes());assert not receipt['runtime_bound'] and not receipt['analysis_admitted'] and receipt['status'].startswith('SKIP3') and not (out/name/'worker').exists()
        # Deliberately corrupt only NEW private copies; source packages remain untouched.
        original_lib=Path(template['r_library']);before={q.relative_to(original_lib).as_posix():pin(q) for q in original_lib.rglob('*') if q.is_file()}
        for name,shadow in [('changed-private-package',False),('shadow-Matrix-library',True)]:
            fixture=out/(name+'-fixture/R-lib');shutil.copytree(original_lib,fixture)
            if shadow:
                (fixture/'Matrix').mkdir();(fixture/'Matrix/DESCRIPTION').write_bytes(b'Package: Matrix\nVersion: 0.0.0\n')
            else:
                path=fixture/'Rsubread/DESCRIPTION';path.write_bytes(path.read_bytes()+b'\nIntentionalFixtureMutation: must be rejected before R starts\n')
            request=dict(template,out=str(out/name),r_library=str(fixture));path=out/(name+'.request.json');save(path,request);command=[java,'-Xmx256m','-jar',str(jar),str(path)]
            run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/(name+'.stdout')).write_bytes(run.stdout);(out/(name+'.stderr')).write_bytes(run.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=run.returncode));assert run.returncode==3,(name,run.stderr.decode(errors='replace'))
            receipt=json.loads((out/name/'COMPLETED.json').read_bytes());assert receipt['status'].startswith('SKIP3') and not receipt['runtime_bound'] and not (out/name/'worker').exists()
        assert all(pin(original_lib/n)==v for n,v in before.items())
        existing=out/'existing';existing.mkdir();(existing/'keep.txt').write_bytes(b'preserve-exactly\n');request=dict(template,out=str(existing));path=out/'existing.request.json';save(path,request);run=subprocess.run([java,'-jar',str(jar),str(path)],capture_output=True,timeout=30);(out/'existing.stdout').write_bytes(run.stdout);(out/'existing.stderr').write_bytes(run.stderr);assert run.returncode==2 and {q.name:q.read_bytes() for q in existing.iterdir()}=={'keep.txt':b'preserve-exactly\n'}
        assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        state.update(status='passed-two-public-original-Rsubread-runtime-bindings-six-real-skips-and-existing-output-preservation',artifact=build['artifact'],real_skips=6,exported_original_packages_used=a.packages is not None,known_package_mutation_and_shadow_import_rejected_before_R=True,existing_output_preserved=True,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as e:state['error']=repr(e);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual public-runtime/skip/preservation checks')
    raise SystemExit(main())
