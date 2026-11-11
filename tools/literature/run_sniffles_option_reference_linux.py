# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original edition-specific thresholds, genotypes, literal IDs and phased CRAM."""
import argparse,json,os,resource,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_sniffles_reference_linux import ROOT,pin,save,inventory,members
from run_sniffles_reference_linux import original_vcf


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    base=ROOT/'build/literature-review/sniffles-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    runtime=base/'reference-setup-3';fixtures=base/'fixtures-1';extended=base/'extended-fixtures-1';cram=base/'extended-original-2/prepared/sampleA.cram'
    state=dict(status='FAIL2_ORIGINAL_SNIFFLES_OPTION_EXPERIMENTS',analysis_admitted=False,implementation_credit=False,Java_calling_adapter_validated=False,whole_goal_complete=False,cases=[])
    inputs=[fixtures/'sampleA.bam',fixtures/'sampleA.bam.bai',cram,Path(str(cram)+'.crai'),extended/'reference.fa',Path(str(extended/'reference.fa')+'.fai')]
    before={p.relative_to(ROOT).as_posix():pin(p) for p in inputs}
    try:
        setup=json.loads((runtime/'COMPLETED.json').read_bytes());assert setup['status']=='PASS_PRIVATE_FIVE_ORIGINAL_SNIFFLES_REFERENCE_RUNTIMES_ONLY'
        assert inventory(runtime/'venv')==json.loads((runtime/'RUNTIME-FILES.json').read_bytes()) and inventory(runtime/'sources')==json.loads((runtime/'SOURCE-COPY-FILES.json').read_bytes())
        experiment=json.loads((base/'extended-original-2/COMPLETED.json').read_bytes());assert experiment['status']=='PASS_ELEVEN_EXTENDED_ORIGINAL_SNIFFLES_REFERENCE_EXPERIMENTS_ONLY'
        cases=[
            ('v107-legacy-segment-high','v1.0.7',fixtures/'sampleA.bam',['--min_seq_size','1000000'],2,None),
            ('v24-alignment-high','v2.4',fixtures/'sampleA.bam',['--min-alignment-length','1000000'],0,None),
            ('v107-numeric-genotype','v1.0.7',fixtures/'sampleA.bam',['--min_length','80','--min_support','10','--minmapping_qual','22','--min_seq_size','500','--genotype'],1,None),
            ('v202-numeric-auto-literal','v2.0.2',fixtures/'sampleA.bam',['--minsvlen','80','--minsupport','auto','--mapq','22','--min-alignment-length','500','--sample-id=--literal sample202'],2,'--literal sample202'),
            ('v24-numeric-cram-literal-phase','v2.4',cram,['--reference',str(extended/'reference.fa'),'--minsvlen','80','--minsupport','10','--mapq','22','--min-alignment-length','500','--sample-id=--literal sample24','--phase'],2,'--literal sample24')]
        def limits():
            for kind,value in [(resource.RLIMIT_AS,8589934592),(resource.RLIMIT_CPU,120),(resource.RLIMIT_FSIZE,268435456),(resource.RLIMIT_NOFILE,256),(resource.RLIMIT_CORE,0)]:resource.setrlimit(kind,(value,value))
        for name,tag,input_path,options,wanted,sample in cases:
            home=out/name;home.mkdir();target=home/'original.vcf';source=list(setup['probes'][tag]['command'])
            if tag!='v1.0.7':source=[source[0],'-X','pycache_prefix='+str(home/'never-created-bytecode-cache'),*source[1:]]
            command=[*source,'--mapped_reads' if tag=='v1.0.7' else '--input',str(input_path),'--vcf',str(target),'--threads','1',*options]
            env=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',HOME=str(home),TMPDIR=str(home),REF_PATH=str(extended/'direct-reference-cache/%s'),REF_CACHE=str(extended/'direct-reference-cache/%s'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
            save(home/'command.json',command);save(home/'environment.json',env);record=dict(id=name,edition=tag[1:],command=command,analysis_admitted=False,source_namespace_patched=False)
            child=None;started=time.monotonic()
            try:
                with (home/'original.stdout').open('xb') as stdout,(home/'original.stderr').open('xb') as stderr:
                    child=subprocess.Popen(command,cwd=home,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True,preexec_fn=limits);record['owned_group']=child.pid
                    while child.poll() is None:
                        assert time.monotonic()-started<180,'Original option experiment exceeded wall-time capacity'
                        time.sleep(.025)
                    record['exit_code']=child.returncode;assert child.returncode==0 and not any(row['state']!='Z' for row in members(child.pid))
                diagnostics=(home/'original.stdout').read_text(encoding='utf8')+(home/'original.stderr').read_text(encoding='utf8')
                assert 'Sniffles2 Error:' not in diagnostics and 'Traceback (most recent call last)' not in diagnostics
                decoded=original_vcf(target);assert len(decoded['records'])==wanted,(name,len(decoded['records']),wanted)
                if sample is not None:assert decoded['samples']==[sample],'Original literal sample ID was changed'
                if name.endswith('-phase'):
                    assert all('|' in row['samples'][0][row['format'].index('GT')] and row['samples'][0][row['format'].index('PS')]=='1001' for row in decoded['records'])
                save(home/'complete-original-records.json',decoded)
                record.update(status='PASS_COMPLETE_ORIGINAL_OPTION_EXPERIMENT_ONLY',records=len(decoded['records']),original_vcf=pin(target),complete_decoding=pin(home/'complete-original-records.json'),literal_sample_id=sample)
            finally:
                if child is not None:
                    record['owned_before_cleanup']=members(child.pid)
                    for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                        if not any(row['state']!='Z' for row in members(child.pid)):break
                        try:os.killpg(child.pid,sig)
                        except ProcessLookupError:pass
                        deadline=time.monotonic()+seconds
                        while any(row['state']!='Z' for row in members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                    child.wait(timeout=10);record['owned_live_after_cleanup']=[row for row in members(child.pid) if row['state']!='Z'];assert not record['owned_live_after_cleanup']
                record['elapsed_seconds']=time.monotonic()-started;save(home/'COMPLETED.json',record)
            state['cases'].append(record);print(name+': retained '+str(wanted)+' complete original records; literal sample='+str(sample),flush=True)
        assert inventory(runtime/'venv')==json.loads((runtime/'RUNTIME-FILES.json').read_bytes()) and inventory(runtime/'sources')==json.loads((runtime/'SOURCE-COPY-FILES.json').read_bytes())
        assert before=={name:pin(ROOT/name) for name in before}
        state.update(status='PASS_FIVE_ORIGINAL_SNIFFLES_OPTION_EXPERIMENTS_ONLY',complete_original_records=7,original_sources_and_inputs_unchanged=True,
            protected_input_pins=before,source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},
            edition_specific_semantics='Legacy --min_seq_size is an event-segment threshold, not modern query_alignment_length filtering. The same high value retains2 legacy calls while modern alignment filtering returns0. Expose separate typed settings; do not translate these as an equivalent filter.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error',state.get('edition_specific_semantics','Original option experiments unfinished.'))+
        '\n\nACTION: preserve exact source-edition semantics, literal sample labels and complete original fields. These direct original experiments do not validate a Java calling adapter or a paper. No original scientific source or raw input was patched.\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original option/source/cleanup checks')
    raise SystemExit(main())
