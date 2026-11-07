# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the retained paper-edition functions with recorded current dependencies and compare all prior controls."""
import json,os,subprocess,shutil
from pathlib import Path
from validate_mashr_native import pin
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def read(p):return json.loads(p.read_bytes())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def scalar(x):return x[0] if isinstance(x,list) and len(x)==1 else x
def main():
    out=BASE/'chromvar-paper-source-controls-1';assert not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='paper-source-controls-in-progress'))
    try:
        resources=ROOT/'src/main/resources/org/jlinalg/omics/chromvar-paper-sources';names=['tools/literature/collect_chromvar_paper_source_controls.py','tools/literature/prepare_chromvar_paper_sources.py','tools/literature/collect_chromvar_deviations.R','tools/literature/collect_chromvar_background.R']+[p.relative_to(ROOT).as_posix() for p in resources.rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        env={k:v for k,v in os.environ.items() if not k.startswith('LC_') and k not in {'LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS'}};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');comparisons=0
        def run(name,folder,script,casefile):
            protocol=folder/'protocol.txt';protocol.write_text('\n'.join(map(str,[BASE/'motif-reference-setup-1/R-lib',BASE/'reference-setup-1/R-lib',ROOT/'build/omics-sctransform-r',casefile,resources]))+'\n',encoding='utf8',newline='\n');argv=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(protocol)];r=subprocess.run(argv,cwd=folder,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(folder/'stdout.log').write_bytes(r.stdout);(folder/'stderr.log').write_bytes(r.stderr);save(folder/'process.json',dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.stderr.decode('utf8',errors='replace')[-1000:]);return read(folder/'original.json')
        for name,previous in [('basic','chromvar-deviations-original-3'),('extended','chromvar-extended-original-1')]:
            folder=out/name;folder.mkdir();shutil.copyfile(BASE/previous/'cases.json',folder/'cases.json');result=run(name,folder,ROOT/'tools/literature/collect_chromvar_deviations.R',folder/'cases.json');old=read(BASE/previous/'original.json')['results'];assert len(result['results'])==len(old)
            for row in result['results']:
                assert scalar(row['profile']) in ['1.28.0-source','1.5.0-source'];prior=next(v for v in old if scalar(v['id'])==scalar(row['id']) and scalar(v['profile'])=='1.34.1-package');assert {k:v for k,v in row.items() if k!='profile'}=={k:v for k,v in prior.items() if k!='profile'};comparisons+=3
        folder=out/'background';folder.mkdir();shutil.copyfile(BASE/'background-original-controls-1/cases.json',folder/'cases.json');original=(ROOT/'tools/literature/collect_chromvar_background.R').read_text();a='for(profile in c("1.16.0-source","1.34.1-package"))';b='for(profile in c("1.28.0-source","1.5.0-source"))';assert original.count(a)==1 and original.count('functions[["1.34.1-package"]]')==2;adapted=original.replace(a,b).replace('functions[["1.34.1-package"]]','functions[["1.28.0-source"]]');script=folder/'collector.R';script.write_text(adapted,encoding='utf8',newline='\n');save(folder/'COLLECTOR-ADAPTATION.json',dict(source=pin(ROOT/'tools/literature/collect_chromvar_background.R'),adapted=pin(script),changes='Only source-profile iteration labels and the matching current-body/formals guard lookup; original function bodies are loaded unchanged from resources.'))
        result=run('background',folder,script,folder/'cases.json');old=read(BASE/'background-original-controls-1/original.json')['results'];assert len(result['results'])==45
        for row in result['results']:
            prior=next(v for v in old if v['id']==row['id'] and v['profile']=='1.34.1-public-Matrix-interface');assert {k:v for k,v in row.items() if k!='profile'}=={k:v for k,v in prior.items() if k!='profile'}
        assert comparisons==306 and all(pin(ROOT/n)==v for n,v in sources.items());save(out/'COMPLETED.json',dict(status='passed-original-paper-edition-source-control-correspondence',source_pins=sources,source_profiles=['1.28.0-source','1.5.0-source'],source_profile_case_protocols=102,numeric_method_comparisons=306,background_source_and_public_protocols=45,all_numeric_fields_value_kinds_and_background_RNG_states_match=True,candidate_1_5_0_commit=read(resources/'SOURCE-PROFILES.json')['records'][1]['upstream_commit'],paper_1_5_0_commit_unknown=True,current_dependencies_explicit=True,historical_whole_packages_executed=False,ArchR_pipeline_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-original-paper-edition-source-control-correspondence',numeric_methods=306,background_protocols=45)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_PAPER_EDITION_SOURCE_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original paper-edition controls')
    main()
