# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install exact original biomaRt packages into NEW private libraries; preserve every shared R library."""
import argparse,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,BASES,environment,pin,save
from setup_chipseeker_reference import inventory

BASE_LIBRARIES=[ROOT/'build/literature-review/chipseeker-runtime/reference-setup-3/R-lib',ROOT/'build/literature-review/chipseeker-runtime/reference-setup-2/R-lib',ROOT/'build/omics-enrichment-r',*BASES]
ACTION='SKIP3: skip dependent original biomaRt reference analysis until the exact package/dependencies load and source-body checks pass. Where installation is permitted correct the named archive/package/DLL/network/permission issue in NEW private paths. Preserve installer logs, errors and original R libraries. This helper runs package health only; it grants no implementation credit and retrieves no gene annotation.'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--intake',required=True,type=Path);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/biomart-runtime') and not out.exists();out.mkdir(parents=True);source=Path(__file__).resolve();script=ROOT/'tools/literature/install_biomart_reference.R';sources=[source,script];pins={f.relative_to(ROOT).as_posix():pin(f) for f in sources};state=dict(status='SKIP3_BIOMART_REFERENCE_UNAVAILABLE',required_action=ACTION,source_pins=pins,versions={},analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    for f in sources:(out/f.name).write_bytes(f.read_bytes())
    before=inventory(BASE_LIBRARIES);save(out/'BASES-BEFORE.json',before);save(out/'STARTED.json',dict(status='RUNNING_PRIVATE_ORIGINAL_BIOMART_REFERENCE_PREPARATION',source_pins=pins))
    try:
        assert a.allow_install,'Installation not authorized for this invocation';assert os.name=='nt' and RSCRIPT.is_file(),'Exact captured original reference uses Windows R4.6.1';intake=json.loads((a.intake/'COMPLETED.json').read_bytes());assert intake['primary_roles']==7
        for version in ['2.68.0','2.60.1']:
            reference=intake['artifacts'][version+'-windows'];assert reference['status']=='PASS_ORIGINAL_BIOMART_REFERENCE_CAPTURE_ONLY';archive=a.intake/(version+'-windows')/('biomaRt_'+version+'.zip');assert pin(archive)==reference['archive'];home=out/version;home.mkdir();source_root=a.intake/(version+'-source/source');command=[str(RSCRIPT),'--vanilla',str(script),str(home/'R-lib'),str(home),str(archive.resolve()),str(source_root.resolve()),version,*map(str,BASE_LIBRARIES)];env=environment();temporary=home/'private-temp';temporary.mkdir();env.update(TMPDIR=str(temporary.resolve()),TMP=str(temporary.resolve()),TEMP=str(temporary.resolve()))
            with (home/'installer.stdout').open('xb') as first,(home/'installer.stderr').open('xb') as second:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=600)
            save(home/'process.json',dict(command=command,exit_code=r.returncode));report=json.loads((home/'R-RESULT.json').read_bytes());state['versions'][version]=report;assert r.returncode==0 and report['status']=='PASS_ORIGINAL_BIOMART_REFERENCE_PACKAGE_ONLY',(version,report.get('error'));assert pin(archive)==reference['archive'];print(version,'actual original package and source bodies loaded',flush=True)
        assert all(pin(ROOT/n)==v for n,v in pins.items());state['status']='PASS_PRIVATE_ORIGINAL_BIOMART_PACKAGES_AND_SOURCE_BODIES_ONLY'
    except Exception as error:state['error']=repr(error);print('SKIP3 ORIGINAL REFERENCE ANALYSIS SKIPPED:',repr(error),flush=True)
    finally:
        after=inventory(BASE_LIBRARIES);save(out/'BASES-AFTER.json',after);state['preexisting_libraries_unchanged']=before==after;state['preexisting_file_count']=sum(len(v) for v in before.values())
        if before!=after:state.update(status='FAIL2_PREEXISTING_R_LIBRARY_CHANGED',error='Inspect complete before/after identities; no result admitted')
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Both private original package editions loaded; no gene annotation or Java implementation is validated.')+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
    print(state['status']+'; read '+str(out/'WARNINGS.md'));return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact original package/shared-library checks')
    raise SystemExit(main())
