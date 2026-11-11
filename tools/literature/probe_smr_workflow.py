# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise typed original SMR/HEIDI cases and compare every original table/auxiliary output to a separate original invocation."""
import argparse,copy,json,shutil,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_smr_cmake import linux

PREFIX=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--']
BASE=ROOT/'build/literature-review/smr-runtime'


def analysis(version,identifier,kind='single',method='top20'):
    h=dict(method=method)
    if method!='off':h.update(qtl_p=.00157,ld_upper=.9,minimum_snps=3,skip_smr_p=1)
    if method=='top20':
        h.update(ld_lower=.05,maximum_snps=20)
        if version=='1.4.3':h['maximum_ld_candidates']=500
    a=dict(id=identifier,kind=kind,qtl_p=5e-8,window_kb=2000,minimum_maf=0 if kind=='single' and method=='off' else .01,frequency_check=dict(kind='source-check',maximum_difference=.2,maximum_excluded_fraction=.5),sample_overlap=dict(kind='none'),heidi=h)
    if kind=='multi':a['multi']=dict(window_kb=500,ld_r2=.1)
    return a


def request(version,inputs,out,cases,storage='sparse'):
    return dict(schema_version=1,mode='ANALYZE',profile='original-SMR-Linux-x86_64-1.3.1-1.4.3',version=version,gwas=linux(inputs/'gwas.ma'),qtl=dict(kind='qfile',file=linux(inputs/'qtl.qfile'),storage=storage,precision='source-binary32',preparation_policy='source-version-with-ledger'),reference=dict(kind='plink-bed',prefix=linux(inputs/'reference'),assembly_label='synthetic-coordinate-system',population_label='synthetic512-not-a-human-reference',coordinate_policy='require-equal',missing_genotypes='source-mean-impute-LD'),probe_selection=dict(kind='all'),summary_assembly_label='synthetic-coordinate-system',exposure_units='synthetic-exposure-effect',outcome_units='synthetic-outcome-effect',allele_policy='source-exact-or-swap',analyses=cases,source=linux(BASE/('source-2023-initial-1' if version=='1.3.1' else 'source-intake-1')/'source'),runtime=linux(BASE/'runtime-profile-2'/version),out=linux(out),threads=1,timeout_millis=60000)


