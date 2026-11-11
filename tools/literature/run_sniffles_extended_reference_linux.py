# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original CRAM/reference/CSI/empty-call behavior and legacy/error probes.

Direct upstream reference experiments only; no Java workflow availability credit.
"""
import argparse,json,os,resource,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_sniffles_reference_linux import ROOT,pin,save,inventory,members
from run_sniffles_reference_linux import original_vcf


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    base=ROOT/'build/literature-review/sniffles-runtime';out=args.out.resolve()
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    runtime=base/'reference-setup-3';fixtures=base/'extended-fixtures-1';original=base/'fixtures-1'
    state=dict(status='FAIL2_SNIFFLES_EXTENDED_ORIGINAL_REFERENCE',analysis_admitted=False,implementation_credit=False,
        Java_calling_adapter_validated=False,whole_goal_complete=False,cases=[],processes=[])
    def limits():
        for kind,value in [(resource.RLIMIT_AS,8589934592),(resource.RLIMIT_CPU,120),(resource.RLIMIT_FSIZE,268435456),(resource.RLIMIT_NOFILE,256),(resource.RLIMIT_CORE,0)]:resource.setrlimit(kind,(value,value))
    def execute(home,command):
        record=dict(command=list(map(str,command)));save(home/'command.json',record);child=None;started=time.monotonic()
        try:
            env=dict(PATH='/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',HOME=str(home),TMPDIR=str(home),
                REF_PATH=str(fixtures/'direct-reference-cache/%s'),REF_CACHE=str(fixtures/'direct-reference-cache/%s'),
                OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
            save(home/'environment.json',env)
            with (home/'original.stdout').open('xb') as stdout,(home/'original.stderr').open('xb') as stderr:
                child=subprocess.Popen(list(map(str,command)),cwd=home,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True,preexec_fn=limits)
                record['owned_group']=child.pid
                while child.poll() is None:
                    assert time.monotonic()-started<180,'Original reference control exceeded wall-time capacity'
                    time.sleep(.025)
                record['exit_code']=child.returncode;assert not any(row['state']!='Z' for row in members(child.pid)),'Original caller left live owned workers'
        finally:
            if child is not None:
                record['owned_before_cleanup']=members(child.pid)
                for sig,seconds in [(signal.SIGTERM,2),(signal.SIGKILL,3)]:
                    if not any(row['state']!='Z' for row in members(child.pid)):break
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    deadline=time.monotonic()+seconds
                    while any(row['state']!='Z' for row in members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                child.wait(timeout=10);record['owned_live_after_cleanup']=[row for row in members(child.pid) if row['state']!='Z']
                assert not record['owned_live_after_cleanup'],'Owned original workers remain live'
            record['elapsed_seconds']=time.monotonic()-started;save(home/'process.json',record);state['processes'].append(record)
        return record
    try:
        setup=json.loads((runtime/'COMPLETED.json').read_bytes());creation=json.loads((fixtures/'COMPLETED.json').read_bytes())
        assert setup['status']=='PASS_PRIVATE_FIVE_ORIGINAL_SNIFFLES_REFERENCE_RUNTIMES_ONLY'
        assert inventory(runtime/'venv')==json.loads((runtime/'RUNTIME-FILES.json').read_bytes())
        assert inventory(runtime/'sources')==json.loads((runtime/'SOURCE-COPY-FILES.json').read_bytes())
        assert creation['status']=='PASS_EXTENDED_SYNTHETIC_SNIFFLES_FORMAT_CONTROL_CREATION_ONLY'
        assert all(pin(fixtures/name)==value for name,value in creation['files'].items())
        original_pins={name:pin(original/name) for name in ['reference.fa','sampleA.bam','sampleA.bam.bai']}
        prepared=out/'prepared';prepared.mkdir();cram=prepared/'sampleA.cram';cram.write_bytes((fixtures/'sampleA.cram').read_bytes())
        python=runtime/'venv/bin/python';cache=prepared/'never-created-bytecode-cache'
        prepared_process=execute(prepared,[python,'-I','-B','-X','pycache_prefix='+str(cache),'-c','import pysam,sys;pysam.index(sys.argv[1])',cram])
        assert prepared_process['exit_code']==0 and Path(str(cram)+'.crai').is_file()
        cases=[(tag,'cram-plain',cram,['--reference',fixtures/'reference.fa'],3) for tag in ['v2.0.2','v2.0.7','v2.3.2','v2.4']]
        cases.extend([(tag,'cram-bgzf',cram,['--reference',fixtures/'reference-bgzip.fa.bgz'],3) for tag in ['v2.0.2','v2.4']])
        cases.extend([
            ('v2.4','csi',fixtures/'long-contig.bam',[],3),
            ('v2.4','empty',fixtures/'no-indels.bam',[],0),
            ('v2.4','bam-reference',original/'sampleA.bam',['--reference',fixtures/'reference.fa'],3),
            ('v1.0.7','known-dup',original/'sampleA.bam',['--Ivcf',fixtures/'known-dup.vcf'],None),
            ('v2.0.2','wrong-contig-error',original/'sampleA.bam',['--tandem-repeats',fixtures/'wrong-contig.bed'],None)])
        for tag,kind,input_path,options,wanted in cases:
            name=tag.replace('.','')+'-'+kind;home=out/name;home.mkdir();target=home/'original.vcf'
            source=list(setup['probes'][tag]['command'])
            if tag!='v1.0.7':source=[source[0],'-X','pycache_prefix='+str(home/'never-created-bytecode-cache'),*source[1:]]
            command=[*source,'--mapped_reads' if tag=='v1.0.7' else '--input',input_path,'--vcf',target,'--threads','1',*options]
            record=execute(home,command);assert record['exit_code']==0,(name,'unexpected original process failure')
            stdout=(home/'original.stdout').read_text(encoding='utf8');stderr=(home/'original.stderr').read_text(encoding='utf8')
            # A fatal source run can leave a zero-byte or incomplete VCF. Retain
            # those original bytes as failed output; never parse them as a valid
            # zero-call scientific result merely because the process exited 0.
            decoded=original_vcf(target) if kind!='wrong-contig-error' and target.exists() else None
            item=dict(id=name,edition=tag[1:],kind=kind,exit_code=record['exit_code'],expected_records=wanted,records=len(decoded['records']) if decoded is not None else None,
                original_vcf=pin(target) if target.exists() else None,source_namespace_patched=False,analysis_admitted=False)
            if kind=='wrong-contig-error':
                assert 'Sniffles2 Error:' in stdout+stderr and 'no matching annotations' in stdout+stderr
                item.update(status='PASS_OBSERVED_ORIGINAL_FATAL_DIAGNOSTIC_WITH_ZERO_EXIT_ONLY',original_source_fatal_exit_zero=True,
                    original_failed_vcf_bytes=target.stat().st_size if target.exists() else None,failed_VCF_not_parsed_as_valid_callset=True,
                    required_adapter_behavior='Reject the entire batch despite zero exit; retain complete diagnostics and earlier provisional cases as unadmitted.')
            else:
                assert decoded is not None and 'Sniffles2 Error:' not in stdout+stderr
                if wanted is not None:assert len(decoded['records'])==wanted,(name,len(decoded['records']),wanted)
                save(home/'complete-original-records.json',decoded);item['complete_decoding']=pin(home/'complete-original-records.json')
                types=[next(field['value'] for field in row['info'] if field['key']=='SVTYPE') for row in decoded['records']]
                item.update(status='PASS_COMPLETE_EXTENDED_ORIGINAL_REFERENCE_ONLY',SVTYPEs=types)
                if kind=='known-dup':
                    item.update(declared_input_SVTYPE='DUP',observed_original_SVTYPEs=types,source_discrepancy=types!=['DUP'],required_action='Retain the original caller type and warn about any observed mismatch; do not relabel an original record to agree with the requested DUP.')
            save(home/'COMPLETED.json',item);state['cases'].append(item)
            print(name+': '+item['status']+' records='+str(item['records'])+' SVTYPEs='+str(item.get('SVTYPEs')),flush=True)
        assert inventory(runtime/'venv')==json.loads((runtime/'RUNTIME-FILES.json').read_bytes())
        assert inventory(runtime/'sources')==json.loads((runtime/'SOURCE-COPY-FILES.json').read_bytes())
        assert all(pin(fixtures/name)==value for name,value in creation['files'].items()) and all(pin(original/name)==value for name,value in original_pins.items())
        state.update(status='PASS_ELEVEN_EXTENDED_ORIGINAL_SNIFFLES_REFERENCE_EXPERIMENTS_ONLY',original_sources_and_inputs_unchanged=True,
            source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},
            scientific_comparison_cases=9,source_behavior_probes=2)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Complete upstream reference experiment capture only.')+
        '\n\nACTION: these are original source/format experiments, not Java calling adapter validation, biological benchmarks or paper reproductions. Preserve source discrepancies and zero-exit fatal diagnostics. No scientific calling result is admitted by this reference harness.\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original extended reference checks')
    raise SystemExit(main())
