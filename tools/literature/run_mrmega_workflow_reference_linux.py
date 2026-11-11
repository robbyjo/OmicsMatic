# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete, unmodified original MR-MEGA GWAS file workflows on synthetic controls."""
import argparse,json,traceback
from pathlib import Path
from setup_mrmega_reference_linux import ROOT,pin,save,owned


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    base=ROOT/'build/literature-review/mrmega-runtime';out=args.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    state=dict(status='FAIL2_ORIGINAL_MRMEGA_COMPLETE_GWAS_REFERENCE',implementation_credit=False,whole_goal_complete=False,cases=[])
    try:
        build=base/'reference-build-1';built=json.loads((build/'COMPLETED.json').read_bytes());members=json.loads((build/'ORIGINAL-SOURCE-FILES.json').read_bytes())
        source=build/'source';assert all(pin(source/n)==v for n,v in members.items());exe=ROOT/built['executable']['path'];exe_pin=pin(exe)
        controls=base/'workflow-controls-1';prepared=json.loads((controls/'COMPLETED.json').read_bytes());assert all(pin(controls/n)==v for n,v in prepared['files'].items())
        cases=json.loads((controls/'cases.json').read_bytes());assert len(cases)==22
        alias_flags=dict(MARKERNAME='marker',EA='ea',NEA='nea',EAF='eaf',BETA='beta',SE='se',OR='or',OR_95L='or_95l',OR_95U='or_95u',CHROMOSOME='chr',POSITION='pos',N='n')
        for case in cases:
            id=case['id'];settings=case['settings'];home=out/id;home.mkdir()
            command=[str(exe),'-i',str(controls/case['file_lists']['linux']),'-o',str(home/'original'),'--pc',str(settings['axes']),'--debug']
            for use,flag in [(settings['trait']=='quantitative','--qt'),(settings['ancestry']=='precalculated','--precalculated'),(settings['no_alleles'],'--no_alleles'),
                (settings['input_gc'],'--gc'),(settings['output_gc'],'--gco'),(not settings['standard_names'],'--no_std_names')]:
                if use:command.append(flag)
            for col,names in settings['aliases'].items():
                for name in names:command.extend(['--name_'+alias_flags[col],name])
            for filter in settings['filters']:command.extend(['--filter',filter['column']+filter['operator']+str(filter['value'])])
            assert owned(command,home/'complete-program',home)==0,(id,'Read original stdout/stderr')
            log=home/'original.log';result=home/'original.result'
            assert 'Analysis finished.' in log.read_text() and result.is_file()
            rows=result.read_text().splitlines();header=rows[0].split('\t');assert len(rows)>2 and all(len(r.split('\t'))==len(header) for r in rows[1:])
            state['cases'].append(dict(id=id,complete_original_GWAS_file_workflow_executed=True,output_precision='original default six significant digits',
                result_rows=len(rows)-1,result=pin(result),program_log=pin(log)))
            print('PASS complete original GWAS '+id,flush=True)
        assert all(pin(source/n)==v for n,v in members.items()) and pin(exe)==exe_pin and all(pin(controls/n)==v for n,v in prepared['files'].items())
        state.update(status='PASS_TWENTY_TWO_COMPLETE_ORIGINAL_MRMEGA_GWAS_FILE_WORKFLOWS',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},
            original_sources_unchanged=members,original_executable_unchanged=exe_pin,controls_unchanged=pin(controls/'COMPLETED.json'))
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original source/file checks')
    raise SystemExit(main())
