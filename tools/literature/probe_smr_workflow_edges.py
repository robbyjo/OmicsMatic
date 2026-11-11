# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Original-backed SMR allele/overlap/legacy-format controls plus real dependency/capacity/atomic-failure protocols."""
import argparse,copy,json,math,shutil,struct,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_smr_cmake import linux
from probe_smr_workflow import BASE,PREFIX,analysis,request,direct_options


def windows_paths(value):
    if isinstance(value,dict):return {k:windows_paths(v) for k,v in value.items()}
    if isinstance(value,list):return [windows_paths(v) for v in value]
    if isinstance(value,str) and value.startswith('/mnt/d/'):return 'D:/'+value[len('/mnt/d/'):]
    return value


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};names=[*build['source_pins'],'tools/literature/probe_smr_workflow.py',Path(__file__).resolve().relative_to(ROOT).as_posix()];pins={n:pin(ROOT/n) for n in names};assert all(pins[n]==v for n,v in build['source_pins'].items());reports=[];state=dict(status='FAIL2_SMR_EDGE_PROBE_INCOMPLETE',artifact=build['artifact'],source_pins=pins,protocols=reports,adapter_admitted=False,implementation_credit=False,whole_goal_complete=False)
    def run(identifier,q,expected=0,windows=False,late=False):
        home=out/identifier;home.mkdir();q=copy.deepcopy(q);q['out']=linux(home/'analysis')
        if windows:q=windows_paths(q)
        save(home/'request.json',q);command=([r'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx2g','-cp',str(jar)] if windows else PREFIX+['/usr/bin/java','-Xmx2g','-cp',linux(jar)])+['org.jlinalg.cli.SmrWorkflowCli','--request',str(home/'request.json') if windows else linux(home/'request.json')];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(home/'stdout').write_bytes(r.stdout);(home/'stderr').write_bytes(r.stderr);save(home/'process.json',dict(command=command,exit_code=r.returncode));row=dict(id=identifier,expected_exit_code=expected,exit_code=r.returncode,passed=r.returncode==expected,comparisons=[]);reports.append(row);print(identifier,'exit',r.returncode,'expected',expected,flush=True)
        if not row['passed']:return
        status=json.loads((home/'analysis/analysis-status.json').read_bytes());assert status['result_admitted']==(expected==0) and (home/'analysis/WARNINGS.md').is_file()
        if expected:
            assert not (home/'analysis/results').exists()
            if late:assert (home/'analysis/worker/cases/early/original.smr').is_file() and (home/'analysis/worker/cases/late/process-receipt.json').is_file()
            return
        runtime=BASE/'runtime-profile-2'/q['version'];program=[linux(runtime/'lib/ld-linux-x86-64.so.2'),'--inhibit-cache','--library-path',linux(runtime/'lib'),'--inhibit-rpath','',linux(runtime/'bin/smr')];inputs=home/'analysis/inputs';prepared=home/'analysis/worker/prepare/original' if q['qtl']['kind']=='qfile' else inputs/'qtl'
        for case in q['analyses']:
            original=home/('direct-'+case['id']);original.mkdir();command=PREFIX+['/usr/bin/env','-i','PATH=/usr/bin:/bin','LC_ALL=C','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1',*program,*direct_options(case,q,inputs,prepared,original/'original')];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=180);(original/'stdout').write_bytes(r.stdout);(original/'stderr').write_bytes(r.stderr);save(original/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0;files={}
            for file in original.glob('original.*'):
                target=home/'analysis/worker/cases'/case['id']/file.name;assert target.is_file() and file.read_bytes()==target.read_bytes(),(identifier,case['id'],file.name);files[file.name]=pin(file)
            assert any(n.endswith(('.smr','.msmr')) for n in files);row['comparisons'].append(dict(case=case['id'],complete_original_outputs=files))
    try:
        inputs=out/'base-inputs';shutil.copytree(BASE/'synthetic-controls-1',inputs);edited=out/'alignment-inputs';shutil.copytree(inputs,edited)
        gwas=[line.split('\t') for line in (edited/'gwas.ma').read_text().splitlines()]
        for i,x in enumerate(gwas[1:],1):
            if i%3==1:x[1],x[2]=x[2],x[1];x[3]=str(1-float(x[3]));x[4]=str(-float(x[4]))
            if i==4:x[3]='0' # Original GWAS swap complements only strictly positive frequency.
            if i==27:x[3]='.99'
            if i==28:x[1:3]=['C','T'] if x[1:3] in [['A','G'],['G','A']] else ['A','G']
        (edited/'gwas.ma').write_text(''.join('\t'.join(x)+'\n' for x in gwas),encoding='ascii')
        qtl=[line.split('\t') for line in (edited/'qtl.qfile').read_text().splitlines()]
        for x in qtl[1:]:
            if x[0]=='rsSynthetic2':x[3],x[4]=x[4],x[3];x[5]=str(1-float(x[5]));x[11]=str(-float(x[11]))
        (edited/'qtl.qfile').write_text(''.join('\t'.join(x)+'\n' for x in qtl),encoding='ascii');bim=[line.split() for line in (edited/'reference.bim').read_text().splitlines()];bed=bytearray((edited/'reference.bed').read_bytes());samples=512;rowbytes=(samples+3)//4
        for i,x in enumerate(bim):
            if i%4==0:
                x[4],x[5]=x[5],x[4]
                for j in range(samples):
                    offset=3+i*rowbytes+j//4;shift=2*(j%4);code=(bed[offset]>>shift)&3;new=3 if code==0 else 0 if code==3 else code;bed[offset]=(bed[offset]&~(3<<shift))|(new<<shift)
            offset=3+i*rowbytes;bed[offset]=(bed[offset]&~3)|1
        bim[25][3]=str(int(bim[25][3])+99);(edited/'reference.bim').write_text(''.join('\t'.join(x)+'\n' for x in bim),encoding='ascii');(edited/'reference.bed').write_bytes(bed)
        overlap=out/'overlap-inputs';shutil.copytree(inputs,overlap);gwas=[line.split('\t') for line in (overlap/'gwas.ma').read_text().splitlines()];qtl=[line.split('\t') for line in (overlap/'qtl.qfile').read_text().splitlines()]
        for i,x in enumerate(gwas[1:],1):
            if 20<=i<=28:x[4]=str(.012*(.12*(i-24)+.03*((i%3)-1)))
        for x in qtl[1:]:
            i=int(x[0].removeprefix('rsSynthetic'))
            if 20<=i<=28:x[11]=str(.02*(.14*(i-24)+.05*((i%2)*2-1)))
        inconsistent=out/'legacy-SE-rederivation-inputs';shutil.copytree(overlap,inconsistent);(inconsistent/'qtl.qfile').write_text(''.join('\t'.join(x)+'\n' for x in qtl),encoding='ascii')
        for x in qtl[1:]:x[13]=format(math.erfc(abs(float(x[11])/float(x[12]))/math.sqrt(2)),'.17g')
        for name,rows in [('gwas.ma',gwas),('qtl.qfile',qtl)]: (overlap/name).write_text(''.join('\t'.join(x)+'\n' for x in rows),encoding='ascii')
        ordered=out/'reversed-order-inputs';shutil.copytree(inputs,ordered);bim=(ordered/'reference.bim').read_text().splitlines();raw=(ordered/'reference.bed').read_bytes();rowbytes=128;(ordered/'reference.bim').write_text('\n'.join(reversed(bim))+'\n',encoding='ascii');(ordered/'reference.bed').write_bytes(raw[:3]+b''.join(raw[3+i*rowbytes:3+(i+1)*rowbytes] for i in reversed(range(32))));gwas=(ordered/'gwas.ma').read_text().splitlines();(ordered/'gwas.ma').write_text('\n'.join([gwas[0],*reversed(gwas[1:])])+'\n',encoding='ascii');qtl=[line.split() for line in (ordered/'qtl.qfile').read_text().splitlines()]
        for x in qtl[1:]:
            if x[6]=='probe-strong' and x[0] in ['rsSynthetic1','rsSynthetic2']:x[11:14]=['.5','.02','NA']
        (ordered/'qtl.qfile').write_text(''.join('\t'.join(x)+'\n' for x in qtl),encoding='ascii');save(ordered/'DERIVED.json',dict(kind='explicit-synthetic-order-and-equal-Z-control',base=pin(inputs/'GENERATED.json'),BIM_and_matching_BED_rows_reversed=True,GWAS_rows_reversed=True,strong_probe_SNP1_and2_equal_beta_SE_with_missing_input_P=True,raw_baseline_preserved=True))
        maf=out/'MAF-boundary-inputs';shutil.copytree(inputs,maf);(maf/'SOURCE_BASE_GENERATED.json').write_bytes((maf/'GENERATED.json').read_bytes());(maf/'GENERATED.json').write_text(json.dumps(dict(kind='derived-synthetic500-MAF-boundary',reference_samples=500,source_baseline=pin(inputs/'GENERATED.json')),indent=2)+'\n',encoding='utf8');fam=(maf/'reference.fam').read_text().splitlines();(maf/'reference.fam').write_text('\n'.join(fam[:500])+'\n',encoding='ascii');bim=[x.split() for x in (maf/'reference.bim').read_text().splitlines()];bim[0][4],bim[0][5]=bim[0][5],bim[0][4];(maf/'reference.bim').write_text(''.join('\t'.join(x)+'\n' for x in bim),encoding='ascii');raw=(inputs/'reference.bed').read_bytes();packed=bytearray(raw[:3])
        for i in range(32):
            row=bytearray(125)
            for j in range(500):
                code=(2 if j<10 else 0) if i==0 else (raw[3+i*128+j//4]>>(2*(j%4)))&3;row[j//4]|=code<<(2*(j%4))
            packed.extend(row)
        (maf/'reference.bed').write_bytes(packed);save(maf/'DERIVED.json',dict(kind='explicit-synthetic-frequency-operation-order-boundary',base=pin(inputs/'GENERATED.json'),reference_samples=500,excluded_synthetic_samples=fam[500:],top_SNP_BIM_alleles_swapped=True,top_BIM_A1_frequency=.99,aligned_QTL_allele_frequency=.01,maf_cutoff=.01,native_complement_must_count_other_dosages_directly=True,raw_baseline_preserved=True))
        xunknown=out/'undefined-X-inputs';shutil.copytree(inputs,xunknown);bim=[x.split() for x in (xunknown/'reference.bim').read_text().splitlines()]
        for x in bim:x[0]='23'
        (xunknown/'reference.bim').write_text(''.join('\t'.join(x)+'\n' for x in bim),encoding='ascii')
        for version in ['1.3.1','1.4.3']:
            for storage in ['sparse','dense']:run(version+'-reversed-order-'+storage,request(version,ordered,out/'unused',[analysis(version,'ordered-new'),analysis(version,'ordered-off',method='off'),analysis(version,'ordered-multi',kind='multi')],storage=storage))
            q=request(version,maf,out/'unused',[analysis(version,'boundary-new'),analysis(version,'boundary-off',method='off'),analysis(version,'boundary-multi',kind='multi')]);q['reference']['population_label']='synthetic500-exact-MAF-boundary'
            for case in q['analyses']:case['frequency_check']=dict(kind='disabled')
            run(version+'-MAF-boundary',q)
            q=request(version,edited,out/'unused',[analysis(version,'alignment-new'),analysis(version,'alignment-off',method='off'),analysis(version,'alignment-multi',kind='multi')]);q['reference']['coordinate_policy']='source-ID-and-alleles-with-ledger';run(version+'-alignment-missing-frequency',q)
            cases=[analysis(version,'overlap-new'),analysis(version,'overlap-old',method='original'),analysis(version,'overlap-off',method='off'),analysis(version,'overlap-multi',kind='multi')]
            for case in cases:case['sample_overlap']=dict(kind='source-flag-without-estimation') if case['kind']=='multi' else dict(kind='estimate-original',null_p=.01,minimum_null_snps=2)
            run(version+'-overlap',request(version,overlap,out/'unused',cases))
            run(version+'-SE-rederivation',request(version,inconsistent,out/'unused',[analysis(version,'declared-source-preparation')]))
            case=analysis(version,'extended-window',kind='multi');case['window_kb']=1;run(version+'-extended-window',request(version,inputs,out/'unused',[case]))
            for storage in ['sparse','dense']:
                old=out/(version+'-legacy-input-'+storage);old.mkdir();original=BASE/('original-probe-'+version+'-1')/('make-'+storage)/'original'
                for suffix in ['.epi','.esi']:shutil.copyfile(Path(str(original)+suffix),old/('qtl'+suffix))
                raw=Path(str(original)+'.besd').read_bytes();indicator=struct.unpack_from('<i',raw)[0];assert indicator==(3 if storage=='sparse' else 5);(old/'qtl.besd').write_bytes(struct.pack('<i',0x40400000 if storage=='sparse' else 0)+raw[64:]);q=request(version,inputs,out/'unused',[analysis(version,'legacy')]);q['qtl']=dict(kind='besd',prefix=linux(old/'qtl'),precision='source-binary32',missing_policy='source-sentinel-ledger');run(version+'-legacy-'+storage,q)
            q=request(version,inputs,out/'unused',[analysis(version,'prepared')]);prepared=BASE/('workflow-setup-'+version+'-1');q.update(source=linux(prepared/'source'),runtime=linux(prepared/'runtime'));run(version+'-fresh-setup',q)
        q=request('1.3.1',inputs,out/'unused',[analysis('1.3.1','early',method='off'),analysis('1.3.1','late')]);q['analyses'][1]['frequency_check']=dict(kind='source-check',maximum_difference=0,maximum_excluded_fraction=0);run('late-original-failure',q,2,late=True)
        for identifier,key,value,expected in [('missing-source','source',linux(out/'unavailable-source-tree'),3),('missing-runtime','runtime',linux(out/'unavailable-runtime-tree'),3),('timeout','timeout_millis',1,2),('output-growth','maximum_output_bytes',1,2),('variant-capacity','maximum_variants',1,2),('input-capacity','maximum_input_bytes',1,2),('native-capacity','maximum_native_operations',1,2)]:
            q=request('1.3.1',inputs,out/'unused',[analysis('1.3.1','test')]);q[key]=value;run(identifier,q,expected)
        run('windows-explicit-skip',request('1.3.1',inputs,out/'unused',[analysis('1.3.1','test')]),3,windows=True)
        run('undefined-X-sex-weights',request('1.3.1',xunknown,out/'unused',[analysis('1.3.1','test')]),2)
        assert all(r['passed'] for r in reports),'Inspect failed edge protocols';state['status']='passed-original-SMR-allele-overlap-legacy-and-atomic-failure-controls';state['complete_original_case_comparisons']=sum(len(r['comparisons']) for r in reports)
    except Exception as error:state['detail']=repr(error)
    finally:
        assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',state)
    print(state['status'],state.get('detail',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/atomic/source checks')
    raise SystemExit(main())