def direct_options(a,q,inputs,prepared,out):
    args=['--gwas-summary',linux(inputs/'gwas.ma'),'--beqtl-summary',linux(prepared),'--peqtl-smr',str(a['qtl_p']),'--cis-wind',str(a['window_kb'])];h=a['heidi'];multi=a['kind']=='multi'
    if q['reference']['kind']=='plink-bed':args+=['--bfile',linux(inputs/'reference')]
    if multi or h['method']!='off':args+=['--maf',str(a['minimum_maf'])]
    if q['probe_selection']['kind']=='supplied':args+=['--extract-probe',q['probe_selection']['file']]
    if h['method']=='off':args+=['--heidi-off']
    else:
        args+=['--heidi-mtd','0' if h['method']=='original' else '1','--peqtl-heidi',str(h['qtl_p']),'--ld-upper-limit',str(h['ld_upper']),'--heidi-min-m',str(h['minimum_snps']),'--psmr' if q['version']=='1.3.1' else '--heidi-skip-thresh',str(h['skip_smr_p'])]
        if h['method']=='top20':
            args+=['--ld-lower-limit',str(h['ld_lower']),'--heidi-max-m',str(h['maximum_snps'])]
            if q['version']=='1.4.3':args+=['--max_num_ld',str(h['maximum_ld_candidates'])]
    f=a['frequency_check'];args+=['--disable-freq-ck'] if f['kind']=='disabled' else ['--diff-freq',str(f['maximum_difference']),'--diff-freq-prop',str(f['maximum_excluded_fraction'])]
    o=a['sample_overlap']
    if o['kind']=='estimate-original':args+=['--sample-overlap','--pmecs',str(o['null_p']),'--mmecs',str(o['minimum_null_snps'])]
    elif o['kind']=='source-flag-without-estimation':args+=['--sample-overlap']
    if multi:args+=['--smr-multi','--set-wind',str(a['multi']['window_kb']),'--ld-multi-snp',str(a['multi']['ld_r2'])]
    return args+['--thread-num','1','--out',linux(out)]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};source_pins={n:pin(ROOT/n) for n in [*build['source_pins'],Path(__file__).resolve().relative_to(ROOT).as_posix()]};assert all(source_pins[n]==v for n,v in build['source_pins'].items());reports=[]
    state=dict(status='FAIL2_SMR_WORKFLOW_PROBE_INCOMPLETE',artifact=build['artifact'],source_pins=source_pins,protocols=reports,adapter_admitted=False,implementation_credit=False,whole_goal_complete=False)
    def run(identifier,q,expect=0,compare=True,windows=False):
        home=out/identifier;home.mkdir();q['out']=str(home/'analysis') if windows else linux(home/'analysis');save(home/'request.json',q);command=([r'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx2g','-cp',str(jar)] if windows else PREFIX+['/usr/bin/java','-Xmx2g','-cp',linux(jar)])+['org.jlinalg.cli.SmrWorkflowCli','--request',str(home/'request.json') if windows else linux(home/'request.json')]
        r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(home/'stdout').write_bytes(r.stdout);(home/'stderr').write_bytes(r.stderr);save(home/'process.json',dict(command=command,exit_code=r.returncode));row=dict(id=identifier,expected_exit_code=expect,exit_code=r.returncode,passed=r.returncode==expect,comparisons=[]);reports.append(row);print(identifier,'exit',r.returncode,'expected',expect,flush=True)
        if r.returncode!=expect:return
        status=json.loads((home/'analysis/analysis-status.json').read_bytes());assert status['result_admitted']==(expect==0) and (home/'analysis/WARNINGS.md').is_file()
        if expect!=0:assert not (home/'analysis/results').exists();return
        assert status['status']=='PASS_ORIGINAL_SMR_HEIDI_WORKFLOW'
        if not compare:return
        runtime=BASE/'runtime-profile-2'/q['version'];program=[linux(runtime/'lib/ld-linux-x86-64.so.2'),'--inhibit-cache','--library-path',linux(runtime/'lib'),'--inhibit-rpath','',linux(runtime/'bin/smr')];inputs=home/'analysis/inputs';prepared=home/'analysis/worker/prepare/original' if q['qtl']['kind']=='qfile' else inputs/'qtl'
        for case in q['analyses']:
            original=home/('direct-'+case['id']);original.mkdir();command=PREFIX+['/usr/bin/env','-u','LD_PRELOAD','-u','LD_AUDIT','-u','LD_LIBRARY_PATH','-u','GLIBC_TUNABLES','LC_ALL=C','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1',*program,*direct_options(case,q,inputs,prepared,original/'original')];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=180);(original/'stdout').write_bytes(r.stdout);(original/'stderr').write_bytes(r.stderr);save(original/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0
            comparison={}
            for file in original.glob('original.*'):
                target=home/'analysis/worker/cases'/case['id']/file.name;assert target.is_file() and file.read_bytes()==target.read_bytes(),(identifier,case['id'],file.name);comparison[file.name]=pin(file)
            assert any(n.endswith(('.smr','.msmr')) for n in comparison);row['comparisons'].append(dict(case=case['id'],complete_original_outputs=comparison))
    try:
        inputs=out/'base-inputs';shutil.copytree(BASE/'synthetic-controls-1',inputs)
        for version in ['1.3.1','1.4.3']:
            cases=[analysis(version,'single-new'),analysis(version,'single-old',method='original'),analysis(version,'single-off',method='off'),analysis(version,'multi',kind='multi'),analysis(version,'skip-heidi')];cases[-1]['heidi']['skip_smr_p']=0
            for kind in ['single','multi']:
                empty=analysis(version,'empty-'+kind,kind=kind);empty['qtl_p']=0;cases.append(empty)
            run(version+'-sparse',request(version,inputs,out/'unused',cases))
            run(version+'-dense',request(version,inputs,out/'unused',[analysis(version,'single-dense'),analysis(version,'multi-dense',kind='multi')],storage='dense'))
            q=request(version,inputs,out/'unused',[analysis(version,'summary-only',method='off')]);q['reference']=dict(kind='none');q['analyses'][0]['frequency_check']=dict(kind='disabled');run(version+'-no-reference',q,expect=2 if version=='1.3.1' else 0)
            probes=out/(version+'-probes.list');probes.write_text('probe-low-count\nprobe-strong\n',encoding='ascii');q=request(version,inputs,out/'unused',[analysis(version,'selected')]);q['probe_selection']=dict(kind='supplied',file=linux(probes));run(version+'-selected-probes',q)
        assert all(row['passed'] for row in reports),'Inspect retained failed typed runtime attempts'
        state['status']='passed-original-SMR-HEIDI-core-runtime-comparisons';state['complete_original_case_comparisons']=sum(len(r['comparisons']) for r in reports)
    except Exception as error:state['detail']=repr(error)
    finally:
        assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in source_pins.items());save(out/'COMPLETED.json',state)
    print(state['status'],state.get('detail',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-output/source checks')
    raise SystemExit(main())
