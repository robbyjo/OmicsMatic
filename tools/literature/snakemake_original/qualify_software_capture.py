#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual native literal software capture, special metadata, immutable-tree and blob corruption controls."""
from pathlib import Path
import argparse,json,os,shutil,socket,stat,sys
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];sys.path.insert(0,str(HERE))
from setup_runtime import pin,write
from qualify_deployment import execute

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['compiled','native-out','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py');stage=a.native_out.absolute();assert sys.platform=='linux' and stage==stage.resolve() and not stage.exists() and not str(stage).startswith('/mnt/');stage.mkdir();compiled=json.loads((a.compiled/'COMPILED.json').read_bytes());rows=[];warnings=[]
    for name,expected in compiled['sources'].items():assert pin(ROOT/name)==expected,name
    for name in ['literal-specials','changed-file','changed-mode','changed-link','extra-member','missing-member','file-capacity','byte-capacity','changed-blob','contradictory-shared-length']:
        case=stage/name;case.mkdir();source=case/'software';source.mkdir();owned=case/'owned';owned.mkdir();objects=owned/'objects';objects.mkdir();original=b'fixture\n';(source/'value').write_bytes(original);os.link(source/'value',source/'alias');outside=case/'outside';outside.write_bytes(b'not an execution dependency seal\n'*65536);(source/'payload-link').symlink_to(str(outside));(source/'container-root-link').symlink_to('/etc/alternatives/awk');(source/'dangling').symlink_to('/missing-literal-software-target');os.mkfifo(source/'fifo');sock=socket.socket(socket.AF_UNIX);sock.bind(str(source/'s'))
        device=False
        try:os.mknod(source/'character-device',stat.S_IFCHR|0o600,os.makedev(1,3));device=True
        except PermissionError as error:warnings.append(dict(case=name,status='SKIP_OPTIONAL_DEVICE_CREATION_CONTROL',error=repr(error),required_action='Qualify this optional metadata edge on a permitted native Linux filesystem with device-node creation permission. No device stream is opened.'))
        request=case/'REQUEST.json';metadata=case/'SOFTWARE.json';values=dict(owned=str(owned),metadata=str(metadata),roots={'software':str(source)},files=2 if name=='file-capacity' else 1000,bytes=4 if name=='byte-capacity' else 16777216,file_bytes=4 if name=='byte-capacity' else 16777216);write(request,values)
        def run(label,mode,path):return execute(case,label,['/usr/bin/java','-cp',compiled['classpath'],compiled['probe'],mode,str(path)],dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC'),case,120)
        try:
            result=run('capture','software-capture',request)
            if name in ['file-capacity','byte-capacity']:assert result['exit_code']!=0 and not metadata.exists()
            else:
                assert result['exit_code']==0,name;saved=json.loads(metadata.read_bytes());nodes=saved['roots']['software']['nodes'];assert nodes['payload-link']['target']==str(outside) and not (objects/pin(outside)['sha256']).exists();assert nodes['fifo']['content'] is None and nodes['s']['content'] is None
                if device:assert nodes['character-device']['kind']=='character-device' and nodes['character-device']['device']==os.makedev(1,3)
                mode='software-live';accepted=name=='literal-specials'
                if name=='changed-file':
                    path=source/'value';before=path.stat();path.write_bytes(b'changed\n');os.utime(path,ns=(before.st_atime_ns,before.st_mtime_ns))
                elif name=='changed-mode':os.chmod(source/'value',(source/'value').stat().st_mode^stat.S_IXUSR)
                elif name=='changed-link':(source/'payload-link').rename(case/'retained-link');(source/'payload-link').symlink_to('/different-payload-root')
                elif name=='extra-member':(source/'new-member').write_bytes(b'new')
                elif name=='missing-member':(source/'value').rename(case/'retained-value')
                elif name=='changed-blob':(objects/nodes['value']['content']['sha256']).write_bytes(b'changed\n');mode='software-check'
                elif name=='contradictory-shared-length':
                    nodes['alias']['content']['bytes']+=1;saved['bytes']+=1;damaged=case/'DAMAGED.json';write(damaged,saved);values['metadata']=str(damaged);alternate=case/'DAMAGED-REQUEST.json';write(alternate,values);request=alternate;mode='software-check'
                checked=run('verify',mode,request);assert (checked['exit_code']==0)==accepted,(name,'Inspect retained native verifier diagnostics')
            rows.append(dict(case=name,device_control_executed=device,expected_capture_success=name not in ['file-capacity','byte-capacity']));print(name+' PASS',flush=True)
        finally:sock.close()
        target=out/name;target.mkdir()
        for path in case.iterdir():
            if path.is_file():shutil.copyfile(path,target/path.name)
        shutil.copytree(objects,target/'objects')
    for name,expected in compiled['sources'].items():assert pin(ROOT/name)==expected,name
    write(out/'WARNINGS.json',warnings);write(out/'COMPLETED.json',dict(status='PASS_TEN_ACTUAL_LITERAL_SOFTWARE_CAPTURE_AND_CORRUPTION_CONTROLS',cases=rows,warnings=warnings,device_controls_skipped=sum(not r['device_control_executed'] for r in rows),compiled=pin(a.compiled/'COMPILED.json'),user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False));print('PASS_TEN_LITERAL_SOFTWARE_CAPTURE_CONTROLS',flush=True)
if __name__=='__main__':main()
