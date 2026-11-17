#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Stream all six public managed bundles from compact evidence into standalone portable CLI controls."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,time,traceback,zipfile
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
def pin(path):
    sha=hashlib.sha256();size=0
    with path.open('rb') as stream:
        for raw in iter(lambda:stream.read(1048576),b''):sha.update(raw);size+=len(raw)
    return dict(bytes=size,sha256=sha.hexdigest())
def write(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def member(name):
    assert name and not name.startswith('/') and '\\' not in name and all(part not in ['','.','..'] for part in name.split('/'))
    return name
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['archive','jar','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--java',required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py')
    try:
        assert sys.platform=='win32';archive=args.archive/'managed-capture-evidence.zip';before=pin(archive);assert before==json.loads((args.archive/'ARCHIVED.json').read_bytes())['archive'];jar_pin=pin(args.jar);rows=[]
        with zipfile.ZipFile(archive) as source:
            index=json.loads(source.read('INDEX.json'));assert len(source.namelist())==len(set(source.namelist()))
            for permission in ['reuse','install']:
                for mode in ['conda','apptainer','conda-apptainer']:
                    name=permission+'-'+mode;case=out/name;case.mkdir();prefix='cases/'+name+'/bundle/';refs={key[len(prefix):]:row for key,row in index['files'].items() if key.startswith(prefix)}
                    manifest=json.loads(source.read(refs['manifest.json']['member']));marker=json.loads(source.read(refs['COMPLETED.json']['member']));assert marker['user_analysis_admitted'] is True and marker['manifest']==refs['manifest.json']['pin'];assert set(refs)==set(manifest['files'])|{'manifest.json','COMPLETED.json'};target=case/'bundle.zip'
                    with zipfile.ZipFile(target,'x',allowZip64=True) as saved:
                        for directory in manifest['directories']:saved.writestr(member(directory)+'/',b'')
                        for relative,row in sorted(refs.items()):
                            member(relative);info=zipfile.ZipInfo(relative);info.compress_type=zipfile.ZIP_STORED if relative.startswith('objects/') else zipfile.ZIP_DEFLATED;sha=hashlib.sha256();size=0
                            with source.open(row['member']) as old,saved.open(info,'w',force_zip64=True) as new:
                                for raw in iter(lambda:old.read(1048576),b''):size+=len(raw);sha.update(raw);new.write(raw)
                            assert dict(bytes=size,sha256=sha.hexdigest())==row['pin'],relative
                    archive_pin=pin(target);write(case/'ARCHIVED.json',dict(archive=archive_pin,files={key:row['pin'] for key,row in refs.items()},directories=manifest['directories'],source_virtual_prefix=prefix))
                    invocation=json.loads(source.read(refs['invocation.json']['member']));assert not Path(invocation['owned_root']).exists()
                    command=[args.java,'-jar',str(args.jar.resolve()),'snakemake-tools','verify','--bundle',str(target),'--out',str(case/'verification')];env=os.environ.copy();env['PATH']='';env['PYTHONPATH']='';start=time.monotonic()
                    with (case/'stdout').open('xb') as so,(case/'stderr').open('xb') as se:process=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=600)
                    row=dict(case=name,command=command,exit_code=process.returncode,seconds=round(time.monotonic()-start,3),archive=archive_pin);write(case/'PROCESS.json',row);assert process.returncode==0,(name,'Inspect retained actual portable public-command diagnostics');rows.append(row);print(name+' PORTABLE PASS',flush=True)
        assert pin(archive)==before and pin(args.jar)==jar_pin
        write(out/'COMPLETED.json',dict(status='PASS_SIX_PUBLIC_MANAGED_STANDALONE_ZIP_VERIFICATIONS_WITHOUT_PRODUCER_PATHS',cases=rows,source_archive=before,jar=jar_pin,path_empty=True,engine_executed=False,producer_paths_available=False,original_archive_unchanged=True,new_catalog_credit=0,whole_goal_complete=False))
    except BaseException as error:write(out/'FAILURE.json',dict(error=repr(error),traceback=traceback.format_exc(),required_action='Retain the original compact evidence and every partial standalone archive/report. Correct the named archive or reader discrepancy and retry NEW paths; do not rerun scientific analyses because export failed.'));raise
if __name__=='__main__':main()
