#!/usr/bin/env python3
"""Explicitly provision the tested private FIMO5.5.9 Windows reference build.

This helper reuses the exact authenticated local reference build, retains upstream
licenses/provenance, verifies source archives, and runs actual P/q scientific probes.
It does not install Cygwin/WSL/Docker or build an untested alternate runtime.
"""
from __future__ import annotations
import argparse,csv,datetime as dt,hashlib,json,math,os,platform,re,shutil,subprocess,tempfile,urllib.request,uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
LOCK=ROOT/'src/main/resources/org/jlinalg/omics/fimo-runtime-lock.json'
MARKER='.jlinalg-fimo.json'
REFERENCE_MARKER='.jlinalg-fimo-reference.json'
DECLARATION='provenance/intended-use.json'
REMOVED_ENV=('LD_PRELOAD','LD_LIBRARY_PATH','CYGWIN','MSYSTEM','LIBRARY_PATH','MEME_ETC_DIR','MEME_BIN_DIR','MEME_DIR')
RECOVERY=('Required action: on this tested Windows x86_64 profile, provide the unchanged sealed reference build, '
          'the pinned source archive cache and an explicit educational/research/nonprofit declaration. Run '
          'python tools/literature/install_fimo.py --home NEW_PRIVATE_HOME --reference-home REFERENCE_HOME '
          '--cache SOURCE_CACHE --intended-use educational-research-nonprofit --allow-install '
          '(add --allow-download only if source-archive downloads are permitted). The reference is normally '
          'build/omics-fimo-5.5.9-gnurx-reference and the source cache is build/literature-review/fimo-runtime. '
          'Existing or changed homes are preserved. If this exact reference/runtime is absent or installation '
          'is forbidden, SKIP the upstream analysis. Native motif-scan can perform its documented P-value subset '
          'without installation; q-value analyses require a permitted validated original runtime. For another '
          'host, use the official MEME installation guide and complete a new profile validation before admission. '
          'No alternate Linux/WSL/Docker runtime has been validated by this installer.')

def digest(path):
    with Path(path).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def contained(home,name):
    if not isinstance(name,str) or not re.fullmatch(r'[A-Za-z0-9._/-]+',name) or any(x in ('','.','..') for x in name.split('/')):raise ValueError('Unsafe profile member: '+str(name))
    member=home.joinpath(*name.split('/'))
    if member.is_symlink() or any(p.is_symlink() for p in member.parents if p!=home.parent):raise ValueError('Symbolic runtime member or parent is unsupported')
    if not member.resolve().is_relative_to(home.resolve()):raise ValueError('Runtime member escapes private home')
    return member
def verify_files(home,files):
    if not isinstance(files,dict) or not files or len(files)>64:raise ValueError('Invalid runtime file map')
    for name,sha in files.items():
        if not isinstance(sha,str) or not re.fullmatch('[0-9a-f]{64}',sha) or digest(contained(home,name))!=sha:raise ValueError('Runtime integrity failed: '+str(name))
def inventory(home,marker):
    found=set()
    for member in home.rglob('*'):
        if member.is_symlink():raise ValueError('Symbolic runtime member is unsupported')
        if member.is_file() and member!=marker:found.add(member.relative_to(home).as_posix())
    return found
def validate(home,lock):
    marker=home/MARKER
    if not marker.is_file() or marker.stat().st_size>1_048_576:raise ValueError('Missing or oversized runtime marker')
    record=json.loads(marker.read_text(encoding='utf-8'))
    for key in ('tool','version','profile','eligibility','executable'):
        if record.get(key)!=lock[key]:raise ValueError('Runtime profile mismatch: '+key)
    if record.get('runtime_lock_sha256')!=digest(LOCK):raise ValueError('Runtime lock differs from the current project')
    files=record.get('files');expected=dict(lock['reference_files'])
    if not isinstance(files,dict) or set(files)!=set(expected)|{DECLARATION}:raise ValueError('Unexpected runtime member map')
    if any(files[n]!=h for n,h in expected.items()):raise ValueError('Authenticated runtime member hash changed')
    verify_files(home,files)
    declaration_path=home/DECLARATION
    if declaration_path.stat().st_size>65536:raise ValueError('Oversized intended-use declaration')
    declaration=json.loads(declaration_path.read_text(encoding='utf-8'))
    if declaration.get('intended_use')!=lock['eligibility']:raise ValueError('Missing permitted intended-use declaration')
    if inventory(home,marker)!=set(files):raise ValueError('Runtime contains unlisted files')
    return record
