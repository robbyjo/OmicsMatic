# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute an actual native chromVAR request and compare every published statistic to original R output."""
import argparse,csv,json,math,subprocess
from pathlib import Path
from prepare_chromvar_request import prepare,ROOT,BASE,save
from validate_mashr_native import JAVA21,linux,pin
CLI='org.jlinalg.cli.ChromVarRequestCli'
def read(p):return json.loads(p.read_bytes())
def scalar(v):return v[0] if isinstance(v,list) and len(v)==1 else v
def equal(got,want):
    if want in ['NA','NaN','Inf','-Inf']:assert got==want,(got,want)
    else:assert math.isfinite(float(got)) and math.isclose(float(got),float(want),rel_tol=2e-11,abs_tol=2e-12),(got,want)
def compare(target,reference):
    core=reference['core']['value'];cells=core['cell_ids'];motifs=core['motif_ids'];rows=list(csv.DictReader((target/'deviations.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t'));assert len(rows)==len(cells)*len(motifs)
    for i,motif in enumerate(motifs):
        for j,cell in enumerate(cells):
            row=rows[i*len(cells)+j];assert (row['motif_id'],row['cell_id'])==(motif,cell);equal(row['deviation'],core['deviations']['rows'][i][j]);equal(row['z'],core['z']['rows'][i][j])
    rows=list(csv.DictReader((target/'motif-summary.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t'));assert len(rows)==len(motifs)
    for i,row in enumerate(rows):assert row['motif_id']==motifs[i];equal(row['fraction_matches'],core['fractionMatches'][i]);equal(row['fraction_background_overlap'],core['fractionBackgroundOverlap'][i])
    rows=list(csv.DictReader((target/'expectations.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t'));expected=reference['effective_expectation'];assert len(rows)==len(expected)
    for i,row in enumerate(rows):assert row['peak_id']=='peak'+str(i+1);equal(row['expectation'],expected[i])
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',default='chromvar-workflow-build-1');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='actual-native-chromVAR-request-probe-in-progress'))
    try:
        build=read(BASE/a.build/'BUILD.json');jar=ROOT/build['artifact']['path'];assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};names=['tools/literature/probe_chromvar_request.py','tools/literature/prepare_chromvar_request.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        data=read(BASE/'chromvar-deviations-original-3/cases.json');case=next(v for v in data['cases'] if v['id']=='baseline');target=out/'Unicode β analysis';path=out/'request.json';save(path,prepare(data['datasets'][case['dataset']],case,'1.34.1-package',out/'Unicode α inputs',target))
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.stderr.decode('utf8',errors='replace')[-2000:])
        command('DEVIATIONS',[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path]);reference=next(v for v in read(BASE/'chromvar-deviations-original-3/original.json')['results'] if scalar(v['id'])=='baseline' and scalar(v['profile'])=='1.34.1-package');compare(target,reference)
        for platform,java,convert in [('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]:
            path=out/(platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(platform,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(path)])
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-actual-native-chromVAR-request-probe',source_pins=sources,artifact=build['artifact'],original_statistics_match=True,captured_verifications=3,native_analysis_requires_no_R=True,background_sampling_performed=False,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-actual-native-chromVAR-request-probe',captured_verifications=3)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_CHROMVAR_REQUEST_PROBE',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native workflow checks')
    main()
