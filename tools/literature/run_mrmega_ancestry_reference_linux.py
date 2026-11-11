# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original whole-program marker selection and full-precision source MDS."""
import argparse,json,re,struct,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_mrmega_reference_linux import ROOT,pin,save,owned


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    base=ROOT/'build/literature-review/mrmega-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    state=dict(status='FAIL2_MRMEGA_ORIGINAL_ANCESTRY_REFERENCE',implementation_credit=False,analysis_admitted=False,whole_goal_complete=False,cases=[])
    try:
        build=base/'reference-build-1';built=json.loads((build/'COMPLETED.json').read_bytes());assert built['status']=='PASS_PRIVATE_UNMODIFIED_ORIGINAL_MRMEGA_REFERENCE_BUILD_ONLY'
        source=build/'source';members=json.loads((build/'ORIGINAL-SOURCE-FILES.json').read_bytes());assert all(pin(source/name)==value for name,value in members.items())
        executable=ROOT/built['executable']['path'];assert pin(executable)=={k:built['executable'][k] for k in ['bytes','sha256']}
        fixtures=base/'ancestry-controls-1';prepared=json.loads((fixtures/'COMPLETED.json').read_bytes());assert prepared['complete_cases']==12 and all(pin(fixtures/name)==value for name,value in prepared['files'].items())
        cases=json.loads((fixtures/'cases.json').read_bytes());component=[]
        for case in cases:
            home=out/case['id'];home.mkdir();paths=[fixtures/name for name in case['cohort_files']]
            listing=home/'cohorts.in';listing.write_text('\n'.join(map(str,paths))+'\n',encoding='utf8')
            command=[str(executable),'--qt','--pc',str(case['axes']),'--debug','--print_pcs_and_die','-i',str(listing),'-o',str(home/'original')]
            assert owned(command,home/'whole-program-mds',home)==0,'Read complete original ancestry stdout/stderr'
            stdout=(home/'whole-program-mds/stdout').read_text();log=(home/'original.log').read_text()
            assert 'Time to die' in stdout and 'Analysis finished.' not in log
            chosen=[line.strip() for line in log.split('Selected markers:',1)[1].split('eaf values:',1)[0].splitlines() if line.strip()]
            assert chosen and len(chosen)==len(set(chosen))
            by={row['id']:(number,row) for number,row in enumerate(case['variants'])};assert set(chosen)<=set(by)
            pc_lines=log.split('Principal components:',1)[1].strip().splitlines();assert pc_lines[0].split()==['PCs',*['PC'+str(i) for i in range(case['axes'])]]
            rounded=[]
            for path,line in zip(paths,pc_lines[1:],strict=True):
                fields=line.split();assert fields[0]==str(path) and len(fields)==case['axes']+1;rounded.append([float(v) for v in fields[1:]])
            f32=lambda x:struct.unpack('f',struct.pack('f',x))[0]
            stored=[[f32(v) for v in by[id][1]['frequencies']] for id in chosen]
            assert all(.01<=value<=.99 for row in stored for value in row)
            component.append(f"{case['id']} {case['cohorts']} {case['axes']} {len(chosen)}")
            component.extend(' '.join(format(v,'.17g') for v in row) for row in stored)
            component.extend(format(y,'.17g')+' '+format(w,'.17g') for y,w in zip(case['effects'],case['weights'],strict=True))
            record=dict(id=case['id'],cohorts=case['cohorts'],axes=case['axes'],selected_ids=chosen,
                selected_source_ordinals=[by[id][0] for id in chosen],stored_frequency_matrix=stored,
                original_log_coordinates_six_significant_digits=rounded,eligible_variants=int(re.search(r'Altogether (\d+) good markers',stdout).group(1)),
                program_log=pin(home/'original.log'),original_full_metaregression_not_executed=True)
            save(home/'COMPLETED.json',record);state['cases'].append(record);print('PASS original ancestry marker population '+case['id'],flush=True)
        data=out/'component-controls.txt';data.write_text('\n'.join(component)+'\n',encoding='utf8')
        files='ap.cpp readFile.cpp chisquaredistr.cpp cohort.cpp filter.cpp gammafunc.cpp global.cpp ialglib.cpp ibetaf.cpp igammaf.cpp marker.cpp mds.cpp normaldistr.cpp regression.cpp structures.cpp studenttdistr.cpp tools.cpp'.split()
        probe=ROOT/'tools/literature/mrmega_ancestry_probe.cpp';binary=out/'original-ancestry-probe'
        command=['/usr/bin/g++','-I',str(source),*[str(source/name) for name in files],str(probe),'-Wno-deprecated','-O1','-lz','-o',str(binary)]
        assert owned(command,out/'compile',out)==0,'Original MDS public-function probe compile failed'
        values=out/'full-precision-values';values.mkdir();assert owned([str(binary),str(data),str(values)],out/'component-run',out)==0
        assert {path.stem for path in values.glob('*.tsv')}=={case['id'] for case in cases}
        for case in state['cases']:
            parsed={line.split('\t')[0]:[float(v) for v in line.split('\t')[1].split(',')] for line in (values/(case['id']+'.tsv')).read_text().splitlines()}
            assert set(parsed)=={'distances','coordinates','coefficients','errors','covariance','statistics'}
            assert len(parsed['coordinates'])==case['cohorts']*case['axes']
            for a,b in zip(parsed['coordinates'],[v for row in case['original_log_coordinates_six_significant_digits'] for v in row],strict=True):assert abs(a-b)<=5e-6*max(1,abs(a))
            case['full_precision_values']=pin(values/(case['id']+'.tsv'))
        assert all(pin(source/name)==value for name,value in members.items()) and all(pin(fixtures/name)==value for name,value in prepared['files'].items())
        state.update(status='PASS_TWELVE_ORIGINAL_MARKER_POPULATIONS_MDS_AND_COUPLED_REGRESSIONS_ONLY',complete_cases=12,
            original_sources_unchanged=members,original_executable_unchanged=pin(executable),
            source_pins={path.relative_to(ROOT).as_posix():pin(path) for path in [Path(__file__).resolve(),probe]},
            source_scope='Original main program runs quantitative input ingestion and ancestry computation only. Full-precision public MDS/regression functions are separate unchanged-source calls; complete original GWAS meta-regression file output remains untested in this checkpoint.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full original marker/MDS population checks')
    raise SystemExit(main())