def fetch(artifact,cache,allowed):
    target=cache/artifact['archive']
    if not target.exists():
        if not allowed:raise RuntimeError('Pinned source archive absent and download not permitted: '+str(target))
        cache.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='fimo-download-',dir=cache) as temporary:
            partial=Path(temporary)/'archive'
            with urllib.request.urlopen(artifact['url'],timeout=180) as source,partial.open('wb') as out:shutil.copyfileobj(source,out)
            if digest(partial)!=artifact['sha256']:raise ValueError('Downloaded source archive differs from the pinned SHA-256')
            if target.exists():raise FileExistsError('Archive appeared concurrently; preserve both attempts and retry')
            shutil.copyfile(partial,target)
    if digest(target)!=artifact['sha256']:raise ValueError('Cached source archive integrity failed; original bytes are preserved: '+str(target))
    return target
def native_path(path):
    path=Path(path).resolve()
    if not re.fullmatch('[A-Za-z]:',path.drive):raise ValueError('This Cygwin profile requires local drive-letter paths; UNC paths are unsupported')
    return '/cygdrive/'+path.drive[0].lower()+'/'+path.as_posix()[3:]
def environment(home):
    result=os.environ.copy();result['PATH']=str(home/'bin')+';'+str(Path(os.environ['SystemRoot'])/'System32');result['LC_ALL']='C'
    for key in REMOVED_ENV:result.pop(key,None)
    return result
def table(path):
    with path.open(encoding='utf-8',newline='') as f:return list(csv.DictReader((x for x in f if x.strip() and not x.startswith('#')),delimiter='\t'))
def rounded(expected,actual):
    value=float(actual);radius=.5*10**(math.floor(math.log10(abs(value)))-2) if value else 0
    if not math.isfinite(value) or abs(expected-value)>radius+abs(expected)*1e-12:raise ValueError('Original scientific probe differs: '+repr((expected,actual)))
