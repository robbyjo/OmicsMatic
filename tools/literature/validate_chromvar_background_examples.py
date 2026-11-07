# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute complete checked background/deviation examples and fresh Windows25/Linux follow-ups."""
import argparse,csv,json,os,subprocess
from pathlib import Path
from prepare_chromvar_background_request import ROOT,BASE,save
from probe_chromvar_background import comparison,CLI
from probe_chromvar_request import equal
from validate_mashr_native import JAVA21,linux,pin
NATIVE='org.jlinalg.cli.ChromVarRequestCli';EXAMPLES=ROOT/'examples/chromvar-background'
def read(p):return json.loads(p.read_bytes())
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in root.rglob('*') if p.is_file()}
def score_comparison(root,reference):
    z=list(csv.DictReader((root/'deviations.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t'));assert len(z)==18
    for i,mid in enumerate(['motif-multi','motif-single','motif-empty']):
        for j in range(6):row=z[i*6+j];assert (row['motif_id'],row['cell_id'])==(mid,'cell'+str(j+1));equal(row['deviation'],reference['deviations'][i][j]);equal(row['z'],reference['z'][i][j])
    for i,row in enumerate(csv.DictReader((root/'motif-summary.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t')):equal(row['fraction_matches'],reference['fractionMatches'][i]);equal(row['fraction_background_overlap'],reference['fractionBackgroundOverlap'][i])
    for i,row in enumerate(csv.DictReader((root/'expectations.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t')):assert row['peak_id']=='peak'+str(i+1);equal(row['expectation'],reference['expectation'][i])
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='documented-background-native-deviation-examples-in-progress'))
    try:
        build=read(BASE/'background-workflow-build-2/BUILD.json');jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());assert read(BASE/'background-reference-chain-1/COMPLETED.json')['actual_original_indexed_reference_formula_and_public_background_and_deviations'];reference=read(BASE/'background-reference-chain-1/original-reference/original.json');names=['tools/literature/validate_chromvar_background_examples.py','tools/literature/prepare_chromvar_background_examples.py','tools/literature/probe_chromvar_background.py','tools/literature/probe_chromvar_request.py','docs/literature/CHROMVAR_BACKGROUND_DEVELOPMENT.md']+[p.relative_to(ROOT).as_posix() for p in EXAMPLES.rglob('*') if p.is_file()];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        def command(name,argv,expected=0):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==expected,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1100:])
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)];runs=[];verifications=0
        def verify(label,path,cli,literal=None):
            nonlocal verifications
            req=literal or out/(label+'-verify.json')
            if literal is None:save(req,dict(schema_version=1,mode='VERIFY',analysis=path.relative_to(out).as_posix()))
            for name,java,convert in platforms:command(label+'-'+name,[*java,'-Xmx768m','-cp',convert(jar),cli,'--request',convert(req)]);verifications+=1
        for reqname,vername,cli in [('request.json','verify-request.json',CLI),('deviations-request.json','deviations-verify-request.json',NATIVE),('legacy-request.json','legacy-verify-request.json',CLI),('legacy-deviations-request.json','legacy-deviations-verify-request.json',NATIVE)]:
            request=read(EXAMPLES/reqname);target=(EXAMPLES/request['out']).resolve();assert not target.exists();command(reqname,[JAVA21/'java.exe','-Xmx768m','-cp',jar,cli,'--request',EXAMPLES/reqname]);comparison(target,reference) if cli==CLI else score_comparison(target,reference);verify(reqname,target,cli,EXAMPLES/vername);before=inventory(target);command(reqname+'-existing-output-refused',[JAVA21/'java.exe','-Xmx768m','-cp',jar,cli,'--request',EXAMPLES/reqname],2);assert inventory(target)==before;runs.append(dict(request_path=(EXAMPLES/reqname).relative_to(ROOT).as_posix(),analysis_path=target.relative_to(ROOT).as_posix(),kind='BACKGROUND' if cli==CLI else 'DEVIATIONS',original_comparison=True,captured_verifications=3,existing_output_preserved=True))
        current=read(EXAMPLES/'request.json');background=out/'fresh Java25 background β';current['out']=str(background)
        for key in ['counts','peak_features','cells','fasta','fai','peak_ranges','r_motif_library','r_signac_library','r_base_library']:current[key]=str((EXAMPLES/current[key]).resolve())
        save(out/'fresh-java25-background.json',current);command('fresh-java25-BACKGROUND',[platforms[1][1][0],'-Xmx768m','-cp',jar,CLI,'--request',out/'fresh-java25-background.json']);comparison(background,reference);verify('fresh-java25-background',background,CLI);runs.append(dict(analysis_path=background.relative_to(ROOT).as_posix(),kind='BACKGROUND',platform='windows-java25',original_comparison=True,captured_verifications=3))
        for label,java,convert in [platforms[1],platforms[2]]:
            request=read(EXAMPLES/'deviations-request.json');target=out/('fresh '+label+' deviations β');request['out']=convert(target);request['background']=convert(background/'background.tsv')
            for key in ['counts','peak_features','cells','motif_labels','annotation_peaks']:request[key]=convert((EXAMPLES/request[key]).resolve())
            req=out/('fresh-'+label+'-deviations.json');save(req,request);command('fresh-'+label+'-DEVIATIONS',[*java,'-Xmx768m','-cp',convert(jar),NATIVE,'--request',convert(req)]);score_comparison(target,reference);verify('fresh-'+label+'-deviations',target,NATIVE);runs.append(dict(analysis_path=target.relative_to(ROOT).as_posix(),kind='DEVIATIONS',platform=label,original_comparison=True,captured_verifications=3))
        assert verifications==21 and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-documented-original-background-native-deviation-examples',source_pins=sources,artifact=build['artifact'],runs=runs,literal_BACKGROUND_requests=2,literal_DEVIATIONS_requests=2,literal_VERIFY_requests=4,additional_fresh_Java25_BACKGROUND=True,additional_fresh_Java25_and_Linux_DEVIATIONS=True,original_comparisons=7,captured_verifications=21,existing_output_refusals=4,inputs_and_requests_unchanged=True,independent_native_RNG_replay=False,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-documented-original-background-native-deviation-examples',actual_analyses=7,captured_verifications=21,existing_output_refusals=4)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_DOCUMENTED_BACKGROUND_DEVIATION_EXAMPLES',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete documented workflow checks')
    main()
