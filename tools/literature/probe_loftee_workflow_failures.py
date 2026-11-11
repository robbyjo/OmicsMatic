# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real CLI no-result contracts for unavailable runtimes, malformed inputs and owned process limits."""
import argparse,json,shutil,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_loftee_reference import linux
from probe_loftee_workflow import BASE,request
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--phase',choices=['fast','supervision'],default='fast');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};source=Path(__file__).resolve();(out/'probe-at-attempt.py').write_bytes(source.read_bytes());pins={source.relative_to(ROOT).as_posix():pin(source),**build['source_pins']};state=dict(status='FAIL2_NO_RESULT_PROTOCOLS_INCOMPLETE',artifact=build['artifact'],source_pins=pins,protocols=[],implementation_credit=False,whole_goal_complete=False);inputs=BASE/'workflow-expanded-inputs-1';runtime=a.runtime.resolve()
    names=['windows-original-runtime-unavailable','unvalidated-cache-profile','missing-runtime','invalid-field','original-REF-mismatch','wrong-FAI','wrong-GZI','input-byte-capacity','resource-byte-capacity','decoded-capacity','variant-capacity','wrong-compression','unsafe-contig','prior-CSQ','duplicate-INFO','unsafe-output-parent'] if a.phase=='fast' else ['owned-process-timeout','owned-output-growth']
    try:
        for name in names:
            home=out/name;home.mkdir();q=request('grch38-1.0.4',inputs,runtime,home/'analysis');expected=2;has_status=True
            if name=='windows-original-runtime-unavailable':
                def windows(v):
                    if isinstance(v,dict):return {k:windows(x) for k,x in v.items()}
                    if isinstance(v,list):return [windows(x) for x in v]
                    if isinstance(v,str) and v.startswith('/mnt/d/projects/JLinAlg/'):return str(ROOT/v.removeprefix('/mnt/d/projects/JLinAlg/'))
                    return v
                q=windows(q);expected=3
            elif name=='unvalidated-cache-profile':
                q['transcripts']=dict(kind='vep-cache',directory=linux(home/'explicitly-unread-synthetic-cache'),assembly_label='GRCh38',release_label='synthetic-control-never-deserialize',cache_version=105,species='homo_sapiens',cache_trust='trusted-original-ensembl-cache');q['fasta']['assembly_label']='GRCh38';q['gerp']['assembly_label']='GRCh38';expected=3
            elif name=='missing-runtime':q['runtime']['environment']=linux(home/'explicitly-missing-original-environment');expected=3
            elif name=='invalid-field':q['unknown_option']=True;has_status=False
            elif name in ['original-REF-mismatch','wrong-FAI','wrong-GZI','unsafe-contig','prior-CSQ','duplicate-INFO']:
                supplied=home/'supplied';shutil.copytree(inputs,supplied);q=request('grch38-1.0.4',supplied,runtime,home/'analysis')
                if name=='wrong-FAI':file=supplied/'reference.fa.fai';file.write_text(file.read_text(encoding='ascii').replace('4000','3999'),encoding='ascii')
                elif name=='wrong-GZI':
                    q['fasta'].update(file=linux(supplied/'reference.fa.gz'),index=linux(supplied/'reference.fa.gz.fai'),gzi=linux(supplied/'reference.fa.gz.gzi'),compression='bgzip');fake=bytearray(24);fake[0]=fake[8]=fake[16]=1;(supplied/'reference.fa.gz.gzi').write_bytes(fake)
                else:
                    file=supplied/'input.vcf';text=file.read_text(encoding='ascii')
                    if name=='original-REF-mismatch':text=text.replace('\tframeshift-insertion\tC\tCA','\tframeshift-insertion\tG\tGA')
                    elif name=='unsafe-contig':text=text.replace('SYN1\t104','SYN1;echo\t104',1)
                    elif name=='prior-CSQ':text=text.replace('#CHROM','##INFO=<ID=CSQ,Number=.,Type=String,Description="Retained stale annotation negative control">\n#CHROM')
                    else:text=text.replace('\tPASS\t.','\tPASS\tX=1;X=2',1)
                    file.write_text(text,encoding='ascii',newline='\n')
            elif name=='input-byte-capacity':q['maximum_input_bytes']=1
            elif name=='resource-byte-capacity':q['maximum_resource_bytes']=1
            elif name=='decoded-capacity':q['maximum_decoded_characters']=1
            elif name=='variant-capacity':q['maximum_variants']=1
            elif name=='wrong-compression':q['input_compression']='gzip'
            elif name=='unsafe-output-parent':q['out']=linux(home/'unsafe parent'/'analysis')
            elif name=='owned-process-timeout':q['timeout_millis']=1
            elif name=='owned-output-growth':q['maximum_output_bytes']=1
            else:raise AssertionError(name)
            save(home/'request.json',q);prefix=[str(JAVA21/'java.exe'),'-cp',str(jar)] if name.startswith('windows-') else ['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-cp',linux(jar)];command=prefix+['org.jlinalg.cli.LofteeWorkflowCli','--request',str(home/'request.json') if name.startswith('windows-') else linux(home/'request.json')]
            with (home/'stdout.log').open('xb') as stdout,(home/'stderr.log').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=1800)
            save(home/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==expected,(name,r.returncode,expected);assert 'REQUIRED' in (home/'stderr.log').read_text(encoding='utf8').upper() or 'REQUIRE EXIT0' in (home/'stderr.log').read_text(encoding='utf8').upper()
            analysis=home/'unsafe parent/analysis' if name=='unsafe-output-parent' else home/'analysis';assert not (analysis/'results').exists()
            if has_status:
                status=json.loads((analysis/'analysis-status.json').read_bytes());
                if name=='unvalidated-cache-profile':assert 'has not been validated' in status['cause'] and not (analysis/'inputs').exists()
                assert not status['result_admitted'] and (analysis/'WARNINGS.md').is_file();receipts=list((analysis/'worker').rglob('process-receipt.json')) if (analysis/'worker').exists() else []
                if a.phase=='supervision':assert receipts,'Original process was never exercised'
                for receipt in receipts:assert all(not x['alive_after_cleanup'] for x in json.loads(receipt.read_bytes())['owned_process_cleanup'])
            else:assert not analysis.exists()
            state['protocols'].append(dict(name=name,expected_exit=expected,observed_exit=r.returncode,result_admitted=False,warning_actions_present=True));print(name,'expected no-result exit',expected,'passed',flush=True)
        assert all(pin(ROOT/n)==v for n,v in pins.items());state['status']='passed-real-CLI-no-result-and-prominent-warning-protocols'
    except Exception as error:state['error']=repr(error)
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable no-result/cleanup protocols')
    raise SystemExit(main())
