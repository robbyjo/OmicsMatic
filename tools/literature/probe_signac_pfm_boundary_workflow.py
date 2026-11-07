# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the actual scanner at R's fractional integer boundary, preserving before/after captures."""
import argparse,copy,csv,json,subprocess
from pathlib import Path
from prepare_signac_scan_request import ROOT,BASE,prepare,save
from validate_mashr_native import JAVA21,linux,pin
def read(p):return json.loads(p.read_bytes())
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--expected-code',type=int,choices=[0,2],required=True);p.add_argument('--before',type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists() and '/' not in a.build and '\\' not in a.build;out.mkdir();save(out/'INCOMPLETE.json',dict(status='actual-PFM-boundary-workflow-probe-in-progress'))
    try:
        build=read(BASE/a.build/'BUILD.json');jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());names=['tools/literature/probe_signac_pfm_boundary_workflow.py','tools/literature/prepare_signac_scan_request.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        data=copy.deepcopy(read(BASE/'scan-original-7/cases.json'));case=next(c for c in data['cases'] if c['id']=='matched');data['datasets'][case['dataset']]['pfms'][0]['rows'][0][0]=2147483647.5;target=out/'analysis';save(out/'request.json',prepare(data,case,'1.17.1-package',out/'inputs',target));cli='org.jlinalg.cli.SignacScanRequestCli'
        def command(name,argv,code):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1200:])
        command('FIT',[JAVA21/'java.exe','-Xmx768m','-cp',jar,cli,'--request',out/'request.json'],a.expected_code)
        with (target/'worker/effective-pfms.tsv').open(encoding='utf8',newline='') as f:rows=list(csv.DictReader(f,delimiter='\t'))
        assert float(rows[0]['A'])==2147483647 and read(target/'manifest.json')['process_exit_code']==0
        if a.expected_code==2:
            assert read(target/'analysis-status.json')['status']=='failed' and not (target/'enrichment.tsv').exists() and 'cannot retain this count as a finite R integer' in (target/'WARNINGS.md').read_text(encoding='utf8')
        else:
            assert read(target/'analysis-status.json')['status']=='completed-with-warnings';diagnostics=read(target/'diagnostics.json');assert diagnostics['upstream_PFM_changed_counts']==1 and diagnostics['maximum_PFM_count_change']==.5 and 'UPSTREAM_PFM_INTEGER_COERCION' in (target/'WARNINGS.md').read_text(encoding='utf8')
            assert a.before is not None;before=a.before.resolve();assert before.is_relative_to(BASE.resolve())
            for name in ['enrichment-original.tsv','rng-before.tsv','rng-after.tsv','effective-pfms.tsv','motif-presence.mtx','motif-positions.tsv']:
                assert (target/'worker'/name).read_bytes()==(before/'analysis/worker'/name).read_bytes(),name
            platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
            for platform,java,convert in platforms:
                path=out/(platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(platform,[*java,'-Xmx768m','-cp',convert(jar),cli,'--request',convert(path)],0)
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='captured-actual-upstream-valid-fractional-integer-boundary',source_pins=sources,artifact=build['artifact'],actual_R_fit_completed=True,declared_count=2147483647.5,effective_count=2147483647,expected_project_exit=a.expected_code,result_admitted=a.expected_code==0,compared_identical_upstream_results_with_before_capture=a.expected_code==0,captured_verifications=3 if a.expected_code==0 else 0,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='captured-actual-upstream-valid-fractional-integer-boundary',exit=a.expected_code,admitted=a.expected_code==0)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ACTUAL_PFM_BOUNDARY_PROBE',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable boundary checks')
    main()
