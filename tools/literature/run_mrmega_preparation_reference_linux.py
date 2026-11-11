# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture full-precision original file preparation and regression through original APIs."""
import argparse,json,traceback
from pathlib import Path
from setup_mrmega_reference_linux import ROOT,pin,save,owned


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    base=ROOT/'build/literature-review/mrmega-runtime';out=args.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    state=dict(status='FAIL2_ORIGINAL_MRMEGA_FILE_PREPARATION_REFERENCE',implementation_credit=False,whole_goal_complete=False,cases=[])
    try:
        source=base/'reference-build-1/source';members=json.loads((base/'reference-build-1/ORIGINAL-SOURCE-FILES.json').read_bytes());assert all(pin(source/n)==v for n,v in members.items())
        controls=base/'workflow-controls-1';prepared=json.loads((controls/'COMPLETED.json').read_bytes());assert all(pin(controls/n)==v for n,v in prepared['files'].items())
        previous=json.loads((base/'workflow-original-1/COMPLETED.json').read_bytes());assert previous['status']=='PASS_TWENTY_TWO_COMPLETE_ORIGINAL_MRMEGA_GWAS_FILE_WORKFLOWS'
        object=out/'original-main-entry-renamed.o'
        # Rename only the entry-point symbol to link public parser functions into our test executable.
        # Original scientific code and all source files remain unchanged. Original CLI runs are separate.
        assert owned(['/usr/bin/g++','-Dmain=mrmega_original_entry','-c',str(source/'main.cpp'),'-Wno-deprecated','-O1','-o',str(object)],out/'compile-original-entry',out)==0
        names='ap.cpp readFile.cpp chisquaredistr.cpp cohort.cpp filter.cpp gammafunc.cpp global.cpp ialglib.cpp ibetaf.cpp igammaf.cpp marker.cpp mds.cpp normaldistr.cpp regression.cpp structures.cpp studenttdistr.cpp tools.cpp'.split()
        probe=ROOT/'tools/literature/mrmega_preparation_probe.cpp';binary=out/'original-preparation-probe'
        assert owned(['/usr/bin/g++','-I',str(source),*[str(source/n) for n in names],str(probe),str(object),'-Wno-deprecated','-O1','-lz','-o',str(binary)],out/'compile-probe',out)==0
        for case in json.loads((controls/'cases.json').read_bytes()):
            id=case['id'];s=case['settings'];home=out/id;home.mkdir()
            lines=['filelist '+str(controls/case['file_lists']['linux']),'axes '+str(s['axes']),'quantitative '+str(int(s['trait']=='quantitative')),
                'precalculated '+str(int(s['ancestry']=='precalculated')),'noalleles '+str(int(s['no_alleles'])),'inputgc '+str(int(s['input_gc'])),
                'outputgc '+str(int(s['output_gc'])),'standards '+str(int(s['standard_names']))]
            for col,names in s['aliases'].items():
                for name in names:lines.append('alias '+col+' '+name)
            for f in s['filters']:lines.append('filter '+f['column']+f['operator']+str(f['value']))
            if s['ancestry']=='inferred':
                log=(base/'workflow-original-1'/id/'original.log').read_text();chosen=[v.strip() for v in log.split('Selected markers:',1)[1].split('eaf values:',1)[0].splitlines() if v.strip()]
                lines.extend('selected '+v for v in chosen)
            config=home/'probe-settings.txt';config.write_text('\n'.join(lines)+'\n',encoding='utf8')
            assert owned([str(binary),str(config),str(home)],home/'original-apis',home)==0,(id,'Read original API logs')
            outputs={name:pin(home/name) for name in ['prepared.tsv','fits.tsv','lambdas.tsv','coordinates.tsv','output-lambda.txt','original-api.log']}
            state['cases'].append(dict(id=id,outputs=outputs,original_parser_and_marker_weight_APIs_executed=True));print('PASS full-precision original file preparation '+id,flush=True)
        assert all(pin(source/n)==v for n,v in members.items()) and all(pin(controls/n)==v for n,v in prepared['files'].items())
        state.update(status='PASS_TWENTY_TWO_FULL_PRECISION_ORIGINAL_FILE_PREPARATION_AND_REGRESSION_FAMILIES',original_sources_unchanged=members,
            entrypoint_only_renamed_for_test_linking=True,complete_original_CLI_runs_separate=True,
            source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),probe]})
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source/input checks')
    raise SystemExit(main())
