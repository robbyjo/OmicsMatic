#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal selected deployment providers and real source controls; install nothing and run no user analysis."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,os,platform,re,shutil,subprocess,sys,traceback
if not __debug__:raise RuntimeError('Adoption assertions must remain enabled')
HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from setup_deployment import ARTIFACTS,APPTAINER_LIBRARIES,APPTAINER_INSTALLER,manager_tree
from setup_runtime import pin,write
ACTION='This is provider adoption and source-control evidence, not a completed Java analysis adapter. Missing/forbidden prerequisites mean SKIP3: skip dependent analysis, restore the exact permitted manager/library or use a permitted Linux x86-64 host, then retry NEW paths. FAIL2: retain complete inputs, source logs and partial records; resolve the named integrity discrepancy. Never refresh historical hashes to admit changed software or claim an identical author/OS environment.'

def verify_source_records(source,index_path):
    if (source/'FAILURE.json').exists():raise ValueError('Failed source-control family cannot be adopted; retain it and qualify NEW source records')
    declared=json.loads(index_path.read_bytes())
    if type(declared.get('schema_version')) is not int or declared['schema_version']!=1 or not isinstance(declared.get('files'),dict):raise ValueError('Malformed complete source-control export index')
    paths=list(source.rglob('*'))
    if any(p.is_symlink() or not (p.is_file() or p.is_dir()) for p in paths):raise ValueError('Source-control export must contain ordinary files and directories only')
    expected=declared['files'];actual={p.relative_to(source).as_posix():p for p in paths if p.is_file() and p.resolve()!=index_path.resolve()}
    if set(actual)!=set(expected):raise ValueError('Complete source-control export file census differs; await successful producer/export completion or retain the damaged copy and retry NEW records')
    for name,value in expected.items():
        path=actual[name]
        if not isinstance(value,dict) or set(value)!={'bytes','sha256'} or type(value['bytes']) is not int or value['bytes']<0 or not isinstance(value['sha256'],str) or not re.fullmatch('[a-f0-9]{64}',value['sha256']) or pin(path)!=value:raise ValueError('Source-control export bytes changed or pin malformed: '+name)

