# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create NEW synthetic ChIPseeker inputs/requests and optionally run the selected development JAR."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True);parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--mode',choices=['ANNOTATE','NEAREST'],default='ANNOTATE');parser.add_argument('--java',default='java')
    parser.add_argument('--runtime-setup',type=Path);parser.add_argument('--rscript',type=Path);parser.add_argument('--r-library',action='append',type=Path,default=[])
    parser.add_argument('--run',action='store_true');args=parser.parse_args()
    libraries=[];rscript=None
    if args.mode=='ANNOTATE':
        if args.runtime_setup:
            setup=args.runtime_setup.resolve();result=json.loads((setup/'RESULT.json').read_bytes())
            if result['status']!='INSTALLED_ORIGINAL_CHIPSEEKER_REFERENCE_ONLY' or not result['preexisting_libraries_unchanged']:
                parser.error('Use a completed original reference setup with unchanged existing libraries')
            libraries=[str(setup/'R-lib'),*json.loads((setup/'STARTED.json').read_bytes())['libraries']]
            command=json.loads((setup/'process.json').read_bytes())['command'];rscript=command[0]
        elif args.rscript and args.r_library:
            rscript=str(args.rscript.resolve());libraries=[str(p.resolve()) for p in args.r_library]
        else:parser.error('ANNOTATE needs --runtime-setup or explicit --rscript and one or more --r-library paths')
    out=args.out.resolve()
    if out.exists():parser.error('Output already exists; preserve it and choose a NEW directory')
    out.mkdir(parents=True);inputs=out/'inputs';inputs.mkdir();source=Path(__file__).resolve().parent/'synthetic-inputs'
    for path in source.iterdir():
        if path.is_file():shutil.copyfile(path,inputs/path.name)
    save=lambda path,value:path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf-8',newline='\n')
    save(out/'nearest-request.json',dict(schema_version=1,options={'input':'inputs/peaks.tsv','features':'inputs/features.tsv',
         'reference-build':'SYNTHETIC_CHIPSEEKER_CONTROL_V1','coordinates':'one-based-inclusive','out':'nearest-result'}))
    if args.mode=='ANNOTATE':
        provenance=json.loads((inputs/'PROVENANCE.json').read_bytes())
        save(out/'annotation-request.json',dict(schema_version=1,mode='ANNOTATE',version='1.48.1',peaks='inputs/peaks.tsv',txdb='inputs/synthetic-txdb.sqlite',
            txdb_sha256=provenance['fixture_sqlite']['sha256'],
            reference_build='SYNTHETIC_CHIPSEEKER_CONTROL_V1',coordinates='one-based-inclusive',rscript=rscript,r_libraries=libraries,
            analyses=[dict(id='genomic-annotation',level='transcript',tss_region=[-2000,1000],select_annotation_prefixes=['Promoter'])],out='annotation-result',timeout_seconds=300))
    request=out/('annotation-request.json' if args.mode=='ANNOTATE' else 'nearest-request.json')
    command=[args.java,'-Xmx768m','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-jar',str(args.jar.resolve()),'chipseeker-tools',args.mode,'--request',str(request)]
    save(out/'command.json',command)
    (out/'README.txt').write_text('SYNTHETIC CONTROLS ONLY: no real genome, sample, peak experiment or paper is reproduced.\n'
        'Read analysis-status.json and WARNINGS.md in the selected result. Require result_admitted=true.\n'
        'One source-unmapped query remains explicit in the ledger. Raw upstream geneChr/geneStrand may be numeric codes.\n'
        'The annotation example selects literal Promoter labels only after full-population annotation; it defines no enhancer rule.\n'
        'This helper installs nothing. Missing/forbidden dependencies require SKIP3 and the stated recovery action.\n',encoding='utf-8')
    print(json.dumps(command,ensure_ascii=True),flush=True)
    if not args.run:return 0
    with (out/'stdout.log').open('xb') as stdout,(out/'stderr.log').open('xb') as stderr:
        process=subprocess.Popen(command,cwd=out,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=os.name!='nt')
        try:code=process.wait(timeout=700)
        except (KeyboardInterrupt,subprocess.TimeoutExpired):
            if process.poll() is None:
                if os.name=='nt':subprocess.run(['taskkill.exe','/PID',str(process.pid),'/T','/F'],stdout=stderr,stderr=stderr,check=False)
                else:
                    import signal
                    os.killpg(process.pid,signal.SIGTERM)
                    try:process.wait(timeout=10)
                    except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL)
            raise
    save(out/'process.json',dict(command=command,exit_code=code,software_installed=False))
    print('Exit '+str(code)+'. Read '+str(out/'stderr.log')+' and the result WARNINGS.md.',flush=True)
    return code


if __name__=='__main__':raise SystemExit(main())
