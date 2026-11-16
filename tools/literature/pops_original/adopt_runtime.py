#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Adopt complete selected original PoPS software bytes; no model is fitted."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,os,platform,re,stat,subprocess,time,traceback
from install_runtime import PINS,ACTION

HERE=Path(__file__).resolve().parent
def sha(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for b in iter(lambda:stream.read(1048576),b''):h.update(b)
    return h.hexdigest()
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def text(value):
    value=str(value)
    if any(c in value for c in ['\t','\r','\n','\0']):raise ValueError('Unqualified control character in runtime record')
    return value
def environment():
    result=os.environ.copy()
    for key in ['PYTHONPATH','PYTHONHOME','PYTHONSTARTUP','LD_PRELOAD','LD_AUDIT','LD_LIBRARY_PATH','OPENBLAS_CORETYPE']:result.pop(key,None)
    for key in ['OPENBLAS_NUM_THREADS','OMP_NUM_THREADS','MKL_NUM_THREADS','NUMEXPR_NUM_THREADS','BLIS_NUM_THREADS']:result[key]='1'
    result['PYTHONDONTWRITEBYTECODE']='1';return result
def nodes(root):
    result=[]
    for directory,dirs,files in os.walk(root,followlinks=False):
        for name in dirs+files:
            path=Path(directory)/name;relative=path.relative_to(root).as_posix()
            mode=path.lstat().st_mode
            if stat.S_ISLNK(mode):
                if path.is_dir() and not within(path.resolve(),root):raise ValueError('External directory link is not a closed runtime: '+str(path))
                result.append((relative,'link',text(os.readlink(path)),path))
            elif stat.S_ISDIR(mode):result.append((relative,'directory','-',path))
            elif stat.S_ISREG(mode):result.append((relative,'file','-',path))
            else:raise ValueError('Nonregular runtime entry: '+str(path))
    return sorted(result)
def within(path,root):
    try:path.relative_to(root);return True
    except ValueError:return False
def fingerprint(row):
    alias,name,kind,target,path=row
    digest=sha(path) if kind=='file' or (kind=='link' and path.is_file()) else '-'
    return [alias,kind,text(name),target,digest]
def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--installed',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=None
    try:
        if platform.system()!='Linux' or platform.machine() not in ['x86_64','amd64']:raise FileNotFoundError('Adopt the runtime inside permitted Linux x86-64/WSL')
        if not Path('/usr/bin/ldd').is_file():raise FileNotFoundError('Required permitted ldd helper is unavailable')
        a.out.absolute().mkdir(parents=True,exist_ok=False);out=a.out.resolve();(out/'WARNINGS.md').write_text('# Original runtime adoption\n\n'+ACTION+'\nComplete manifest scope is the selected environment, original source tree and explicitly resolved shared-library files, not a complete OS image. Hashes establish integrity, not author-binary authenticity.\n',encoding='utf-8')
        installed=a.installed.resolve();record=json.loads((installed/'INSTALLED.json').read_bytes())
        if record['status']!='INSTALLED_ORIGINAL_POPS_DEPENDENCIES_NOT_ADOPTED_NOT_ANALYSIS':raise ValueError('Separately completed original setup required')
        python=Path(record['python']);prefix=python.parent.parent.resolve();source=(installed/'original').resolve()
        if not python.is_file() or not os.access(python,os.X_OK):raise FileNotFoundError('Installed Python unavailable')
        if sha(installed/'SOURCE.json')!=record['source_sha256'] or sha(installed/'pixi.lock')!=record['pixi_lock_sha256']:raise ValueError('Installed source/lock changed since setup')
        sources=json.loads((installed/'SOURCE.json').read_bytes())['files'];pins=dict(PINS)
        for name,data in json.loads((HERE/'source-pins.json').read_bytes()).items():pins[name]=(data['commit'],data['sha256'])
        if set(sources)!=set(pins):raise ValueError('Original source membership differs')
        for name,(commit,digest) in pins.items():
            if sources[name]['commit']!=commit or sources[name]['sha256']!=digest or sha(source/name)!=digest:raise ValueError('Selected original source differs: '+name)
        env=environment();command=[str(python),'-I','-B',str(HERE/'runtime_health.py'),str(source)];start=time.monotonic()
        with (out/'health.stdout').open('xb') as so,(out/'health.stderr').open('xb') as se:r=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
        save(out/'HEALTH-PROCESS.json',dict(command=command,exit_code=r.returncode,seconds=round(time.monotonic()-start,3),thread_limits={k:env[k] for k in ['OPENBLAS_NUM_THREADS','OMP_NUM_THREADS','MKL_NUM_THREADS','NUMEXPR_NUM_THREADS','BLIS_NUM_THREADS']}))
        if r.returncode:raise RuntimeError('Original runtime health failed; retain raw health consoles')
        health=json.loads((out/'health.stdout').read_bytes());assert health['status']=='PASS_EXACT_ORIGINAL_POPS_RUNTIME_HEALTH_NOT_ANALYSIS'
        print('Inventorying complete environment and source membership',flush=True)
        roots={'env':prefix,'source':source};before={alias:nodes(path) for alias,path in roots.items()}
        rows=[(alias,name,kind,target,path) for alias,items in before.items() for name,kind,target,path in items]
        # Inspect actual ELF shared objects and interpreter, including their external loader/libc edges.
        print('Inventory entries: '+str(len(rows))+'; inspecting ELF candidates',flush=True)
        elf=set();python_real=python.resolve()
        for alias,name,kind,target,path in rows:
            if alias!='env' or kind=='directory' or ('.so' not in path.name and not name.startswith('bin/python')):continue
            if not path.is_file():continue
            with path.open('rb') as f:magic=f.read(4)
            if magic==b'\x7fELF':elf.add(path.resolve())
        def dependencies(path):
            process=subprocess.run(['/usr/bin/ldd',str(path)],env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=60)
            output=(process.stdout+process.stderr).decode('utf-8',errors='replace')
            if process.returncode or 'not found' in output:raise FileNotFoundError('Unresolved installed ELF dependency '+str(path)+': '+output)
            found=set()
            for line in output.splitlines():
                for match in re.finditer(r'(?<!\S)(/[^\s()]+)',line):
                    q=Path(match.group(1));
                    if q.is_file() and not within(q.resolve(),prefix):found.add(q)
            return str(path),output,found
        assert python_real in elf,'Actual adopted interpreter was not inspected'
        print('Inspecting dependencies of '+str(len(elf))+' unique ELF objects',flush=True)
        external=set();ldd={}
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            for path,output,found in pool.map(dependencies,sorted(elf)):ldd[path]=output;external.update(found)
        for path in sorted(external):
            rows.append(('system',str(path),'link' if path.is_symlink() else 'file',text(os.readlink(path)) if path.is_symlink() else '-',path))
        save(out/'ELF-DEPENDENCIES.json',dict(scope='Static dynamic-loader dependencies resolved for the adopted interpreter and all installed ELF shared objects; not a complete OS image or executable authenticity claim.',objects=ldd,external_files=[str(q) for q in sorted(external)]))
        print('Hashing '+str(len(rows))+' complete runtime/source/dependency entries',flush=True)
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:entries=sorted(pool.map(fingerprint,rows))
        if any(nodes(path)!=before[alias] for alias,path in roots.items()):raise ValueError('Runtime membership changed during adoption')
        manifest='root\tkind\tpath\ttarget\tsha256\n'+''.join('\t'.join(row)+'\n' for row in entries)
        (out/'runtime-files.tsv').write_text(manifest,encoding='utf-8',newline='\n')
        for name in ['SOURCE.json','pixi.toml','pixi.lock']:(out/name).write_bytes((installed/name).read_bytes())
        values=dict(profile='original-pops-runtime-v1',platform='linux-x86_64',python=str(python),environment=str(prefix),sources=str(source),manifest_sha256=sha(out/'runtime-files.tsv'),source_record_sha256=sha(out/'SOURCE.json'),health_sha256=sha(out/'health.stdout'),elf_dependencies_sha256=sha(out/'ELF-DEPENDENCIES.json'))
        with (out/'runtime.properties').open('x',encoding='utf-8',newline='\n') as f:
            for key,value in sorted(values.items()):f.write(key+'='+text(value).replace('\\','\\\\').replace(':','\\:')+'\n')
        save(out/'READY.json',dict(status='READY_ADOPTED_ORIGINAL_POPS_RUNTIME_NOT_ANALYSIS',profile='original-pops-runtime-v1',versions=health['versions'],files=len(entries),roots={k:str(v) for k,v in roots.items()},external_library_files=len(external),manifest_sha256=values['manifest_sha256'],adopter_sha256=sha(Path(__file__)),health_script_sha256=sha(HERE/'runtime_health.py'),scientific_analysis_performed=False,author_runtime_reproduced=False))
        print(json.dumps(dict(status='READY_ADOPTED_ORIGINAL_POPS_RUNTIME_NOT_ANALYSIS',out=str(out),entries=len(entries),external_library_files=len(external))),flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if out is not None:(out/('SKIPPED.json' if code==3 else 'FAILURE.json')).write_text(json.dumps(dict(status='SKIP3_ORIGINAL_POPS_ADOPTION' if code==3 else 'FAIL2_ORIGINAL_POPS_ADOPTION',reason=repr(error),required_action=ACTION),indent=2)+'\n',encoding='utf-8');(out/'traceback.txt').write_text(traceback.format_exc(),encoding='utf-8')
        print(('SKIP3' if code==3 else 'FAIL2'),repr(error),ACTION,flush=True);return code

if __name__=='__main__':raise SystemExit(main())
