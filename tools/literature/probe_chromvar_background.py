# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run one actual upstream background request and compare every original index/RNG integer, then portable VERIFY."""
import argparse,json,os,subprocess
from pathlib import Path
from prepare_chromvar_background_request import prepare,save,ROOT,BASE
from validate_mashr_native import JAVA21,linux,pin
CLI='org.jlinalg.cli.ChromVarBackgroundRequestCli'
def comparison(analysis,original):
    lines=(analysis/'background.tsv').read_text(encoding='utf8').splitlines();matrix=[line.split('\t')[1:] for line in lines[1:]];assert matrix==original['background']
    for suffix,key in [('before','rng_before'),('after','rng_after'),('public-after','rng_after')]:
        values=[int(line.split('\t')[1]) for line in (analysis/f'worker/rng-{suffix}.tsv').read_text().splitlines()[1:]];assert values==original[key]
    return True
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--build',default='background-workflow-build-1');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='actual-background-request-probe-in-progress'))
    try:
        build=json.loads((BASE/a.build/'BUILD.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());names=['tools/literature/probe_chromvar_background.py','tools/literature/prepare_chromvar_background_request.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.stderr.decode('utf8',errors='replace')[-1800:])
        data=json.loads((BASE/'background-original-controls-1/cases.json').read_bytes());case=next(v for v in data['cases'] if v['id']=='baseline');analysis=out/'Unicode β analysis';prepare(out/'input-request',analysis,data['datasets']['baseline'],case,'1.34.1-package');command('BACKGROUND',[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',out/'input-request/request.json']);reference=next(v for v in json.loads((BASE/'background-original-controls-1/original.json').read_bytes())['results'] if v['id']=='baseline' and v['profile']=='1.34.1-public-Matrix-interface');comparison(analysis,reference)
        verify=out/'verify.json';save(verify,dict(schema_version=1,mode='VERIFY',analysis=analysis.name));platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
        for name,java,convert,separator in platforms:command(name,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(verify)])
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-actual-original-background-request-and-portable-capture-probe',source_pins=sources,artifact=build['artifact'],original_background_indices_and_RNG_match=True,captured_verifications=3,Unicode_paths_and_IDs=True,analysis_path=analysis.relative_to(ROOT).as_posix(),independent_native_RNG_replay=False,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-actual-original-background-request-and-portable-capture-probe',original_indices_and_RNG_match=True,captured_verifications=3)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ACTUAL_BACKGROUND_REQUEST_PROBE',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original background request checks')
    main()
