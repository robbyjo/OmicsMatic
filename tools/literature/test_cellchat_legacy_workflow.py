# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual legacy-JAR failure/skip admission and rehashed scientific-output regressions."""
import argparse,copy,hashlib,json,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/cellchat-runtime'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe';jar=a.jar.resolve();source=Path(__file__).resolve();bindings={str(jar):pin(jar),str(source):pin(source)};records=[];state=dict(status='FAIL2_LEGACY_WORKFLOW_ADMISSION_CONTROLS',new_catalog_credit=False,whole_goal_complete=False)
    baseline=json.loads((BASE/'legacy-typed-controls-7/request.json').read_bytes());profile=json.loads(Path(baseline['runtime_profile']).read_bytes())
    def execute(name,request,expected):
        home=out/name;home.mkdir();request['out']=str(home/'analysis');save(home/'request.json',request);command=[java,'-Xmx1g','-jar',str(jar),'--request',str(home/'request.json')]
        with (home/'stdout').open('xb') as so,(home/'stderr').open('xb') as se:r=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=300)
        record=dict(id=name,command=command,exit_code=r.returncode,expected_exit=expected);save(home/'process.json',record);records.append(record);assert r.returncode==expected,(name,r.returncode,(home/'stderr').read_text()[-2000:]);assert b'WARNINGS / REQUIRED ACTIONS:' in (home/'stderr').read_bytes()
        target=home/'analysis'
        if target.exists():
            report=json.loads((target/'COMPLETED.json').read_bytes());assert not report['result_admitted'] and report['status'].startswith('SKIP3' if expected==3 else 'FAIL2');assert (target/'WARNINGS.md').is_file() and not (target/'results').exists()
        return home
    try:
        cases=[]
        def case(name,fn,expected=2):r=copy.deepcopy(baseline);fn(r);cases.append((name,r,expected))
        case('missing-runtime-profile',lambda r:r.update(runtime_profile=str(out/'absent-profile.json')),3)
        missing=copy.deepcopy(profile);missing['libraries']=[str(out/'absent-library')];save(out/'missing-library-profile.json',missing)
        case('missing-runtime-library',lambda r:r.update(runtime_profile=str(out/'missing-library-profile.json')),3)
        wrong=copy.deepcopy(profile);wrong['source_label']='2.2.0-2025-release';save(out/'wrong-edition-profile.json',wrong)
        case('wrong-edition',lambda r:r.update(runtime_profile=str(out/'wrong-edition-profile.json')))
        case('wrong-expression-units',lambda r:r['analysis'].update(expression_units='counts'))
        case('alpha-endpoint',lambda r:r['analysis']['projection'].update(alpha=1))
        case('null-truncated-mean',lambda r:r['analysis']['inference'].update(type='truncatedMean',trim=None))
        case('unknown-analysis-field',lambda r:r['analysis'].update(unapproved=True))
        case('nboot-capacity',lambda r:r['analysis']['inference'].update(nboot=1))
        case('output-capacity',lambda r:r['limits'].update(output_bytes=1024))
        case('complete-human-ppi-capacity',lambda r:r['analysis']['projection'].update(species='human'))
        negative=out/'negative-late.tsv';raw=Path(baseline['analysis']['expression']).read_text();rows=raw.splitlines();fields=rows[-1].split('\t');fields[-1]='-1';rows[-1]='\t'.join(fields);negative.write_text('\n'.join(rows)+'\n',encoding='utf8')
        case('negative-later-input',lambda r:r['analysis'].update(expression=str(negative)))
        reordered=out/'reordered-meta.tsv';rows=Path(baseline['analysis']['metadata']).read_text().splitlines();rows[1],rows[2]=rows[2],rows[1];reordered.write_text('\n'.join(rows)+'\n',encoding='utf8')
        case('phenotype-order',lambda r:r['analysis'].update(metadata=str(reordered)))
        corrupt=copy.deepcopy(profile);corrupt['files'][profile['rscript']]['sha256']='0'*64;save(out/'changed-runtime-profile.json',corrupt)
        case('runtime-hash',lambda r:r.update(runtime_profile=str(out/'changed-runtime-profile.json')))
        original=Path(profile['source_root']);copySource=out/'owned-source-copy';shutil.copytree(original,copySource);file=copySource/'R/utilities.R';file.write_bytes(file.read_bytes()+b'\n# deliberate owned source guard fault\n');sourceProfile=copy.deepcopy(profile);sourceProfile['source_root']=str(copySource);save(out/'source-fault-profile.json',sourceProfile)
        case('source-hash',lambda r:r.update(runtime_profile=str(out/'source-fault-profile.json')))
        case('owned-timeout',lambda r:r['limits'].update(wall_seconds=1))
        case('original-no-selected-LR',lambda r:r['analysis']['overexpression'].update(thresh_fc=100))
        case('original-no-differential-selection',lambda r:r['analysis']['differential'].update(up_ligand_logfc=100))
        for name,request,expected in cases:
            home=execute(name,request,expected)
            if name=='complete-human-ppi-capacity':assert not (home/'analysis/process-started.json').exists()
            if name=='owned-timeout':
                pid=json.loads((home/'analysis/process-started.json').read_bytes())['pid'];probe=subprocess.run(['powershell.exe','-NoProfile','-Command',f'if (Get-Process -Id {int(pid)} -ErrorAction SilentlyContinue) {{ exit 1 }} else {{ exit 0 }}'],capture_output=True,timeout=30);assert probe.returncode==0,'Owned R process remained live';save(home/'owned-process-terminal.json',dict(pid=pid,terminal=True,exit_code=probe.returncode))
        # Tamper copies preserve originals. Rehash outer manifests to exercise
        # scientific replay, not merely a changed SHA check.
        complete=BASE/'legacy-typed-controls-7/analysis';tampers=out/'tampered';tampers.mkdir()
        variants=['missing-file','extra-file','changed-raw-probability','changed-projection','changed-SNN-score','changed-neighbor','changed-information-flow','changed-bubble-original-prob','missing-source-warning']
        for name in variants:
            target=tampers/name;shutil.copytree(complete,target);manifest=json.loads((target/'COMPLETED.json').read_bytes())
            if name=='missing-file':(target/'results/cases/condition-1/pathways.tsv').unlink()
            elif name=='extra-file':(target/'extra.txt').write_text('unexpected')
            elif name=='missing-source-warning':
                file=target/'WARNINGS.md';file.write_text('# completed-original-cellchat-legacy-workflow-with-source-warnings\nno source diagnostics\n',encoding='utf8');manifest['files']['WARNINGS.md']=pin(file)
            else:
                relative,column={
                    'changed-raw-probability':('results/cases/condition-1/probabilities-unfiltered.tsv','value'),
                    'changed-projection':('results/cases/condition-1/projected-expression.tsv','c1'),
                    'changed-SNN-score':('results/comparison/functional-similarity.tsv','value'),
                    'changed-neighbor':('results/comparison/functional-knn.tsv','neighbor'),
                    'changed-information-flow':('results/comparison/information-flow.tsv','contribution'),
                    'changed-bubble-original-prob':('results/comparison/bubble-all.tsv','prob.original')
                }[name]
                import csv,io
                file=target/relative;rows=list(csv.reader(io.StringIO(file.read_text(encoding='utf8')),delimiter='\t'));index=1 if name=='changed-projection' else rows[0].index(column);rows[1][index]='123.5' if name!='changed-neighbor' else '99999';buffer=io.StringIO(newline='');writer=csv.writer(buffer,delimiter='\t',quoting=csv.QUOTE_ALL,lineterminator='\n');writer.writerows(rows);file.write_text(buffer.getvalue(),encoding='utf8',newline='\n');manifest['files'][relative]=pin(file)
            if name not in ['missing-file','extra-file']:save(target/'COMPLETED.json',manifest)
            command=[java,'-Xmx1g','-jar',str(jar),'--verify',str(target)];r=subprocess.run(command,capture_output=True,timeout=180);(tampers/(name+'.stdout')).write_bytes(r.stdout);(tampers/(name+'.stderr')).write_bytes(r.stderr);records.append(dict(id='tamper-'+name,command=command,exit_code=r.returncode,expected_exit=2));assert r.returncode==2,(name,r.stdout,r.stderr)
        assert all(pin(Path(n))==v for n,v in bindings.items())
        state.update(status='PASS_LEGACY_WORKFLOW_SKIP_FAILURE_AND_REHASHED_REFUSALS',analysis_dispositions=len(cases),offline_tamper_refusals=len(variants),owned_timeout_terminal=True,source_pins=bindings,records=records)
    except Exception as error:state['error']=repr(error);state['records']=records;(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific admission regressions')
    raise SystemExit(main())
