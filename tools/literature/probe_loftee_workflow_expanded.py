# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare complete typed LOFTEE compressed/resource/fork cases to independent unchanged upstream execution."""
import argparse,json,shutil,subprocess
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save
from setup_loftee_reference import linux
from probe_loftee_workflow import BASE,DEFAULTS,request,direct_options
from compare_loftee_original import compare_files

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--scenario',required=True,choices=['compressed-local-resources','forked-cited-source','late-failure']);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);source=Path(__file__).resolve();(out/'probe-at-attempt.py').write_bytes(source.read_bytes());build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};pins={n:pin(ROOT/n) for n in ['tools/literature/probe_loftee_workflow.py','tools/literature/probe_loftee_workflow_expanded.py','tools/literature/compare_loftee_original.py','tools/literature/test_compare_loftee_original.py']};pins.update(build['source_pins']);inputs=out/'supplied-inputs';shutil.copytree(BASE/'workflow-expanded-inputs-1',inputs);edition='cited-fork-c8fdde00' if a.scenario=='forked-cited-source' else 'grch38-1.0.4';q=request(edition,inputs,a.runtime.resolve(),out/'analysis');q.update(input_vcf=linux(inputs/'genotyped.vcf.gz'),input_compression='gzip');q['fasta'].update(file=linux(inputs/'reference.fa.gz'),index=linux(inputs/'reference.fa.gz.fai'),gzi=linux(inputs/'reference.fa.gz.gzi'),compression='bgzip')
    def case(name,settings=None,ancestor=False,conservation=False):return dict(id=name,settings=settings or {},ancestral_check=ancestor,conservation_check=conservation,complete_cds_check='source-disabled')
    cases=[case('default-GERP'),case('explicit-percentile',dict(use_gerp_end_trunc=False)),case('slow-length',dict(fast_length_calculation='slow')),case('extended-splice',dict(run_splice_predictions=True)),case('ancestral',ancestor=True),case('apply-all',dict(run_splice_predictions=True,apply_all=True))]
    if a.scenario=='compressed-local-resources':cases += [case('local-conservation',conservation=True),case('combined-resources',ancestor=True,conservation=True)]
    elif a.scenario=='forked-cited-source':q['threads']=2
    else:cases=[case('early-success'),case('late-original-schema-error',conservation=True)]
    q['analyses']=cases
    if any(c['ancestral_check'] for c in cases):q['ancestor']=dict(kind='fasta',file=linux(inputs/'ancestor.fa'),index=linux(inputs/'ancestor.fa.fai'),compression='none',assembly_label=q['fasta']['assembly_label'],release_label='synthetic-ancestral-C104T')
    if any(c['conservation_check'] for c in cases):q['conservation']=dict(kind='sqlite',file=linux(inputs/('missing-schema.sqlite' if a.scenario=='late-failure' else 'conservation.sqlite')),assembly_label=q['fasta']['assembly_label'],release_label='synthetic-local-PhyloCSF-schema-control')
    save(out/'request.json',q);state=dict(status='FAIL2_EXPANDED_TYPED_LOFTEE_PROBE_INCOMPLETE',artifact=build['artifact'],source_pins=pins,scenario=a.scenario,edition=edition,comparisons=[],adapter_admitted=False,implementation_credit=False,whole_goal_complete=False)
    def execute(name,command,home):
        save(home/(name+'.command.json'),dict(command=command))
        with (home/(name+'.stdout')).open('xb') as first,(home/(name+'.stderr')).open('xb') as second:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=2400)
        save(home/(name+'.process.json'),dict(command=command,exit_code=r.returncode));return r.returncode
    try:
        prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec'];code=execute('typed',prefix+['/usr/bin/java','-Xmx2g','-cp',linux(jar),'org.jlinalg.cli.LofteeWorkflowCli','--request',linux(out/'request.json')],out);state['typed_exit_code']=code;print(a.scenario,'typed exit',code,flush=True);status=json.loads((out/'analysis/analysis-status.json').read_bytes())
        if a.scenario=='late-failure':
            assert code==2 and not status['result_admitted'] and not (out/'analysis/results').exists();early=out/'analysis/worker/cases/early-success';late=out/'analysis/worker/cases/late-original-schema-error';assert (early/'native-audit.json').is_file() and (late/'process-receipt.json').is_file();assert 'phylocsf_summary' in (late/'stderr.log').read_text(encoding='utf8');state['earlier_success_retained_but_unadmitted']=True;state['status']='passed-original-late-schema-failure-atomic-no-result-control'
        else:
            assert code==0 and status['result_admitted'];effective=json.loads((out/'analysis/effective-request.json').read_bytes());environment=BASE/'reference-setup-1/environment';share=environment/'share/ensembl-vep-105.0-1';bioperl=BASE/'bioperl-setup-1/api';plugin=BASE/'source-intake-1'/('loftee-grch38' if edition=='grch38-1.0.4' else 'loftee38-cited-fork')
            for c in cases:
                stored=next(x for x in effective['analyses'] if x['id']==c['id']);assert stored['settings']=={**DEFAULTS,**c['settings']};home=out/('direct-'+c['id']);home.mkdir();arguments=direct_options(c,q,PurePosixPath(linux(out/'analysis/inputs')),PurePosixPath(linux(plugin)),PurePosixPath(linux(home)));command=prefix+['/usr/bin/env','-i','PATH='+linux(environment/'bin')+':/usr/bin:/bin','HOME='+linux(home),'LC_ALL=C','TZ=UTC','PERL5LIB='+':'.join(linux(f) for f in [plugin,share,bioperl]),'LD_LIBRARY_PATH='+linux(environment/'lib'),linux(environment/'bin/perl'),linux(share/'vep'),*arguments];assert execute('original',command,home)==0;native=out/'analysis/worker/cases'/c['id'];comparison=compare_files(native/'original.vcf',home/'original.vcf',bool(c['settings'].get('run_splice_predictions')));audit=json.loads((native/'native-audit.json').read_bytes());assert audit['variants']==15 and audit['annotations']==80;ledger=(native/'native-annotations.tsv').read_text(encoding='utf8')
                if c['ancestral_check']:assert 'ANC_ALLELE' in ledger,'Positive ancestral-state control was not exercised'
                if c['conservation_check']:assert 'PHYLOCSF_UNLIKELY_ORF' in ledger and 'PHYLOCSF_WEAK' in ledger,'Positive SQLite conservation flags not exercised'
                state['comparisons'].append(dict(case=c['id'],complete_source_record_comparison=comparison,typed_vcf=pin(native/'original.vcf'),original_vcf=pin(home/'original.vcf'),native_audit=audit,nonbiological_path_time_version_header_metadata_not_compared=True));print(c['id'],'complete original comparison passed',flush=True)
            state['status']='passed-expanded-typed-LOFTEE-and-independent-original-complete-record-comparisons'
        for receipt in (out/'analysis/worker').rglob('process-receipt.json'):assert all(not x['alive_after_cleanup'] for x in json.loads(receipt.read_bytes())['owned_process_cleanup'])
        assert all(pin(ROOT/n)==v for n,v in pins.items())
    except Exception as error:state['status']='FAIL2_EXPANDED_TYPED_LOFTEE_PROBE';state['error']=repr(error)
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-record comparisons')
    raise SystemExit(main())
