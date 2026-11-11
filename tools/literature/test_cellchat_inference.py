# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual CellChat inference JAR skip/failure and rehashed-output refusals."""
import argparse,copy,csv,io,json,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--baseline',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    jar=a.jar.resolve();baseline=a.baseline.resolve();java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
    request=json.loads((baseline/'source-request.json').read_bytes());profile=json.loads((baseline/'runtime-profile.json').read_bytes());records=[]
    bindings={str(jar):pin(jar),str(Path(__file__).resolve()):pin(Path(__file__).resolve())};state=dict(status='FAIL2_INFERENCE_ADMISSION_CONTROLS',new_catalog_credit=False,whole_goal_complete=False)
    try:
        cases=[]
        def case(name,fn,expected=2):r=copy.deepcopy(request);fn(r);cases.append((name,r,expected))
        case('missing-runtime',lambda r:r.update(runtime_profile=str(out/'absent.json')),3)
        wrong=copy.deepcopy(profile);wrong['source_label']='1.0.0';save(out/'wrong-edition.json',wrong)
        case('wrong-edition',lambda r:r.update(runtime_profile=str(out/'wrong-edition.json')))
        missing=copy.deepcopy(profile);missing['libraries']=[str(out/'absent-library')];save(out/'missing-library.json',missing)
        case('missing-library',lambda r:r.update(runtime_profile=str(out/'missing-library.json')),3)
        case('unknown-field',lambda r:r['analysis'].update(unapproved=True))
        case('unprojected-raw-false',lambda r:r['analysis']['inference'].update(raw_use=False))
        case('legacy-thresholdedMean',lambda r:(r['analysis'].update(source_label='1.0.0'),r['analysis']['inference'].update(type='thresholdedMean',trim=0.1)))
        case('legacy-partial-multi-category',lambda r:r['analysis'].update(source_label='1.0.0',database_categories=['Secreted Signaling','ECM-Receptor']))
        case('legacy-sample-filter',lambda r:r['analysis'].update(source_label='1.0.0',filter=dict(min_cells=10,min_samples=2,rare_keep=False)))
        case('invalid-nboot',lambda r:r['analysis']['inference'].update(nboot=1))
        case('complete-human-ppi-capacity',lambda r:(r['analysis'].update(projection=dict(species='human',alpha=0.5,normalization='rows')),r['limits'].update(projection_working_bytes=1024)))
        negative=out/'negative.tsv';rows=Path(request['analysis']['expression']).read_text().splitlines();last=rows[-1].split('\t');last[-1]='-1';rows[-1]='\t'.join(last);negative.write_text('\n'.join(rows)+'\n',encoding='utf8')
        case('negative-later-input',lambda r:r['analysis'].update(expression=str(negative)))
        reordered=out/'reordered.tsv';rows=Path(request['analysis']['metadata']).read_text().splitlines();rows[1],rows[2]=rows[2],rows[1];reordered.write_text('\n'.join(rows)+'\n',encoding='utf8')
        case('cell-order',lambda r:r['analysis'].update(metadata=str(reordered)))
        bad=copy.deepcopy(profile);bad['files'][profile['rscript']]['sha256']='0'*64;save(out/'runtime-hash.json',bad)
        case('runtime-hash',lambda r:r.update(runtime_profile=str(out/'runtime-hash.json')))
        owned=out/'owned-source';shutil.copytree(Path(profile['source_root']),owned);file=owned/'R/utilities.R';file.write_bytes(file.read_bytes()+b'\n# deliberate owned-copy source fault\n');bad=copy.deepcopy(profile);bad['source_root']=str(owned);save(out/'source-hash.json',bad)
        case('source-hash',lambda r:r.update(runtime_profile=str(out/'source-hash.json')))
        case('owned-timeout',lambda r:r['limits'].update(wall_seconds=1))
        case('original-no-selected-LR',lambda r:r['analysis']['overexpression'].update(thresh_fc=100))
        for name,r,expected in cases:
            home=out/name;home.mkdir();r['out']=str(home/'analysis');save(home/'request.json',r);cmd=[java,'-Xmx1g','-jar',str(jar),'--request',str(home/'request.json')]
            with (home/'stdout').open('xb') as so,(home/'stderr').open('xb') as se:process=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=420)
            row=dict(id=name,command=cmd,exit_code=process.returncode,expected_exit=expected);records.append(row);save(home/'process.json',row);assert process.returncode==expected,(name,process.returncode,(home/'stderr').read_text()[-1800:]);assert 'WARNINGS / REQUIRED ACTIONS:' in (home/'stderr').read_text()
            target=home/'analysis'
            if target.exists():
                d=json.loads((target/'COMPLETED.json').read_bytes());assert not d['result_admitted'] and d['status'].startswith('SKIP3' if expected==3 else 'FAIL2');assert (target/'WARNINGS.md').exists() and not(target/'results').exists()
            if name=='complete-human-ppi-capacity':assert not(target/'process-started.json').exists()
            if name=='owned-timeout':
                pid=int(json.loads((target/'process-started.json').read_bytes())['pid']);probe=subprocess.run(['powershell.exe','-NoProfile','-Command',f'if (Get-Process -Id {pid} -ErrorAction SilentlyContinue) {{ exit 1 }} else {{ exit 0 }}'],capture_output=True,timeout=30);assert probe.returncode==0;save(home/'owned-process-terminal.json',dict(pid=pid,terminal=True))
        tampers=out/'tampered';tampers.mkdir();variants=['missing-file','extra-file','raw-probability','unrequested-filter','network-weight','centrality','missing-source-warning']
        for name in variants:
            target=tampers/name;shutil.copytree(baseline,target);manifest=json.loads((target/'COMPLETED.json').read_bytes())
            if name=='missing-file':(target/'results/cases/condition-1/pathways.tsv').unlink()
            elif name=='extra-file':(target/'extra.txt').write_text('unexpected')
            elif name=='missing-source-warning':
                f=target/'WARNINGS.md';f.write_text('# '+manifest['status']+'\nno diagnostics\n',encoding='utf8');manifest['files']['WARNINGS.md']=pin(f)
            else:
                relative={'raw-probability':'probabilities-unfiltered.tsv','unrequested-filter':'probabilities-filtered.tsv','network-weight':'network-weights.tsv','centrality':'centrality.tsv'}[name];relative='results/cases/condition-1/'+relative;f=target/relative;rows=list(csv.reader(io.StringIO(f.read_text()),delimiter='\t'));index=rows[0].index('value');chosen=next(i for i,row in enumerate(rows[1:],1) if row[rows[0].index('metric')]=='outdeg') if name=='centrality' else 1;rows[chosen][index]='123.5';buffer=io.StringIO(newline='');writer=csv.writer(buffer,delimiter='\t',quoting=csv.QUOTE_ALL,lineterminator='\n');writer.writerows(rows);f.write_text(buffer.getvalue(),encoding='utf8',newline='\n');manifest['files'][relative]=pin(f)
            if name not in ['missing-file','extra-file']:save(target/'COMPLETED.json',manifest)
            cmd=[java,'-Xmx1g','-jar',str(jar),'--verify',str(target)];r=subprocess.run(cmd,capture_output=True,timeout=180);(tampers/(name+'.stdout')).write_bytes(r.stdout);(tampers/(name+'.stderr')).write_bytes(r.stderr);records.append(dict(id='tamper-'+name,command=cmd,exit_code=r.returncode,expected_exit=2));assert r.returncode==2,(name,r.stdout,r.stderr)
        assert all(pin(Path(n))==v for n,v in bindings.items());state.update(status='PASS_INFERENCE_SKIP_FAILURE_AND_REHASHED_REFUSALS',analysis_dispositions=len(cases),offline_tamper_refusals=len(variants),owned_timeout_terminal=True,source_pins=bindings,records=records)
    except Exception as error:state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific admission regressions')
    raise SystemExit(main())
