# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real CLI refusals, failed-result isolation and analytic odd-median behavior."""
import argparse,concurrent.futures,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mrmega_workflow import BASE,DISTRO,linux,table


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_MRMEGA_WORKFLOW_REFUSAL_CONTRACTS',implementation_credit=False,whole_goal_complete=False)
    try:
        fixture=BASE/'workflow-controls-1';cases=json.loads((fixture/'cases.json').read_bytes());by={c['id']:c for c in cases}
        specifications=['missing-beta','nonfinite-beta','malformed-se','zero-se','negative-se','underflow-se','sentinel-beta','truncated-row','negative-N',
            'overflow-N','missing-axis','blank-axis-list','singleton-lambda','invalid-gzip','missing-file','duplicate-json-key','zero-output-lambda',
            'collinear-model','unknown-setting','existing-output','invalid-OR','truncated-filter','directory-input','invalid-utf8','analytic-odd-median']
        fixtures=out/'fixtures';fixtures.mkdir();prepared=[]
        for id in specifications:
            home=fixtures/id;home.mkdir();base=by['binary-lower-ci' if id=='invalid-OR' else 'numeric-filter' if id=='truncated-filter' else 'qt-even']
            settings=dict(base['settings']);files=[]
            for c,name in enumerate(base['cohort_files']):
                raw=(fixture/name).read_bytes();lines=raw.decode().splitlines();header=lines[0].split();values=lines[1].split()
                if id=='singleton-lambda':lines=lines[:2]
                elif id=='zero-output-lambda':
                    lines=[lines[0]]+['\t'.join('0' if j==header.index('BETA') else v for j,v in enumerate(row.split())) for row in lines[1:]];settings['output_gc']=True
                elif id=='analytic-odd-median':
                    settings['axes']=0;lines=['MARKERNAME CHROMOSOME POSITION EA NEA EAF N BETA SE']+['v'+str(j)+' 1 '+str(j+1)+' A G .25 100 '+str(j+1)+' 1' for j in range(5)]
                if c==0:
                    replacements={'nonfinite-beta':('BETA','NaN'),'malformed-se':('SE','bad'),'zero-se':('SE','0'),'negative-se':('SE','-.1'),
                        'underflow-se':('SE','1e-80'),'sentinel-beta':('BETA','-666'),'negative-N':('N','-3'),'overflow-N':('N','2147483648'),'invalid-OR':('OR','0')}
                    if id in replacements:
                        col,value=replacements[id];values[header.index(col)]=value;lines[1]='\t'.join(values)
                    if id=='missing-beta':lines[0]=lines[0].replace('BETA','WRONG')
                    if id=='truncated-row':lines[1]='\t'.join(values[:-1])
                    if id=='truncated-filter':lines[1]='\t'.join(values[:-1])
                path=home/('cohort-'+str(c)+('.gz' if id=='invalid-gzip' and c==0 else '.tsv'))
                path.write_bytes(b'not a gzip stream' if id=='invalid-gzip' and c==0 else b'\xff\n' if id=='invalid-utf8' and c==0 else ('\n'.join(lines)+'\n').encode());files.append(path)
            lists={}
            for platform in ['windows','linux']:
                convert=(lambda p:str(p.resolve())) if platform=='windows' else linux
                lines=[]
                for c,path in enumerate(files):
                    if c==0 and id=='missing-file':path=home/'DOES-NOT-EXIST.tsv'
                    if c==0 and id=='directory-input':path=home
                    pc='' if settings['axes']==0 else ' 0 0' if id=='collinear-model' else ' '+str((c-3.5)*.09)+' '+str(((c-3.5)**2-5.25)*.013)
                    if id=='missing-axis' and c==0:pc=' 0'
                    lines.append(convert(path)+pc)
                if id=='blank-axis-list':lines.insert(1,'')
                path=home/('cohorts-'+platform+'.in');path.write_text('\n'.join(lines)+'\n',encoding='utf8');lists[platform]=path
            if id=='unknown-setting':settings['invented_option']=True
            text=json.dumps(settings)
            if id=='duplicate-json-key':text=text[:-1]+',"axes":2}'
            config=home/'settings.json';config.write_text(text,encoding='utf8')
            prepared.append(dict(id=id,config=config,lists=lists,expected_code=0 if id=='analytic-odd-median' else 2))
        input_pins={p.relative_to(ROOT).as_posix():pin(p) for p in fixtures.rglob('*') if p.is_file()}
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical)
        platforms={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
            'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']}
        def check(item):
            compiler,platform=item;home=out/(compiler+'-'+platform);home.mkdir();convert=linux if platform=='linux21' else lambda p:str(p.resolve())
            jar=BASE/('workflow-build-21-4' if compiler=='javac21' else 'workflow-build-25-3')/'mrmega-workflow-development.jar'
            cp=(':' if platform=='linux21' else ';').join(convert(p) for p in [jar,canonical]);rows=[]
            for case in prepared:
                parent=home/case['id'];parent.mkdir();native=parent/'native';sentinel=None
                if case['id']=='existing-output':native.mkdir();(native/'preserve.txt').write_bytes(b'keep this existing result\n');sentinel=pin(native/'preserve.txt')
                command=[*platforms[platform],'-cp',cp,'org.jlinalg.cli.MrMegaWorkflowCli','--settings',convert(case['config']),'--file-list',convert(case['lists']['linux' if platform=='linux21' else 'windows']),'--out',convert(native)]
                save(parent/'command.json',command);run=subprocess.run(command,capture_output=True,timeout=120);(parent/'stdout').write_bytes(run.stdout);(parent/'stderr').write_bytes(run.stderr);save(parent/'process.json',dict(exit_code=run.returncode))
                assert run.returncode==case['expected_code'],(compiler,platform,case['id'],run.stderr.decode(errors='replace'))
                if run.returncode==2:
                    assert not (native/'results.tsv').exists() and b'FAILED' in run.stderr and (b'Required action' in run.stderr or b'WARNINGS.md' in run.stderr)
                    if case['id']=='existing-output':assert {p.name for p in native.iterdir()}=={'preserve.txt'} and pin(native/'preserve.txt')==sentinel
                    elif native.exists():
                        assert 'Required action:' in (native/'WARNINGS.md').read_text() and json.loads((native/'run.json').read_bytes())['status'].startswith('FAIL2_')
                        if case['id']=='collinear-model':assert (native/'partial/results.tsv').is_file() and all(r['status']=='FAIL2_NUMERICALLY_UNRESOLVED' for r in table(native/'partial/results.tsv'))
                else:
                    assert all(float(r['lambda'])==4/.4549364 for r in table(native/'cohorts.tsv'))
                    fitted=table(native/'results.tsv');assert len(fitted)==5
                    for i,row in enumerate(fitted):assert abs(float(row['beta_0'])-(i+1))<=2e-15*(i+1) and float(row['raw_chisq_association'])==8*(i+1)**2
                rows.append(dict(id=case['id'],exit_code=run.returncode,expected_failure=run.returncode==2,no_failed_results_admitted=run.returncode!=2 or not (native/'results.tsv').exists()))
            print('PASS25 CLI contracts '+compiler+'/'+platform,flush=True);return dict(compiler=compiler,platform=platform,contracts=rows)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(check,[(c,p) for c in ['javac21','javac25'] for p in platforms]))
        assert all(pin(ROOT/n)==v for n,v in input_pins.items()) and pin(canonical)==canonical_pin
        state.update(status='PASS_TWENTY_FOUR_REAL_CLI_REFUSALS_AND_ANALYTIC_ODD_MEDIAN_ON_BOTH_BUILDS_THREE_JVMS',runs=runs,
            refusal_comparisons=144,analytic_odd_median_comparisons=6,input_pins=input_pins,canonical_executable_unchanged=canonical_pin,
            source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable real CLI failure/result isolation checks')
    raise SystemExit(main())
