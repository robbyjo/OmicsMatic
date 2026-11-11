# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise typed Java workflow and independently launched original VEP/LOFTEE complete records."""
import argparse,copy,json,shutil,subprocess
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save
from setup_loftee_reference import linux

BASE=ROOT/'build/literature-review/loftee-runtime'
DEFAULTS=dict(min_intron_size=15,fast_length_calculation='fast',use_gerp_end_trunc=True,gerp_end_trunc_cutoff=-58,filter_position=.05,get_splice_features=True,run_splice_predictions=False,weak_donor_cutoff=-4,donor_disruption_mes_cutoff=6,acceptor_disruption_mes_cutoff=7,donor_disruption_cutoff=.98,acceptor_disruption_cutoff=.99,max_scan_distance=15,donor_rescue_cutoff=8.5,acceptor_rescue_cutoff=8.5,exonic_denovo_only=True,max_denovo_donor_distance=200,denovo_donor_cutoff=.995,sre_flanksize=100,apply_all=False)


def request(edition,inputs,runtime,out):
    label='SYNTHETIC_LOFTEE_CONTROLS';return dict(schema_version=1,mode='ANALYZE',profile='original-VEP105.0-LOFTEE-GRCh38-Linux-x86_64',edition=edition,input_vcf=linux(inputs/'input.vcf'),input_compression='none',reference_origin='synthetic-controls',transcripts=dict(kind='gtf',file=linux(inputs/'genes.gtf.gz'),index=linux(inputs/'genes.gtf.gz.tbi'),assembly_label=label,release_label='five-synthetic-transcripts'),fasta=dict(file=linux(inputs/'reference.fa'),index=linux(inputs/'reference.fa.fai'),compression='none',assembly_label=label,release_label='entirely-synthetic-4000-bases'),gerp=dict(kind='bigwig',file=linux(inputs/'gerp.bw'),assembly_label=label,release_label='synthetic-two-level-GERP'),ancestor=dict(kind='source-disabled'),conservation=dict(kind='source-disabled'),runtime=dict(environment=linux(runtime/'environment'),bioperl=linux(runtime/'bioperl'),source=linux(runtime/'source'/edition)),analyses=[dict(id='default-GERP',settings={},ancestral_check=False,conservation_check=False,complete_cds_check='source-disabled'),dict(id='explicit-percentile',settings=dict(use_gerp_end_trunc=False),ancestral_check=False,conservation_check=False,complete_cds_check='source-disabled')],out=linux(out),threads=1,timeout_millis=1800000)


def table(path):
    lines=path.read_text(encoding='utf8').splitlines();csq=next(s for s in lines if s.startswith('##INFO=<ID=CSQ,'));return dict(CSQ_header=csq,column_header=next(s for s in lines if s.startswith('#CHROM\t')),records=[s for s in lines if not s.startswith('#')])


