# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check actual public preflight rejects R-coercible sample IDs and a wrong phenotype header before any original program executes."""
import argparse,copy,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/leafcutter-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir();state=dict(status='FAIL2_LEAFCUTTER_INPUT_CONTRACTS_NOT_VALIDATED',checks=[],implementation_credit=False,whole_goal_complete=False)
    try:
        build=json.loads((base/'workflow-build-3/COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};sources=dict(build['source_pins']);sources[Path(__file__).resolve().relative_to(ROOT).as_posix()]=pin(Path(__file__));request=json.loads((base/'workflow-fixtures-2/request.json').read_bytes());first=next(c for c in request['analyses'] if c['kind']=='differential')
        for name,ids,expected in [('numeric-sample-ids',['01','2'],'coerce'),('logical-sample-ids',['FALSE','TRUE'],'coerce'),('wrong-ratio-header',['S1','S2'],'chrom')]:
            home=out/name;home.mkdir();counts=home/'counts.txt';groups=home/'groups.txt';counts.write_text(' '.join(ids)+'\nchr1:100:200:clu_1_+ '+('10/30 20/30' if name=='wrong-ratio-header' else '10 20')+'\nchr1:120:200:clu_1_+ '+('20/30 10/30' if name=='wrong-ratio-header' else '20 10')+'\n',encoding='utf8',newline='\n');groups.write_text(ids[0]+' baseline\n'+ids[1]+' treated\n',encoding='utf8',newline='\n');case=copy.deepcopy(first);case.update(id=name,counts=linux(counts));case['differential'].update(groups=linux(groups),minimum_samples_per_intron=1,minimum_samples_per_group=1)
            if name=='wrong-ratio-header':case=dict(id=name,kind='phenotype',counts_ratio=linux(counts),phenotype=dict(pcs=0,seed=4711,blacklist=[]))
            spec=copy.deepcopy(request);spec.update(analyses=[case],out=linux(home/'analysis'));save(home/'request.json',spec);input_pins={f.name:pin(f) for f in [counts,groups,home/'request.json']};command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx3g','-cp',linux(jar),'org.jlinalg.cli.LeafCutterWorkflowCli','--request',linux(home/'request.json')];result=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,creationflags=subprocess.CREATE_NO_WINDOW);(home/'stdout').write_bytes(result.stdout);(home/'stderr').write_bytes(result.stderr);save(home/'process.json',dict(command=command,exit_code=result.returncode));marker=json.loads((home/'analysis/analysis-status.json').read_bytes());assert result.returncode==2 and marker['result_admitted'] is False and marker['status'].startswith('FAIL2') and expected in marker['error'].lower() and (home/'analysis/WARNINGS.md').is_file() and not (home/'analysis/worker').exists() and not (home/'analysis/manifest.json').exists() and b'REQUIRED ACTION' in result.stderr;assert all(pin(home/f)==want for f,want in input_pins.items());state['checks'].append(dict(id=name,exit_code=2,original_program_executed=False,input_pins=input_pins))
        assert all(pin(ROOT/name)==want for name,want in sources.items());state.update(status='PASS_PUBLIC_LEAFCUTTER_EXPLICIT_SAMPLE_ID_AND_RATIO_HEADER_REJECTIONS_ONLY',artifact=build['artifact'],source_pins=sources)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact original-input rejection checks')
    raise SystemExit(main())