def capture_process(out,name,command,environment):
    with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:r=subprocess.run(command,env=environment,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=60)
    write(out/(name+'.PROCESS.json'),dict(command=command,exit_code=r.returncode))
    if r.returncode:raise FileNotFoundError('Provider health probe failed: '+name+'; inspect '+str(out/(name+'.stderr')))

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--conda-setup',type=Path,required=True);p.add_argument('--apptainer-setup',type=Path,required=True);p.add_argument('--source-controls',type=Path,required=True);p.add_argument('--source-index',type=Path,help='Optional independent complete export index for an earlier producer without EXPORTED.json');p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=None
    try:
        if sys.platform!='linux' or platform.machine() not in ['x86_64','amd64']:raise FileNotFoundError('Selected provider adoption requires Linux x86-64')
        target=a.out.absolute()
        if target!=target.resolve() or not target.parent.is_dir() or any(target.is_relative_to(p.resolve()) or p.resolve().is_relative_to(target) for p in [a.conda_setup,a.apptainer_setup]):raise ValueError('Require a NEW canonical provider metadata directory separate from both setup prefixes and an existing parent')
        target.mkdir(exist_ok=False);out=target;(out/'WARNINGS.md').write_text('# Deployment providers: source qualification only\n\n'+ACTION+'\n',encoding='utf-8');shutil.copyfile(__file__,out/'as-run-adopter.py');snap=out/'as-run';snap.mkdir()
        for name in ['setup_deployment.py','setup_runtime.py']:shutil.copyfile(HERE/name,snap/name)
        source=a.source_controls.resolve();source_index=a.source_index.resolve() if a.source_index is not None else source/'EXPORTED.json';verify_source_records(source,source_index);shutil.copyfile(source_index,out/'SOURCE-RECORDS.json');controls=json.loads((source/'COMPLETED.json').read_bytes());assert controls['status']=='PASS_THREE_ORIGINAL_SOURCE_DEPLOYMENT_MODES_NOT_JAVA_ADAPTER' and controls['manager_trees_unchanged']
        assert {r['mode'] for r in controls['cases']}=={'conda','apptainer','conda-apptainer'}
        selected_source=json.loads((source/'SOURCE.json').read_bytes());providers={};snapshots={}
        for name,parent in [('conda',a.conda_setup),('apptainer',a.apptainer_setup)]:
            parent=parent.resolve();installed=json.loads((parent/(name.upper()+'-INSTALLED.json')).read_bytes());prefix=Path(installed['prefix']);executable=Path(installed['executable'])
            assert prefix==prefix.resolve() and executable.is_relative_to(prefix) and executable.is_file() and os.access(executable,os.X_OK)
            expected_version='conda 26.9.4\n' if name=='conda' else 'apptainer version 1.5.5\n'
            if installed['version']!=expected_version:raise FileNotFoundError('Selected '+name+' edition '+expected_version.strip()+' required at '+str(prefix)+'; found '+repr(installed['version'])+'. Restore the exact permitted selected provider and retry NEW adoption paths.')
            assert prefix==Path(selected_source['manager_installation'])/'conda' if name=='conda' else prefix==Path(selected_source['apptainer_prefix'])
            for artifact in [ARTIFACTS[name]]+(APPTAINER_LIBRARIES+[APPTAINER_INSTALLER] if name=='apptainer' else []):
                assert pin(parent/'downloads'/artifact['name'])=={k:artifact[k] for k in ['bytes','sha256']},artifact['name']
            manifest=parent/(name+'-files.json');assert pin(manifest)==installed['manifest'];declared=json.loads(manifest.read_bytes());assert declared['root']==str(prefix)
            current=manager_tree(prefix);assert current==declared['nodes'],'Installed manager changed since its bootstrap: '+name;snapshots[name]=current
            target=out/(name+'-files.json');shutil.copyfile(manifest,target)
            for suffix in ['-INSTALLED.json']:
                shutil.copyfile(parent/(name.upper()+suffix),out/(name.upper()+suffix))
            license=parent/'licenses'/(name+'-LICENSE.txt');assert pin(license)==installed['license'];shutil.copyfile(license,out/(name+'-LICENSE.txt'))
            providers[name]=dict(prefix=str(prefix),executable=str(executable),version=installed['version'].strip(),manifest=pin(target),entries=len(current),artifact=installed['artifact'],license=pin(out/(name+'-LICENSE.txt')))
        home=out/'home';home.mkdir();condarc=out/'condarc';condarc.write_text('channels:\n  - conda-forge\nchannel_priority: strict\n',encoding='utf-8');environment=dict(PATH='/usr/bin:/bin',HOME=str(home),LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',CONDARC=str(condarc),XDG_CACHE_HOME=str(out/'cache'))
        capture_process(out,'conda-info',[providers['conda']['executable'],'info','--json'],environment)
        capture_process(out,'conda-priority-show',[providers['conda']['executable'],'config','--show','channel_priority','--json'],environment)
        capture_process(out,'conda-priority-get',[providers['conda']['executable'],'config','--get','channel_priority','--json'],environment)
        info=json.loads((out/'conda-info.stdout').read_bytes());assert info['conda_version']=='26.9.4' and info['conda_prefix']==providers['conda']['prefix']
        assert json.loads((out/'conda-priority-show.stdout').read_bytes())['channel_priority']=='strict'
        capture_process(out,'apptainer-buildcfg',[providers['apptainer']['executable'],'buildcfg'],environment)
        app_prefix=Path(providers['apptainer']['prefix']);config={line.split('=',1)[0]:line.split('=',1)[1] for line in (out/'apptainer-buildcfg.stdout').read_text().splitlines() if '=' in line}
        # Generic compiled SYSCONFDIR remains /etc by upstream design; the
        # application-specific configuration and session paths are relocated.
        for key in ['BINDIR','LIBEXECDIR','SESSIONDIR','APPTAINER_CONFDIR','APPTAINER_CONF_FILE','PLUGIN_ROOTDIR']:
            if key in config:assert Path(config[key]).is_relative_to(app_prefix),(key,config[key])
        paths={};foreign={}
        for name,nodes in snapshots.items():
            prefix=Path(providers[name]['prefix'])
            for relative,node in nodes.items():
                # Package-cache payloads and the retained Debian FHS source copy
                # are sealed as data; inspect loader dependencies of active files.
                if name=='conda' and relative.startswith('pkgs/') or name=='apptainer' and relative.startswith('usr/'):continue
                if node['kind']=='file' or node['kind']=='symlink' and node['target_kind']=='file':paths[str((prefix/relative).resolve())]=name
        def elf(item):
            path,name=item
            with open(path,'rb') as f:head=f.read(20)
            if head[:4]!=b'\x7fELF':return None
            machine=int.from_bytes(head[18:20],'little' if head[5]==1 else 'big');return path,name,machine
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:objects=[x for x in pool.map(elf,paths.items()) if x is not None]
        def libraries(item):
            path,name,machine=item
            if machine!=62:return path,dict(component=name,machine=machine,not_executed='foreign ELF object'),set()
            env=dict(environment)
            if name=='apptainer':env['LD_LIBRARY_PATH']=str(app_prefix/'dependencies/usr/lib/x86_64-linux-gnu')
            r=subprocess.run(['/usr/bin/ldd',path],env=env,stdin=subprocess.DEVNULL,capture_output=True,text=True,timeout=60);text=r.stdout+r.stderr
            if 'not found' in text:raise FileNotFoundError('Missing provider ELF dependency for '+path+': '+text)
            if r.returncode and not any(x in text for x in ['not a dynamic executable','statically linked']):raise ValueError('Unexpected loader inspection failure for '+path+': '+text)
            found={Path(m.group(1)) for m in re.finditer(r'(?<!\S)(/[^\s()]+)',text) if Path(m.group(1)).is_file()}
            return path,dict(component=name,machine=machine,stdout=r.stdout,stderr=r.stderr,exit_code=r.returncode),found
        inspected={};external=set()
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            for path,value,found in pool.map(libraries,objects):inspected[path]=value;external.update(p for p in found if not any(p.resolve().is_relative_to(Path(v['prefix'])) for v in providers.values()))
        external.update(p.resolve() for p in list(external));host={}
        for path in sorted(external):host[str(path)]=dict(kind='symlink' if path.is_symlink() else 'file',mode=path.lstat().st_mode&0o7777,target=os.readlink(path) if path.is_symlink() else None,resolved=str(path.resolve()),pin=pin(path))
        write(out/'HOST-ELF.json',dict(scope='Active provider ELF objects and their resolved loader libraries; package-cache payloads and retained Debian source tree are sealed but are not treated as active programs. This is not a complete OS/kernel, dynamic plugin or runtime data-file capture.',objects=inspected,external=host))
        diagnostics=[]
        for case in controls['cases']:
            for path in (source/case['mode']/'events-execute').glob('*.jsonl'):
                for line in path.read_text().splitlines():
                    row=json.loads(line);value=row.get('record',{})
                    if value.get('levelno',0)>=30:diagnostics.append(dict(case=case['mode'],stream=path.name,sequence=row['sequence'],record=value))
        write(out/'SOURCE-DIAGNOSTICS.json',diagnostics)
        with (out/'WARNINGS.md').open('a',encoding='utf-8') as f:
            for diagnostic in diagnostics:f.write('\nWARNING: retained original diagnostic\n\n'+json.dumps(diagnostic)+'\n')
            f.write('\nOriginal Conda channel-priority diagnostics remain unchanged. Independent --show and --get outputs are retained; do not erase or replace the original warning.\n')
        for name,expected in snapshots.items():assert manager_tree(Path(providers[name]['prefix']))==expected,'Manager changed while adopting: '+name
        verify_source_records(source,source_index);metadata={p.relative_to(out).as_posix():pin(p) for p in out.rglob('*') if p.is_file()};directories=sorted(p.relative_to(out).as_posix() for p in out.rglob('*') if p.is_dir())
        write(out/'ADOPTED.json',dict(status='ADOPTED_SELECTED_DEPLOYMENT_PROVIDERS_NOT_JAVA_ANALYSIS_ADAPTER',schema_version=1,engine_profile='linux-x86_64-snakemake-9.27.1',components=providers,source_controls=dict(path=str(source),marker=pin(source/'COMPLETED.json'),source=pin(source/'SOURCE.json'),export_index=pin(source_index)),host=dict(platform=platform.platform(),machine=platform.machine(),uid=os.getuid(),effective_uid=os.geteuid()),elf_objects=len(inspected),external_paths=len(host),original_diagnostics=len(diagnostics),metadata=metadata,directories=directories,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));print('ADOPTED_SELECTED_DEPLOYMENT_PROVIDERS_NOT_JAVA_ANALYSIS_ADAPTER',flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if out is not None and out.is_dir():
            if not (out/'FAILURE.json').exists():write(out/'FAILURE.json',dict(status='SKIP3' if code==3 else 'FAIL2',error=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
            with (out/'WARNINGS.md').open('a',encoding='utf-8') as f:f.write('\n## '+('SKIP3: dependent analysis skipped' if code==3 else 'FAIL2: no provider admitted')+'\n\n'+str(error)+'\n\n'+ACTION+'\n')
        print(('SKIP3' if code==3 else 'FAIL2')+': '+repr(error)+'\n'+ACTION,file=sys.stderr,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
