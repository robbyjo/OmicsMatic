#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent portable bundle controls, including internally resealed contradictory records."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,time,traceback
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
def read(path):return json.loads(path.read_bytes())
def write(path,value):path.write_text(json.dumps(value,indent=2,sort_keys=True)+'\n',encoding='utf-8',newline='\n')
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}

def reseal(root,case):
    """Rebuild all owned metadata/blob references and physical seals in a NEW damaged copy."""
    index=read(root/'file-records.json');nodes=index['nodes'];old_objects={n[8:]:v for n,v in nodes.items() if n.startswith('objects/')}
    for path in sorted(root.rglob('*')):
        name=path.relative_to(root).as_posix()
        if path.is_file() and name not in ['manifest.json','COMPLETED.json','file-records.json','blob-index.json'] and not name.startswith('objects/'):
            assert name in nodes and nodes[name]['kind']=='file';nodes[name]['pin']=pin(path);blob=root/'objects'/nodes[name]['pin']['sha256']
            if not blob.exists():shutil.copyfile(path,blob)
    closure={}
    def add(value):
        if value is None:return
        prior=closure.setdefault(value['sha256'],value);assert prior==value
    for name in ['capture.json','working-before.json','working-after.json']:
        for family in read(root/name)['roots']:
            for value in family['items'].values():
                if value['kind']=='file':add(value['original']['pin'])
    for output in read(root/'outputs.json'):
        for member in output['members'].values():add(member['pin'])
    for name,node in list(nodes.items()):
        if name.startswith('objects/'):del nodes[name]
        elif node['kind']=='file':add(node['pin'])
    retained=case/'retained-unreferenced-copy-objects';retained.mkdir()
    for path in list((root/'objects').iterdir()):
        assert path.is_file() and path.parent.resolve()==(root/'objects').resolve()
        if path.name not in closure:path.rename(retained/path.name)
    for key,value in closure.items():
        assert pin(root/'objects'/key)==value
        nodes['objects/'+key]=dict(kind='file',pin=value,mode=old_objects[key]['mode'] if key in old_objects else 0o644,target=None,resolved=None)
    write(root/'file-records.json',index);write(root/'blob-index.json',closure)
    files={name:value for name,value in inventory(root).items() if name not in ['manifest.json','COMPLETED.json']}
    directories=sorted(p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_dir())
    write(root/'manifest.json',dict(schema_version=1,tool='Snakemake',files=files,directories=directories))
    marker=read(root/'COMPLETED.json');marker['manifest']=pin(root/'manifest.json');write(root/'COMPLETED.json',marker)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--compiled',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--bundle',type=Path,required=True);p.add_argument('--java',required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py')
    source=a.bundle.resolve();before=inventory(source);compiled=read(a.compiled/'COMPILED.json');assert pin(a.jar)=={k:compiled['baseline_jar'][k] for k in ['bytes','sha256']};assert all(pin(a.compiled/'classes'/n)==v for n,v in compiled['classes'].items());classpath=str(a.compiled.resolve()/'classes')+os.pathsep+str(a.jar.resolve());rows=[]
    try:
        cases=['unchanged-resealed','wrong-parameters','effective-conda','partial-target-dag','wrong-targets','live-owned-process','wrong-source-version','changed-output','removed-warning','changed-helper','extra-helper','missing-job-finish','unlisted-file','unlisted-directory','false-completion']
        for name in cases:
            case=out/name;case.mkdir();root=case/'bundle';shutil.copytree(source,root)
            def change(relative,modify):
                path=root/relative;value=read(path);modify(value);write(path,value)
            if name=='wrong-parameters':
                path=root/'parameters.json';raw=path.read_bytes();assert b'9007199254740993' in raw;path.write_bytes(raw.replace(b'9007199254740993',b'9007199254740992'))
            elif name=='effective-conda':change('DRIVER.json',lambda r:r['effective_policy'].__setitem__('use_conda',True))
            elif name=='partial-target-dag':change('DRIVER.json',lambda r:r['effective_policy'].__setitem__('until',['make']))
            elif name=='wrong-targets':change('COMMAND.json',lambda r:r['argv'].__setitem__(-1,'different-target'))
            elif name=='live-owned-process':change('process/process.json',lambda r:r['owned_live_after_cleanup'].append(r['owned_launcher_pid']))
            elif name=='wrong-source-version':change('DRIVER.json',lambda r:r['health']['versions'].__setitem__('snakemake','0.0.0'))
            elif name=='changed-output':
                outputs=read(root/'outputs.json');member=outputs[0]['members'][''];old=root/'objects'/member['pin']['sha256'];raw=old.read_bytes();assert raw;raw=bytes([raw[0]^1])+raw[1:];target=root/'objects'/hashlib.sha256(raw).hexdigest();target.write_bytes(raw);member['pin']=pin(target);write(root/'outputs.json',outputs)
            elif name=='removed-warning':(root/'WARNINGS.md').write_text('# Source warnings removed\n',encoding='utf-8')
            elif name=='changed-helper':
                path=root/'helpers/driver.py';path.write_bytes(path.read_bytes()+b'\n# deliberately changed captured helper\n')
            elif name=='extra-helper':
                path=root/'helpers/extra.py';path.write_text('# undeclared helper\n',encoding='utf-8');index=read(root/'file-records.json');index['nodes']['helpers/extra.py']=dict(kind='file',pin=pin(path),mode=0o644,target=None,resolved=None);write(root/'file-records.json',index)
            elif name=='missing-job-finish':
                driver=read(root/'DRIVER.json');event=root/'events'/('events-'+str(driver['driver_pid'])+'.jsonl');records=[json.loads(line) for line in event.read_text().splitlines()];old=len(records);records=[r for r in records if r.get('record',{}).get('event')!='job_finished'];assert len(records)<old
                for i,record in enumerate(records,1):record['sequence']=i
                event.write_text(''.join(json.dumps(r)+'\n' for r in records),encoding='utf-8',newline='\n');driver['collector_streams'][event.name]=pin(event);write(root/'DRIVER.json',driver)
            if name not in ['unlisted-file','unlisted-directory','false-completion']:reseal(root,case)
            if name=='unlisted-file':(root/'unexpected').write_text('Unlisted physical file\n')
            elif name=='unlisted-directory':(root/'unexpected-directory').mkdir()
            elif name=='false-completion':change('COMPLETED.json',lambda r:r.__setitem__('whole_paper_reproduced',True))
            command=[a.java,'-cp',classpath,compiled['probe'],'verify',str(root)];start=time.monotonic()
            with (case/'stdout').open('xb') as so,(case/'stderr').open('xb') as se:result=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=60)
            row=dict(case=name,exit_code=result.returncode,expected_acceptance=name=='unchanged-resealed',accepted=result.returncode==0,seconds=round(time.monotonic()-start,3),command=command);write(case/'PROCESS.json',row);rows.append(row);print(json.dumps(row),flush=True)
        assert inventory(source)==before,'Original evidence changed';write(out/'CASES.json',rows);assert all(r['accepted']==r['expected_acceptance'] for r in rows),'Inspect retained unexpected acceptance/rejection; do not admit damaged evidence'
        write(out/'COMPLETED.json',dict(status='PASS_ONE_INDEPENDENTLY_RESEALED_POSITIVE_AND_FOURTEEN_CORRUPTION_CONTROLS',cases=rows,original_bundle_unchanged=True,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False))
    except BaseException as error:write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),cases=rows,required_action='Retain all new damaged copies and original evidence. Fix the identified verification gap and qualify NEW code/bundles; never rewrite an old result or its saved reader hashes.'));raise
if __name__=='__main__':main()