def direct_options(case,q,inputs,plugin,home):
    options=['--input_file',str(inputs/('input.vcf.gz' if q['input_compression']=='gzip' else 'input.vcf')),'--output_file',str(home/'original.vcf'),'--format','vcf','--vcf','--fasta',str(inputs/('reference.fa.gz' if q['fasta']['compression']=='bgzip' else 'reference.fa')),'--species','homo_sapiens','--assembly',q['fasta']['assembly_label'],'--no_stats','--allele_number','--biotype','--numbers','--symbol','--canonical','--mane','--check_ref','--buffer_size',str(q.get('buffer_size',5000)),'--dir_plugins',str(plugin),'--warning_file',str(home/'original.warnings.txt')]
    if q['transcripts']['kind']=='gtf':options+=['--gtf',str(inputs/'genes.gtf.gz')]
    else:options+=['--offline','--cache','--dir_cache',str(inputs/'cache'),'--cache_version','105']
    if q.get('threads',1)>1:options+=['--fork',str(q['threads'])]
    settings={**DEFAULTS,**case['settings']};plugin_options=['LoF','loftee_path:'+str(plugin),'human_ancestor_fa:'+str(inputs/('ancestor.fa.gz' if q.get('ancestor',{}).get('compression')=='bgzip' else 'ancestor.fa')) if case['ancestral_check'] else 'human_ancestor_fa:false','conservation_file:'+str(inputs/'conservation.sqlite') if case['conservation_check'] else 'conservation_file:false','check_complete_cds:false']
    if settings['use_gerp_end_trunc']:plugin_options+=['gerp_bigwig:'+str(inputs/'gerp.bw')]
    for key,value in sorted(settings.items()):
        if settings['use_gerp_end_trunc'] and key=='filter_position' or not settings['use_gerp_end_trunc'] and key=='gerp_end_trunc_cutoff':continue
        encoded=('true' if value else 'false') if key=='apply_all' else ('1' if value else '0') if isinstance(value,bool) else str(value);plugin_options.append(key+':'+encoded)
    return options+['--plugin',','.join(plugin_options)]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--edition',default='grch38-1.0.4');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};source=Path(__file__).resolve();(out/'probe-at-attempt.py').write_bytes(source.read_bytes());pins={source.relative_to(ROOT).as_posix():pin(source),**build['source_pins']};inputs=out/'original-inputs';shutil.copytree(BASE/'original-probe-4/inputs',inputs);q=request(a.edition,inputs,a.runtime.resolve(),out/'analysis');save(out/'request.json',q);state=dict(status='FAIL2_TYPED_LOFTEE_RUNTIME_PROBE_INCOMPLETE',artifact=build['artifact'],source_pins=pins,edition=a.edition,comparisons=[],adapter_admitted=False,implementation_credit=False,whole_goal_complete=False)
    def execute(name,command,home=out):
        with (home/(name+'.stdout')).open('xb') as first,(home/(name+'.stderr')).open('xb') as second:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=1800)
        save(home/(name+'.process.json'),dict(command=command,exit_code=r.returncode));return r.returncode
    try:
        prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec'];code=execute('typed',prefix+['/usr/bin/java','-Xmx2g','-cp',linux(jar),'org.jlinalg.cli.LofteeWorkflowCli','--request',linux(out/'request.json')]);state['typed_exit_code']=code;print('typed original workflow exit',code,flush=True);assert code==0;status=json.loads((out/'analysis/analysis-status.json').read_bytes());assert status['result_admitted'];effective=json.loads((out/'analysis/effective-request.json').read_bytes())
        environment=BASE/'reference-setup-1/environment';share=environment/'share/ensembl-vep-105.0-1';bioperl=BASE/'bioperl-setup-1/api';plugin=BASE/'source-intake-1'/('loftee-grch38' if a.edition=='grch38-1.0.4' else 'loftee38-cited-fork')
        for case in q['analyses']:
            stored=next(c for c in effective['analyses'] if c['id']==case['id']);assert stored['settings']=={**DEFAULTS,**case['settings']};home=out/('direct-'+case['id']);home.mkdir();captured=out/'analysis/inputs';arguments=direct_options(case,q,PurePosixPath(linux(captured)),PurePosixPath(linux(plugin)),PurePosixPath(linux(home)));command=prefix+['/usr/bin/env','-i','PATH='+linux(environment/'bin')+':/usr/bin:/bin','HOME='+linux(home),'LC_ALL=C','TZ=UTC','PERL5LIB='+':'.join(linux(p) for p in [plugin,share,bioperl]),'LD_LIBRARY_PATH='+linux(environment/'lib'),linux(environment/'bin/perl'),linux(share/'vep'),*arguments];assert execute('original',command,home)==0;native=out/'analysis/worker/cases'/case['id'];assert table(home/'original.vcf')==table(native/'original.vcf'),'Complete original CSQ header/sample header/records differ';audit=json.loads((native/'native-audit.json').read_bytes());assert (audit['variants'],audit['annotations'],audit['HC'],audit['LC'])==(15,80,8 if case['id']=='default-GERP' else 10,2 if case['id']=='default-GERP' else 0);state['comparisons'].append(dict(case=case['id'],original_vcf=pin(home/'original.vcf'),typed_vcf=pin(native/'original.vcf'),all_original_data_records_equal=True,all_CSQ_and_sample_header_fields_equal=True,only_path_time_version_header_metadata_not_compared=True));print(case['id']+': complete original records and fields equal',flush=True)
        assert all(pin(ROOT/n)==v for n,v in pins.items());state['status']='passed-typed-LOFTEE-and-independent-original-complete-record-comparisons'
    except Exception as error:state['error']=repr(error)
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original/input/source comparisons')
    raise SystemExit(main())
