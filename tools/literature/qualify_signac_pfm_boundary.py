# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Qualify a constructor-boundary correction with exact source inverse and fresh native capture verification."""
import argparse,json,subprocess,zipfile
from pathlib import Path
from validate_signac_scan_workflow import ROOT,BASE,CLI,read,save,compare_original
from validate_mashr_native import JAVA21,linux,pin
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='boundary-correction-capture-verification-in-progress'))
    try:
        old=read(BASE/'scan-workflow-build-3/BUILD.json');new=read(BASE/'scan-workflow-build-4/BUILD.json');workflow=read(BASE/'scan-workflow-matrix-3/COMPLETED.json');native=read(BASE/'scan-pfm-boundary-audit-after-1/COMPLETED.json');fixed=read(BASE/'scan-pfm-boundary-after-1/COMPLETED.json')
        assert workflow['artifact']==old['artifact'] and native['artifact']==fixed['artifact']==new['artifact'] and fixed['result_admitted'] and fixed['captured_verifications']==3
        assert len(native['platform_validation'])==3 and all(v['tests']==v['passed']==211 and v['skipped']==0 for v in native['platform_validation']) and all(pin(ROOT/n)==v for n,v in new['source_pins'].items())
        source='src/main/java/org/jlinalg/sequence/SignacPfmConstructionAudit.java';before=(BASE/'scan-workflow-build-3/source-snapshot'/source).read_bytes();current=(ROOT/source).read_bytes()
        earlier=b'double value=matrix[position][base];if(value>Integer.MAX_VALUE)throw new IOException("Source constructor cannot retain this count as a finite R integer");\n                        long expected=(long)value;'
        corrected=b'double value=matrix[position][base];long expected=(long)value;if(expected>Integer.MAX_VALUE)throw new IOException("Source constructor cannot retain this count as a finite R integer");\n                        '
        assert before.count(earlier)==current.count(corrected)==1 and current.replace(corrected,earlier,1)==before
        for artifact in [old['artifact'],new['artifact']]:assert pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']}
        with zipfile.ZipFile(ROOT/old['artifact']['path']) as x,zipfile.ZipFile(ROOT/new['artifact']['path']) as y:
            assert set(x.namelist())==set(y.namelist()) and y.testzip() is None;changed=[n for n in x.namelist() if x.read(n)!=y.read(n)];assert changed==['org/jlinalg/sequence/SignacPfmConstructionAudit.class'],changed
        save(out/'BOUNDARY-DELTA.json',dict(status='verified-exact-post-truncation-integer-capacity-correction',before_artifact=old['artifact'],after_artifact=new['artifact'],before_source=old['source_pins'][source],after_source=pin(ROOT/source),source_inverse_exact=True,changed_JAR_entries=changed,all_CLI_R_sources_and_other_classes_unchanged=True,original_R_algorithms_reexecuted=False))
        names=['tools/literature/qualify_signac_pfm_boundary.py',*workflow['source_pins']];sources={n:pin(ROOT/n) for n in names};assert all(sources[n]==v for n,v in workflow['source_pins'].items());save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        original=read(BASE/'scan-original-7/original.json');jar=ROOT/new['artifact']['path'];platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        for i,run in enumerate(workflow['actual_original_fits']):
            target=ROOT/run['analysis_path'];expected=next(v['result']['value'] for v in original['results'] if v['id']==run['case'] and v['profile']==run['profile']);compare_original(target,expected,run['profile']=='1.6.0-source')
            for platform,java,convert in platforms:
                name=f'{i:03d}-'+platform;path=out/(name+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));argv=list(map(str,[*java,'-Xmx768m','-cp',convert(jar),CLI,'--request',convert(path)]));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.stderr.decode('utf8',errors='replace')[-1200:])
            print('verified unchanged original fit '+str(i).zfill(3)+' '+run['profile']+'/'+run['case'],flush=True)
        assert len(workflow['actual_original_fits'])==75 and workflow['captured_verifications']==225 and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in new['source_pins'].items())
        result=dict(status='passed-full-original-scan-enrichment-user-workflow-matrix',source_pins=sources,artifact=new['artifact'],actual_original_fits=workflow['actual_original_fits'],retained_original_failure_gates=workflow['retained_original_failure_gates'],captured_verifications=225,source_inputs_unchanged=True,all_75_completed_original_fits_reused_without_reexecution=True,original_workflow_checkpoint=pin(BASE/'scan-workflow-matrix-3/COMPLETED.json'),exact_boundary_delta=pin(out/'BOUNDARY-DELTA.json'),additional_fixed_boundary_fit=True,independent_MOODS_KDE_RNG_replay=False,full_Signac_workflow_available=False,catalog_implementation_credit=False);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],captured_verifications=225,original_R_fits_reexecuted=False)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_BOUNDARY_WORKFLOW_QUALIFICATION',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source-delta/capture checks')
    main()
