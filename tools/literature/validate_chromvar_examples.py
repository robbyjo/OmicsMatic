# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute literal native examples, fresh Java25/Linux analyses and original statistical comparisons."""
import argparse,json,subprocess
from pathlib import Path
from prepare_signac_scan_request import ROOT,BASE,save
from probe_chromvar_request import compare,scalar
from validate_mashr_native import JAVA21,linux,pin
EXAMPLES=ROOT/'examples/chromvar-deviations';CLI='org.jlinalg.cli.ChromVarRequestCli'
def read(p):return json.loads(p.read_bytes())
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='literal-native-chromVAR-examples-in-progress'))
    try:
        build=read(BASE/'chromvar-workflow-build-1/BUILD.json');jar=ROOT/build['artifact']['path'];assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};names=['tools/literature/validate_chromvar_examples.py','tools/literature/prepare_chromvar_examples.py','tools/literature/probe_chromvar_request.py','docs/literature/CHROMVAR_DEVIATIONS_DEVELOPMENT.md']+[p.relative_to(ROOT).as_posix() for p in EXAMPLES.rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.stderr.decode('utf8',errors='replace')[-1800:])
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def verify(name,target):
            for platform,java,convert in platforms:
                path=out/(name+'-'+platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(name+'-'+platform,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(path)])
        runs=[]
        for name,folder,case in [('request.json','chromvar-deviations-original-3','baseline'),('legacy-request.json','chromvar-deviations-original-3','baseline'),('grouped-request.json','chromvar-extended-original-1','balanced-groups')]:
            r=read(EXAMPLES/name);target=(EXAMPLES/r['out']).resolve();assert target.is_relative_to((ROOT/'build').resolve()) and not target.exists();command(name+'-DEVIATIONS',[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',EXAMPLES/name]);reference=next(v for v in read(BASE/folder/'original.json')['results'] if scalar(v['id'])==case and scalar(v['profile'])==r['profile']);compare(target,reference);verify(name,target);runs.append(dict(request_path=(EXAMPLES/name).relative_to(ROOT).as_posix(),analysis_path=target.relative_to(ROOT).as_posix(),original_statistics_match=True,captured_verifications=3))
        command('literal-VERIFY',[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',EXAMPLES/'verify-request.json'])
        for platform,java,convert in platforms[1:]:
            r=read(EXAMPLES/'request.json');target=out/('fresh-'+platform);r['out']=convert(target)
            for field in ['counts','peak_features','cells','motif_labels','annotation_peaks','background']:r[field]=convert((EXAMPLES/r[field]).resolve())
            path=out/(platform+'-fresh-request.json');save(path,r);command(platform+'-DEVIATIONS',[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(path)]);reference=next(v for v in read(BASE/'chromvar-deviations-original-3/original.json')['results'] if scalar(v['id'])=='baseline' and scalar(v['profile'])=='1.34.1-package');compare(target,reference);verify(platform,target);runs.append(dict(analysis_path=target.relative_to(ROOT).as_posix(),actual_fresh_platform=platform,original_statistics_match=True,captured_verifications=3))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-literal-native-chromVAR-examples-and-fresh-platform-runs',source_pins=sources,artifact=build['artifact'],runs=runs,literal_DEVIATIONS_requests=3,additional_fresh_platform_runs=2,original_statistical_comparisons=5,captured_verifications=16,literal_VERIFY_request_executed=True,inputs_and_requests_unchanged=True,native_analysis_requires_no_R=True,background_sampling_performed=False,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-literal-native-chromVAR-examples-and-fresh-platform-runs',analyses=5,captured_verifications=16)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_CHROMVAR_EXAMPLES',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable literal command checks')
    main()
