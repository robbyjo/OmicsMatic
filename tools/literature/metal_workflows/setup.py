#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Privately prepare unchanged original METAL 2020-05-05 in NEW workspace output."""
import argparse,hashlib,json,os,platform,shutil,stat,subprocess,sys,time,traceback,urllib.request,zipfile
from pathlib import Path,PurePosixPath
ROOT=Path(__file__).resolve().parents[3]
COMMIT='e2253cc3901df8403a331bd725d4d9fe1edfb19f'
ARCHIVE_SHA='090cdaa8593812ec05484a1011e623f543235e49b2d10e9467b15d27c05f74f9'
ARCHIVE_BYTES=1055637
ACTION=('SKIP3: original runtime preparation/dependent analysis skipped. Supply a permitted '
 'Linux x86_64 host, Python 3.9+, CMake/CTest 3.1+, a C++11 compiler, make and zlib '
 'development files; install them only where the operating environment permits, '
 'or leave the dependent analysis skipped. --archive accepts an existing pinned '
 'official archive if network access is unavailable. FAIL2: no runtime admitted; '
 'retain all logs and partials, correct the named failure, and retry NEW output. '
 'Original source headers restrict redistribution: keep source, archive, notice, '
 'compiled executable and complete execution bundles private. This helper changes '
 'no original source and performs no system-wide installation or scientific analysis.')

class Prerequisite(Exception):pass

def require(ok,why):
 if not ok:raise ValueError(why)

def save(path,value):path.write_bytes((json.dumps(value,indent=2)+'\n').encode())

def pin(path):
 with path.open('rb') as stream:
  digest=hashlib.sha256()
  for block in iter(lambda:stream.read(1<<20),b''):digest.update(block)
 return dict(bytes=path.stat().st_size,sha256=digest.hexdigest())

