# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently recheck complete already-admitted candidate output after a retained strict unordered-field mismatch."""
import argparse,json,subprocess
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save
from setup_loftee_reference import linux
from probe_loftee_workflow import BASE,DEFAULTS,direct_options
from compare_loftee_original import compare_files

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--attempt',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();attempt=a.attempt.resolve();out=a.out.resolve();assert attempt.is_relative_to(BASE) and out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True);previous=json.loads((attempt/'COMPLETED.json').read_bytes());assert previous['status']=='FAIL2_EXPANDED_TYPED_LOFTEE_PROBE' and previous['typed_exit_code']==0;artifact=previous['artifact'];assert pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']};assert pin(attempt/'probe-at-attempt.py')==previous['source_pins']['tools/literature/probe_loftee_workflow_expanded.py'];assert all(pin(ROOT/n)==v for n,v in previous['source_pins'].items());analysis=attempt/'analysis';status=json.loads((analysis/'analysis-status.json').read_bytes());assert status['result_admitted'];manifest=json.loads((analysis/'manifest.json').read_bytes());assert all(pin(analysis/n)==v for n,v in manifest['outputs'].items());q=json.loads((attempt/'request.json').read_bytes());effective=json.loads((analysis/'effective-request.json').read_bytes());source=Path(__file__).resolve();pins={f.relative_to(ROOT).as_posix():pin(f) for f in [source,ROOT/'tools/literature/compare_loftee_original.py',ROOT/'tools/literature/test_compare_loftee_original.py']};(out/'rechecker-at-attempt.py').write_bytes(source.read_bytes());state=dict(status='FAIL2_INDEPENDENT_ORIGINAL_RECHECK_INCOMPLETE',artifact=artifact,typed_execution_reused_from=str(attempt.relative_to(ROOT)),prior_failed_comparison_preserved=True,prior_completed_receipt=pin(attempt/'COMPLETED.json'),prior_analysis_manifest=pin(analysis/'manifest.json'),source_pins=pins,comparisons=[],adapter_admitted=False,implementation_credit=False,whole_goal_complete=False)
    def execute(name,command,home):
        with (home/(name+'.stdout')).open('xb') as first,(home/(name+'.stderr')).open('xb') as second:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=300)
        save(home/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
    try:
        import sys
        execute('comparator-contract-tests',[sys.executable,str(ROOT/'tools/literature/test_compare_loftee_original.py')],out);prefix=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec'];environment=BASE/'reference-setup-1/environment';share=environment/'share/ensembl-vep-105.0-1';bioperl=BASE/'bioperl-setup-1/api';plugin=BASE/'source-intake-1'/('loftee-grch38' if q['edition']=='grch38-1.0.4' else 'loftee38-cited-fork')
        for c in q['analyses']:
            stored=next(x for x in effective['analyses'] if x['id']==c['id']);assert stored['settings']=={**DEFAULTS,**c['settings']};home=out/('direct-'+c['id']);home.mkdir();arguments=direct_options(c,q,PurePosixPath(linux(analysis/'inputs')),PurePosixPath(linux(plugin)),PurePosixPath(linux(home)));command=prefix+['/usr/bin/env','-i','PATH='+linux(environment/'bin')+':/usr/bin:/bin','HOME='+linux(home),'LC_ALL=C','TZ=UTC','PERL5LIB='+':'.join(linux(f) for f in [plugin,share,bioperl]),'LD_LIBRARY_PATH='+linux(environment/'lib'),linux(environment/'bin/perl'),linux(share/'vep'),*arguments];execute('original',command,home);native=analysis/'worker/cases'/c['id'];comparison=compare_files(native/'original.vcf',home/'original.vcf',bool(c['settings'].get('run_splice_predictions')));audit=json.loads((native/'native-audit.json').read_bytes());assert audit['variants']==15 and audit['annotations']==80;ledger=(native/'native-annotations.tsv').read_text(encoding='utf8')
            if c['ancestral_check']:assert 'ANC_ALLELE' in ledger,'Positive ancestral-state control not exercised'
            if c['conservation_check']:assert 'PHYLOCSF_UNLIKELY_ORF' in ledger and 'PHYLOCSF_WEAK' in ledger,'Positive conservation flags not exercised'
            state['comparisons'].append(dict(case=c['id'],complete_comparison=comparison,typed_vcf=pin(native/'original.vcf'),independent_original_vcf=pin(home/'original.vcf'),native_audit=audit));print(c['id'],'complete source comparison passed',flush=True)
        assert all(pin(analysis/n)==v for n,v in manifest['outputs'].items()) and all(pin(ROOT/n)==v for n,v in pins.items());state['status']='passed-complete-independent-original-recheck-with-explicit-source-unordered-info-contract'
    except Exception as error:state['error']=repr(error)
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete prior output or original source checks')
    raise SystemExit(main())
