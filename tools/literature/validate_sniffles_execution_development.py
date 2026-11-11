# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise development CLI refusals and real owned Linux processes, without SV credit.

The --capture-process mode archives regular files and special-node metadata only;
it never follows links or opens FIFOs from intentionally failed process controls.
"""
import argparse, hashlib, json, os, stat, subprocess, sys, traceback, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT/'build/literature-review/sniffles-runtime'
DISTRO = 'JLinAlg-Omics-SAIGE-20261026'


def fingerprint(raw): return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
def pin(path): return fingerprint(path.read_bytes())
def save(path, value): path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf8', newline='\n')
def linux(path):
    path=path.resolve(); assert path.drive.lower()=='d:'
    return '/mnt/d/'+path.as_posix()[3:]


def capture(root, target):
    assert sys.platform=='linux' and root.is_absolute() and root.is_dir() and not root.is_symlink() and not target.exists()
    pending=[root]; nodes={}; files={}
    while pending:
        parent=pending.pop()
        with os.scandir(parent) as stream:
            for item in stream:
                path=Path(item.path); name=path.relative_to(root).as_posix(); info=item.stat(follow_symlinks=False)
                assert len(nodes)<10000
                node=dict(mode=stat.S_IMODE(info.st_mode))
                if stat.S_ISDIR(info.st_mode): node['kind']='directory'; pending.append(path)
                elif stat.S_ISLNK(info.st_mode): node.update(kind='symlink-not-followed', target=os.readlink(path))
                elif stat.S_ISFIFO(info.st_mode): node['kind']='fifo-not-opened'
                elif stat.S_ISREG(info.st_mode):
                    assert info.st_size<=33554432
                    descriptor=os.open(path,os.O_RDONLY|os.O_NOFOLLOW)
                    with os.fdopen(descriptor,'rb') as stream:
                        start=os.fstat(stream.fileno()); assert stat.S_ISREG(start.st_mode) and (start.st_dev,start.st_ino)==(info.st_dev,info.st_ino)
                        raw=stream.read(33554433); end=os.fstat(stream.fileno())
                    assert len(raw)<=33554432 and (start.st_size,start.st_mtime_ns,start.st_ctime_ns)==(end.st_size,end.st_mtime_ns,end.st_ctime_ns)
                    assert len(raw)==start.st_size
                    node.update(kind='regular', pin=fingerprint(raw)); files['files/'+name]=raw
                else: raise RuntimeError('Unexpected special node: '+name)
                nodes[name]=node
    receipt=json.loads(files['files/COMPLETED.json'])
    assert any(n['kind']=='symlink-not-followed' for n in nodes.values())
    assert any(n['kind']=='fifo-not-opened' for n in nodes.values()) or any(c['name']=='fifo' and c['status']=='SKIP3_FIFO_CREATION_UNAVAILABLE_ON_WORKSPACE_FILESYSTEM' for c in receipt['controls'])
    files['nodes.json']=(json.dumps(nodes,sort_keys=True,indent=2)+'\n').encode()
    with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name, raw in sorted(files.items()):
            entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16
            archive.writestr(entry,raw)
    print('Captured regular process-control files and nonfollowing special-node metadata')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path)
    parser.add_argument('--capture-process',type=Path);parser.add_argument('--capture-out',type=Path);args=parser.parse_args()
    if args.capture_process:
        assert args.capture_out is not None; capture(args.capture_process,args.capture_out); return 0
    assert args.out is not None
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNIFFLES_EXECUTION_DEVELOPMENT_CONTROLS',implementation_credit=False,SV_calling_adapter_available=False,whole_goal_complete=False,cases=[],process_controls=[])
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'; before=pin(canonical)
    tools=ROOT/'tools/literature'; source=tools/'java/org/jlinalg/cli/SnifflesProcessControls.java'
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),source]}
    runtimes={
        'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
        'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],
        'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']}
    def call(home,command,want,timeout=120):
        home.mkdir(); save(home/'command.json',command)
        run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=timeout)
        (home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr)
        save(home/'process.json',dict(command=command,exit_code=run.returncode,expected_exit_code=want))
        assert run.returncode==want,(home,run.returncode,run.stderr.decode(errors='replace'))
        return run
    try:
        rejected=out/'incomplete-original';rejected.mkdir();save(rejected/'COMPLETED.json',dict(status='FAIL2_CONSTRUCTED_NO_RESULT',analysis_admitted=False))
        rejected_pin=pin(rejected/'COMPLETED.json')
        for label,build,compiler in [
            ('javac21','execution-build-12','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe'),
            ('javac25','execution-build-25-3','C:/Program Files/Common Files/Oracle/Java/javapath/javac.exe')]:
            built=json.loads((BASE/build/'COMPLETED.json').read_bytes());assert built['status']=='PASS_ISOLATED_JAVA17_SNIFFLES_EXECUTION_BUILD_ONLY'
            assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items())
            jar=BASE/build/'sniffles-execution-development.jar';assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']}
            for platform,prefix in runtimes.items():
                home=out/(label+'-'+platform);home.mkdir(); path=linux if platform=='linux21' else lambda p:str(p.resolve())
                entry=[*prefix,'-cp',path(jar)]
                for name,clazz,flags,want,receipt_name in [
                    ('runtime-missing','SnifflesRuntimeCli',['--root',path(home/'missing-runtime'),'--out',path(home/'binding')],3,'binding'),
                    ('reject-failed-batch','SnifflesVerifyCli',['--result',path(rejected),'--out',path(home/'verification')],2,'verification'),
                    ('workflow-missing-request','SnifflesWorkflowCli',['--request',path(home/'absent-request.json')],2 if platform=='linux21' else 3,None)]:
                    run=call(home/name,[*entry,'org.jlinalg.cli.'+clazz,*flags],want)
                    assert b'ACTION' in run.stderr and not (home/name/'COMPLETED.json').exists()
                    receipt=None
                    if receipt_name:
                        folder=home/receipt_name;record=json.loads((folder/'COMPLETED.json').read_bytes())
                        assert not record.get('analysis_admitted',False) and not record.get('verification_passed',False)
                        assert 'ACTION' in (folder/'WARNINGS.md').read_text(encoding='utf8')
                        receipt=pin(folder/'COMPLETED.json')
                    state['cases'].append(dict(compiler=label,platform=platform,name=name,exit_code=want,receipt=receipt))
                for name,clazz,flags in [
                    ('existing-binding','SnifflesRuntimeCli',['--root',path(home/'missing-runtime'),'--out',path(home/'binding')]),
                    ('existing-verification','SnifflesVerifyCli',['--result',path(rejected),'--out',path(home/'verification')])]:
                    target=home/('binding' if clazz=='SnifflesRuntimeCli' else 'verification')
                    original={p.name:pin(p) for p in target.iterdir()};call(home/name,[*entry,'org.jlinalg.cli.'+clazz,*flags],2)
                    assert original=={p.name:pin(p) for p in target.iterdir()}
                    state['cases'].append(dict(compiler=label,platform=platform,name=name,exit_code=2,existing_output_preserved=True))
            compiled=out/(label+'-process-compile');classes=out/(label+'-process-classes');classes.mkdir()
            call(compiled,[compiler,'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(source)],0)
            process_root=out/(label+'-process-controls')
            call(out/(label+'-process-run'),[*runtimes['linux21'],'-cp',linux(jar)+':'+linux(classes),'org.jlinalg.cli.SnifflesProcessControls',linux(process_root)],0)
            process=json.loads((process_root/'COMPLETED.json').read_bytes())
            assert process['status']=='PASS_SNIFFLES_PROCESS_CONTROLS_WITH_DECLARED_SKIPS_ONLY' and process['controls_count']==12 and process['unrelated_sentinel_preserved'] and not process['scientific_analysis_admitted']
            assert process['passed_controls']+process['skipped_controls']==12 and process['passed_controls']>=11
            assert json.loads((process_root/'unrelated-sentinel/CLEANUP.json').read_bytes())['owned_terminal']
            archived=out/(label+'-process-evidence.zip')
            call(out/(label+'-process-capture'),['wsl','-d',DISTRO,'--exec','/usr/bin/python3.12','-B','-P','-s','-S',linux(Path(__file__)),'--capture-process',linux(process_root),'--capture-out',linux(archived)],0)
            state['process_controls'].append(dict(compiler=label,controls=12,passed=process['passed_controls'],skipped=process['skipped_controls'],receipt=pin(process_root/'COMPLETED.json'),archive=pin(archived),archive_name=archived.name,special_nodes_not_opened=True))
            print(label+': public CLI refusals; '+str(process['passed_controls'])+' real owned-process controls passed; '+str(process['skipped_controls'])+' explicitly skipped',flush=True)
        assert len(state['cases'])==30 and len(state['process_controls'])==2
        assert pin(rejected/'COMPLETED.json')==rejected_pin and pin(canonical)==before and all(pin(ROOT/n)==v for n,v in pins.items())
        state.update(status='PASS_SNIFFLES_EXECUTION_DEVELOPMENT_PUBLIC_REFUSALS_AND_PROCESS_CONTROLS_WITH_DECLARED_SKIPS_ONLY',source_pins=pins,canonical_executable_unchanged=before,calling_workflow_scientifically_validated=False,
            passed_process_controls=sum(c['passed'] for c in state['process_controls']),skipped_process_controls=sum(c['skipped'] for c in state['process_controls']),
            superseded_FIFO_claim='The prior process-controls-1 and execution-development-validation-2 receipts counted upstream mkfifo errno95 as a generic expected child failure. They did not exercise FIFO rejection. Their raw logs are retained; no FIFO coverage credit is taken.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable development controls or special-node guards')
    raise SystemExit(main())