def tool(raw,label):
 found=shutil.which(raw)
 if found is None:raise Prerequisite('Missing permitted '+label+': '+raw)
 return str(Path(found).resolve())

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--archive',type=Path);parser.add_argument('--cmake',default='cmake');parser.add_argument('--ctest',default='ctest');parser.add_argument('--cxx',default='g++');parser.add_argument('--jobs',type=int,default=2);args=parser.parse_args();out=None
 try:
  if sys.version_info<(3,9):raise Prerequisite('Python 3.9+ is required')
  if platform.system()!='Linux' or platform.machine() not in ['x86_64','amd64']:raise Prerequisite('Prepare the original runtime inside permitted Linux x86_64/WSL, not a substituted historical Windows edition')
  require(1<=args.jobs<=64,'Choose 1..64 explicit build jobs');target=args.out.absolute();require(target.resolve().is_relative_to(ROOT/'build'),'Choose NEW private workspace build output');target.mkdir(parents=True,exist_ok=False);out=target
  (out/'WARNINGS.txt').write_text(ACTION+'\n',encoding='utf-8');cmake=tool(args.cmake,'CMake');ctest=tool(args.ctest,'CTest');cxx=tool(args.cxx,'C++11 compiler');tool('make','make')
  archive=out/'original-private.zip'
  if args.archive:
   source=args.archive.resolve();require(source!=archive.resolve(),'Archive input overlaps output');shutil.copyfile(source,archive)
  else:
   try:
    request=urllib.request.Request('https://codeload.github.com/statgen/METAL/zip/'+COMMIT,headers={'User-Agent':'OmicsMatic-private-runtime-setup'})
    with urllib.request.urlopen(request,timeout=60) as response,archive.open('xb') as stream:
     total=0
     while True:
      block=response.read(1<<20)
      if not block:break
      total+=len(block);require(total<=ARCHIVE_BYTES,'Official archive exceeds its pinned size');stream.write(block)
   except (OSError,TimeoutError) as e:raise Prerequisite('Official archive download unavailable/forbidden; supply --archive with the pinned official archive: '+str(e)) from e
  require(pin(archive)==dict(bytes=ARCHIVE_BYTES,sha256=ARCHIVE_SHA),'Original official commit archive identity differs; do not refresh the pin')
  source=out/'source';source.mkdir();prefix='METAL-'+COMMIT+'/'
  with zipfile.ZipFile(archive) as z:
   require(len(z.namelist())==len(set(z.namelist())),'Duplicate archive members');total=0
   for entry in z.infolist():
    require(entry.filename.startswith(prefix),'Unexpected original source root');name=entry.filename[len(prefix):]
    if not name:continue
    path=PurePosixPath(name);require(not path.is_absolute() and '..' not in path.parts and '\\' not in name,'Unsafe original archive path');require(not stat.S_ISLNK(entry.external_attr>>16),'Linked original archive member');total+=entry.file_size;require(total<=32<<20,'Original extraction exceeds capacity');destination=source.joinpath(*path.parts)
    if entry.is_dir():destination.mkdir(parents=True,exist_ok=True)
    else:destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(z.read(entry))
  before={p.relative_to(source).as_posix():pin(p) for p in source.rglob('*') if p.is_file()};require(len(before)==136,'Original source population differs');save(out/'SOURCE-FILES.json',before)
  header=(source/'metal/Main.cpp').read_text(encoding='utf-8');notice=header[:header.index('#include')];require('may not be redistributed' in notice,'Original restriction notice missing');(out/'NOTICE-UPSTREAM.txt').write_text(notice,encoding='utf-8')
  env=dict(os.environ,LC_ALL='C',LANG='C',CXX=cxx,GIT_CEILING_DIRECTORIES=str(out.resolve()),PATH='/usr/bin:/bin')
  for key in ['LD_PRELOAD','LD_LIBRARY_PATH','CMAKE_TOOLCHAIN_FILE','CMAKE_PROJECT_INCLUDE','CMAKE_PROJECT_INCLUDE_BEFORE','CXXFLAGS','CFLAGS','CPPFLAGS','LDFLAGS','CPATH','CPLUS_INCLUDE_PATH','LIBRARY_PATH','CMAKE_PREFIX_PATH','CMAKE_MODULE_PATH','PKG_CONFIG_PATH','MAKEFLAGS','CMAKE_GENERATOR']:env.pop(key,None)
  def run(label,command):
   save(out/(label+'.command.json'),command);start=time.monotonic()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:result=subprocess.run(command,cwd=out,env=env,stdout=so,stderr=se,stdin=subprocess.DEVNULL,timeout=900)
   save(out/(label+'.PROCESS.json'),dict(exit_code=result.returncode,seconds=time.monotonic()-start));require(result.returncode==0,label+' failed; inspect '+str(out/(label+'.stderr'))+' and stdout before a NEW attempt')
  build=out/'native-build';installed=out/'runtime';run('cmake-version',[cmake,'--version']);run('compiler-version',[cxx,'--version'])
  run('configure',[cmake,'-S',str(source),'-B',str(build),'-DCMAKE_BUILD_TYPE=Release','-DCMAKE_INSTALL_PREFIX='+str(installed),'-DBUILD_TESTING=ON'])
  run('compile',[cmake,'--build',str(build),'--parallel',str(args.jobs)]);run('official-tests',[ctest,'--test-dir',str(build),'--output-on-failure']);run('install',[cmake,'--install',str(build)])
  after={p.relative_to(source).as_posix():pin(p) for p in source.rglob('*') if p.is_file()};require(after==before,'Original source bytes or membership changed during build; no runtime admitted')
  # This upstream release installs into its native build/bin, ignoring the prefix.
  # Preserve the upstream behavior and copy only its unchanged executable privately.
  targets=(build/'install_manifest.txt').read_text().splitlines();require(targets and all(Path(p).resolve().is_relative_to(out.resolve()) for p in targets),'An upstream install target escaped the private workspace')
  compiled=build/'bin/metal';require(compiled.is_file() and os.access(compiled,os.X_OK),'Compiled executable missing/unavailable');binary=installed/'bin/metal';binary.parent.mkdir(parents=True);shutil.copy2(compiled,binary);require(pin(binary)==pin(compiled) and os.access(binary,os.X_OK),'Private packaged executable differs/unavailable')
  runtime=dict(release='2020-05-05',commit=COMMIT,executable=str(binary.resolve()),executable_sha256=pin(binary)['sha256'],notice=str((out/'NOTICE-UPSTREAM.txt').resolve()),source_archive_sha256=ARCHIVE_SHA,profile_scope='Runtime preparation only; adapter/paper analysis qualification is separate')
  (out/'RUNTIME.tsv').write_bytes(('key\tvalue\n'+''.join(k+'\t'+v+'\n' for k,v in sorted(runtime.items()))).encode())
  receipt=dict(status='PASS_PRIVATE_UNCHANGED_ORIGINAL_METAL_RUNTIME_PREPARED',runtime=runtime,binary=pin(binary),original_source_files_unchanged=136,official_tests_passed=True,source_functions_patched=False,system_installation=False,scientific_analysis_executed=False,redistribution_permitted=False,host=dict(system=platform.system(),machine=platform.machine(),release=platform.release()))
  save(out/'COMPLETED.json',receipt);print(json.dumps(receipt),flush=True);print(ACTION,flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,(Prerequisite,PermissionError)) else 2;failure=dict(status='SKIP3_DEPENDENT_RUNTIME_AND_ANALYSIS_SKIPPED' if code==3 else 'FAIL2_NO_RUNTIME_ADMITTED',error=repr(e),detail=traceback.format_exc(),required_action=ACTION)
  if out is not None:save(out/'FAILURE.json',failure)
  print(failure['status']+': '+str(e)+'\n'+ACTION,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
