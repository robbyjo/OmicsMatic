# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a declared unchanged original SMR source edition privately on Linux; no user analysis admitted."""
import argparse,json,os,shutil,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_smr_cmake import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source-home',required=True,type=Path);p.add_argument('--dependencies-home',required=True,type=Path);p.add_argument('--cmake-home',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-build',action='store_true');a=p.parse_args();out=a.out.resolve();source=a.source_home.resolve();deps=a.dependencies_home.resolve();cmake=a.cmake_home.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);receipt=json.loads((source/'SOURCE.json').read_bytes());assert all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items());dependency_report=json.loads((deps/'COMPLETED.json').read_bytes());cmake_report=json.loads((cmake/'COMPLETED.json').read_bytes());assert dependency_report['status']=='prepared-original-private-SMR-build-dependencies-only' and cmake_report['status']=='prepared-original-private-CMake-only';dependency_receipts={r['name']:json.loads((deps/r['name']/'SOURCE.json').read_bytes()) for r in dependency_report['dependencies']};assert all(pin(deps/name/'source'/n)==v for name,r in dependency_receipts.items() for n,v in r['source_members'].items());binary=ROOT/cmake_report['binary'];assert pin(binary)==cmake_report['members']['cmake/data/bin/cmake'];pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),source/'SOURCE.json',deps/'COMPLETED.json',cmake/'COMPLETED.json']};state=dict(status='SKIP3_SMR_REFERENCE_UNAVAILABLE',source_pins=pins,source_commit=receipt['commit'],analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,required_action='Read configure/build logs and compiler warnings. Supply the exact permitted compiler/CMake/dependency sources or skip dependent SMR analysis. Retry NEW paths; do not patch original functions, substitute versions, mutate existing source/dependency trees or claim source-version labels identify a paper commit. Compilation only prepares a separately licensed executable.')
    try:
        assert a.allow_build,'Source compilation not enabled';work=out/'owned-source';shutil.copytree(source/'source',work);owned=out/'owned-dependencies';owned.mkdir()
        for name in dependency_receipts:shutil.copytree(deps/name/'source',owned/name)
        version=(work/'src/config.h').read_text(encoding='utf8').split('SMR_VERSION',1)[1].split('"')[1];build=out/'build';command=[linux(binary),'-S',linux(work),'-B',linux(build),'-DCMAKE_BUILD_TYPE=Release']
        if version=='1.3.1':
            (owned/'eigen-include').mkdir();shutil.copytree(owned/'eigen',owned/'eigen-include/eigen3');command.append('-Deigen_path='+linux(owned/'eigen-include'))
        else:
            command+=['-DSMR_BLAS_BACKEND=none','-DBUILD_WITH_MKL=OFF','-DBUILD_WITH_MIMALLOC=OFF']
            for name in dependency_receipts:command.append('-DFETCHCONTENT_SOURCE_DIR_'+name.upper()+'='+linux(owned/name))
            command+=['-DCPM_SOURCE_CACHE='+linux(out/'cpm-cache'),'-DFETCHCONTENT_FULLY_DISCONNECTED=ON']
        envprefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/env','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1','TMPDIR='+linux(out)]
        def run(identifier,args,timeout):
            command=envprefix+args
            with (out/(identifier+'.stdout')).open('xb') as stdout,(out/(identifier+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout)
            expected=1 if identifier=='health' else 0;save(out/(identifier+'-process.json'),dict(command=command,exit_code=r.returncode,expected_original_exit_code=expected));assert r.returncode==expected,'Original '+identifier+' failed; inspect retained full logs'
        save(out/'STARTED.json',dict(source_pins=pins,configure_command=envprefix+command,source_commit=receipt['commit'],version=version,backend='Eigen-native/system-zlib for1.3.1; Eigen-native/pinned-zlib/fmt/spdlog for current; no mimalloc',allow_build=a.allow_build));run('configure',command,180);run('compile',[linux(binary),'--build',linux(build),'--parallel','2'],900);program=build/'smr';assert program.is_file();run('health',[linux(program),'--help'],30);stdout=(out/'health.stdout').read_bytes();assert ('Version '+version).encode() in stdout and b'flags include:' in stdout and b'--smr-multi' in stdout and b'--heidi-mtd' in stdout;assert all(pin(work/n)==v for n,v in receipt['source_members'].items());state.update(status='built-unchanged-original-SMR-reference-only',version=version,program=dict(path=program.relative_to(ROOT).as_posix(),**pin(program)),owned_source_members_unchanged=True,source_receipt=pin(source/'SOURCE.json'),original_help_exit_code=1,health_exit1_is_original_usage_only_not_analysis_success=True)
    except Exception as error:state['detail']=str(error)
    finally:
        state['original_source_and_dependencies_unchanged']=all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items()) and all(pin(deps/name/'source'/n)==v for name,r in dependency_receipts.items() for n,v in r['source_members'].items());assert state['original_source_and_dependencies_unchanged'] and all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','')+'\n\n**REQUIRED ACTION:** '+state['required_action']+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('detail',''));return 0 if state['status']=='built-unchanged-original-SMR-reference-only' else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/build/library checks')
    raise SystemExit(main())
