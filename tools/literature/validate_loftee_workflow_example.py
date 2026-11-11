# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the documented complete synthetic request with a restored runtime and compare both cases to original software."""
import argparse,json,subprocess
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save
from setup_loftee_reference import linux
from probe_loftee_workflow import BASE,direct_options
from compare_loftee_original import compare_files

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();runtime=a.runtime.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};source=Path(__file__).resolve();names=['tools/literature/validate_loftee_workflow_example.py','tools/literature/prepare_loftee_workflow_example.py','tools/literature/pack_loftee_workflow_inputs.py','tools/literature/probe_loftee_workflow.py','tools/literature/compare_loftee_original.py','docs/literature/LOFTEE_WORKFLOWS.md','src/test/resources/org/jlinalg/genetics/loftee-original/workflow-inputs-v1.zip'];pins={n:pin(ROOT/n) for n in names};pins.update(build['source_pins']);(out/'validator-at-attempt.py').write_bytes(source.read_bytes());state=dict(status='FAIL2_DOCUMENTED_LOFTEE_EXAMPLE_INCOMPLETE',artifact=build['artifact'],source_pins=pins,runtime_preparation_receipt=pin(runtime/'COMPLETED.json'),comparisons=[],implementation_credit=False,whole_goal_complete=False)
    def execute(name,command,home=out):
        save(home/(name+'.command.json'),dict(command=command))
        with (home/(name+'.stdout')).open('xb') as first,(home/(name+'.stderr')).open('xb') as second:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=1800)
        save(home/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
    try:
        prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec'];example=out/'example';execute('prepare',prefix+['/usr/bin/python3',linux(ROOT/'tools/literature/prepare_loftee_workflow_example.py'),'--runtime-home',linux(runtime),'--out',linux(example)]);q=json.loads((example/'request.json').read_bytes());execute('typed',prefix+['/usr/bin/java','-Xmx2g','-cp',linux(jar),'org.jlinalg.cli.LofteeWorkflowCli','--request',linux(example/'request.json')]);analysis=example/'analysis';assert json.loads((analysis/'analysis-status.json').read_bytes())['result_admitted'];input_audit=json.loads((analysis/'native-input-audit.json').read_bytes());assert input_audit['samples']==2 and input_audit['variants']==15 and input_audit['BGZF_GZI_validated_against_complete_block_boundaries'];environment=BASE/'reference-setup-1/environment';share=environment/'share/ensembl-vep-105.0-1';bioperl=BASE/'bioperl-setup-1/api';plugin=BASE/'source-intake-1/loftee-grch38'
        for c in q['analyses']:
            home=out/('direct-'+c['id']);home.mkdir();arguments=direct_options(c,q,PurePosixPath(linux(analysis/'inputs')),PurePosixPath(linux(plugin)),PurePosixPath(linux(home)));execute('original',prefix+['/usr/bin/env','-i','PATH='+linux(environment/'bin')+':/usr/bin:/bin','HOME='+linux(home),'LC_ALL=C','TZ=UTC','PERL5LIB='+':'.join(linux(f) for f in [plugin,share,bioperl]),'LD_LIBRARY_PATH='+linux(environment/'lib'),linux(environment/'bin/perl'),linux(share/'vep'),*arguments],home);native=analysis/'worker/cases'/c['id'];comparison=compare_files(native/'original.vcf',home/'original.vcf');audit=json.loads((native/'native-audit.json').read_bytes());assert (audit['variants'],audit['annotations'],audit['HC'],audit['LC'])==(15,80,8 if c['id']=='default-GERP' else 10,2 if c['id']=='default-GERP' else 0);state['comparisons'].append(dict(case=c['id'],complete_source_record_comparison=comparison,original_vcf=pin(home/'original.vcf'),typed_vcf=pin(native/'original.vcf'),native_audit=audit));print(c['id'],'documented example and complete original records passed',flush=True)
        assert all(pin(ROOT/n)==v for n,v in pins.items());state['status']='passed-documented-compressed-genotyped-LOFTEE-example-with-restored-runtime-and-original-comparisons'
    except Exception as error:state['error']=repr(error)
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact documented example checks')
    raise SystemExit(main())