def smoke(home,work):
    work.mkdir();mf=work/'probe.meme';fa=work/'probe.fa'
    mf.write_text('MEME version 4\nALPHABET= ACGT\nMOTIF A8 polyA\nletter-probability matrix: alength= 4 w= 8 nsites= 100\n'+'.97 .01 .01 .01\n'*8,newline='\n');fa.write_text('>probe\nAAAAAAAA\n',newline='\n')
    binary=home/'bin/fimo.exe';env=environment(home);records=[]
    for name,args in [('version',['--version']),('text',['--text','--thresh','1']),('q-full',['--oc',native_path(work/'q-full'),'--thresh','1']),('q-filter',['--oc',native_path(work/'q-filter'),'--qv-thresh','--thresh','.001'])]:
        command=[str(binary),*args]
        if name!='version':command+=['--bfile','--uniform--','--motif-pseudo','.1',native_path(mf),native_path(fa)]
        r=subprocess.run(command,env=env,cwd=work,capture_output=True,timeout=120);(work/(name+'.stdout')).write_bytes(r.stdout);(work/(name+'.stderr')).write_bytes(r.stderr)
        if r.returncode!=0:raise RuntimeError('Original '+name+' probe failed: '+r.stderr.decode(errors='replace')[-4000:])
        if name=='version':
            if r.stdout.decode().strip()!='5.5.9':raise ValueError('Original FIMO version mismatch')
        else:
            rows=table(work/(name+'.stdout') if name=='text' else work/name/'fimo.tsv');expected=1 if name=='q-filter' else 2
            if len(rows)!=expected:raise ValueError('Original probe hit count differs: '+name)
            top=[row for row in rows if row['strand']=='+'];assert len(top)==1;rounded(1/65536,top[0]['p-value'])
            if name!='text':rounded(1/32768,top[0]['q-value'])
            elif any(row['q-value'] for row in rows):raise ValueError('Original text mode unexpectedly produced q-values')
        records.append(dict(name=name,command=command,exit_code=r.returncode,stdout_sha256=digest(work/(name+'.stdout')),stderr_sha256=digest(work/(name+'.stderr'))))
    return records

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,required=True);p.add_argument('--reference-home',type=Path,default=ROOT/'build/omics-fimo-5.5.9-gnurx-reference');p.add_argument('--cache',type=Path,default=ROOT/'build/literature-review/fimo-runtime')
    p.add_argument('--intended-use',choices=['educational-research-nonprofit']);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');p.add_argument('--report-dir',type=Path)
    a=p.parse_args(argv);a.home=a.home.resolve();a.reference_home=a.reference_home.resolve();a.cache=a.cache.resolve()
    report=a.report_dir or a.cache/('install-attempt-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S')+'-'+uuid.uuid4().hex[:8]);report=report.resolve();report.mkdir(parents=True,exist_ok=False)
    status=dict(status='running',home=str(a.home),reference_home=str(a.reference_home),allow_install=a.allow_install,allow_download=a.allow_download,installer_sha256=digest(__file__),runtime_lock_sha256=digest(LOCK))
    try:
        lock=json.loads(LOCK.read_text(encoding='utf-8'))
        if platform.system()!='Windows' or platform.machine().lower() not in ('amd64','x86_64'):raise RuntimeError('This exact authenticated FIMO profile is only validated on Windows x86_64')
        if a.home.exists():
            record=validate(a.home,lock);status.update(status='existing-profile-verified',marker_sha256=digest(a.home/MARKER),files=len(record['files']));return 0
        if not a.allow_install:raise RuntimeError('Private installation is not allowed; no runtime was created')
        if a.intended_use!=lock['eligibility']:raise RuntimeError('Explicit educational/research/nonprofit intended-use declaration is required for original MEME terms')
        source_marker=a.reference_home/REFERENCE_MARKER
        if not source_marker.is_file() or digest(source_marker)!=lock['reference_marker_sha256']:raise RuntimeError('The exact authenticated local reference runtime is absent or changed')
        verify_files(a.reference_home,lock['reference_files'])
        if inventory(a.reference_home,source_marker)!=set(lock['reference_files']):raise ValueError('Reference runtime contains unlisted files')
        archives=[fetch(x,a.cache,a.allow_download) for x in lock['source_archives']];status['source_archive_sha256']={x.name:digest(x) for x in archives}
        a.home.parent.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='.jlinalg-fimo-install-',dir=a.home.parent) as temporary:
            staged=Path(temporary)/'runtime';staged.mkdir()
            for name,expected in lock['reference_files'].items():
                target=contained(staged,name);target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(contained(a.reference_home,name),target)
                if digest(target)!=expected:raise ValueError('Reference runtime changed during copy: '+name)
            declaration=dict(intended_use=a.intended_use,source='Explicit --intended-use installer option',recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),license_url=lock['license_url'],scope='Local educational/research/nonprofit use; no commercial or redistribution permission inferred')
            (staged/DECLARATION).write_text(json.dumps(declaration,indent=2)+'\n')
            records=smoke(staged,report/'science-smoke');files=dict(lock['reference_files']);files[DECLARATION]=digest(staged/DECLARATION)
            marker=dict(schema_version=1,status='installed-original-engine-science-smoke-passed',tool=lock['tool'],version=lock['version'],profile=lock['profile'],eligibility=lock['eligibility'],executable=lock['executable'],runtime_lock_sha256=digest(LOCK),files=files,smoke_tests=records,host_boundaries=lock['host_imports'],source_cache=str(a.cache),source_archive_sha256=status['source_archive_sha256'],scope=lock['reference_scope'])
            (staged/MARKER).write_text(json.dumps(marker,indent=2)+'\n');validate(staged,lock)
            if a.home.exists():raise FileExistsError('Destination appeared concurrently; existing home is preserved')
            staged.rename(a.home)
        validate(a.home,lock);status.update(status='installed-and-science-smoke-validated',marker_sha256=digest(a.home/MARKER),files=len(files));return 0
    except Exception as failure:
        status.update(status='skipped-dependency-unavailable',error=str(failure),required_action=RECOVERY)
        (report/'WARNINGS.md').write_text('# FIMO ANALYSIS SKIPPED - DEPENDENCY UNAVAILABLE\n\n'+str(failure)+'\n\n'+RECOVERY+'\n',encoding='utf-8')
        print('SKIPPED FIMO DEPENDENCY: '+str(failure)+'\n'+RECOVERY+'\nDiagnostics: '+str(report/'WARNINGS.md'),file=__import__('sys').stderr);return 3
    finally:
        (report/'status.json').write_text(json.dumps(status,indent=2)+'\n',encoding='utf-8');print(json.dumps(dict(status=status['status'],report=str(report))),flush=True)
if __name__=='__main__':raise SystemExit(main())
