# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test actual java -jar chromvar-tools routing, source reports, original fits and native follow-ups."""
import json,csv,copy,subprocess
from pathlib import Path
from validate_mashr_native import JAVA21,linux,pin
from prepare_chromvar_background_request import prepare,save,ROOT,BASE
from probe_chromvar_background import comparison
from probe_chromvar_request import equal
def read(p):return json.loads(p.read_bytes())
def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in root.rglob('*') if p.is_file()}
def scores(root,reference,peaks,cells):
    rows=list(csv.DictReader((root/'deviations.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t'));assert len(rows)==18
    for i,mid in enumerate(['motif-multi','motif-single','motif-empty']):
        for j,cell in enumerate(cells):row=rows[i*len(cells)+j];assert (row['motif_id'],row['cell_id'])==(mid,cell);equal(row['deviation'],reference['deviations'][i][j]);equal(row['z'],reference['z'][i][j])
    for i,row in enumerate(csv.DictReader((root/'motif-summary.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t')):equal(row['fraction_matches'],reference['fractionMatches'][i]);equal(row['fraction_background_overlap'],reference['fractionBackgroundOverlap'][i])
    for i,row in enumerate(csv.DictReader((root/'expectations.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t')):assert row['peak_id']==peaks[i];equal(row['expectation'],reference['expectation'][i])
def main():
    out=BASE/'chromvar-public-controls-1';assert not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='actual-main-chromvar-controls-in-progress'))
    try:
        build=read(BASE/'chromvar-public-workflow-1/BUILD.json');jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());names=['tools/literature/validate_chromvar_public.py','tools/literature/prepare_chromvar_background_request.py','tools/literature/probe_chromvar_background.py','tools/literature/probe_chromvar_request.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        def command(label,argv,expected=0):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==expected,(label,r.returncode,r.stderr.decode('utf8',errors='replace')[-1300:]);return r
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        for label,java,convert in platforms:
            result=command(label+'-help',[*java,'-jar',convert(jar),'chromvar-tools','--help']);assert b'BACKGROUND' in result.stdout and b'VERIFY' in result.stdout and b'SKIP3' in result.stdout and b'RNG' in result.stdout
            result=command(label+'-root-help',[*java,'-jar',convert(jar),'--help']);assert b'chromvar-tools --help' in result.stdout
            result=command(label+'-source-report',[*java,'-jar',convert(jar),'chromvar-tools','--source-report']);report=json.loads(result.stdout);assert report['R_functions_are_byte_identical'] and report['paper_1_5_0_exact_commit_unknown'] and report['records'][1]['upstream_commit']=='3236dcc515efa01ce54114aee03b227466168126' and not report['ArchR_pipeline_available']
        data=read(BASE/'background-original-controls-1/cases.json');case=next(v for v in data['cases'] if v['id']=='baseline');reference=read(BASE/'background-reference-chain-1/original-reference/original.json');runs=[];verify_count=0
        def verify(label,analysis):
            nonlocal verify_count
            path=out/(label+'-verify.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=Path(__import__('os').path.relpath(analysis,out)).as_posix()))
            for platform,java,convert in platforms:command(label+'-'+platform,[*java,'-Xmx768m','-jar',convert(jar),'chromvar-tools','--request',convert(path)]);verify_count+=1
        for index,(platform,java,convert) in enumerate(platforms[:2]):
            analysis=out/('public '+platform+' background β');directory=out/('inputs-'+platform);request,peaks,cells=prepare(directory,analysis,data['datasets']['baseline'],case,'1.34.1-package',reference=True);request['timeout']=90;save(directory/'request.json',request);command(platform+'-BACKGROUND',[*java,'-Xmx768m','-jar',convert(jar),'chromvar-tools','--request',convert(directory/'request.json')]);comparison(analysis,reference);verify(platform+'-background',analysis);runs.append(dict(platform=platform,kind='BACKGROUND',analysis_path=analysis.relative_to(ROOT).as_posix(),original_statistics_and_RNG_match=True,captured_verifications=3))
            motifs=out/('motifs-'+platform);motifs.mkdir();(motifs/'labels.tsv').write_text('motif_id\tmotif_name\nmotif-multi\tMulti\nmotif-single\tSingle\nmotif-empty\tEmpty\n',encoding='utf8',newline='\n');(motifs/'members.tsv').write_text('motif_id\tpeak_id\n'+''.join('motif-multi\t'+peaks[i-1]+'\n' for i in [1,3,5,7,11])+'motif-single\t'+peaks[1]+'\n',encoding='utf8',newline='\n');deviations=out/('public '+platform+' deviations β');follow=read(analysis/'prepared-inputs.json')
            for key in ['counts','peak_features','cells','background']:follow[key]=str(analysis/follow[key])
            follow.update(out=str(deviations),motif_collection='synthetic-original-public-controls-not-JASPAR',motif_labels=str(motifs/'labels.tsv'),annotation_peaks=str(motifs/'members.tsv'));path=out/(platform+'-deviations.json');save(path,follow);command(platform+'-DEVIATIONS',[*java,'-Xmx768m','-jar',convert(jar),'chromvar-tools','--request',convert(path)]);scores(deviations,reference,peaks,cells);verify(platform+'-deviations',deviations);runs.append(dict(platform=platform,kind='DEVIATIONS',analysis_path=deviations.relative_to(ROOT).as_posix(),original_statistics_match=True,captured_verifications=3))
            if index==0:linux_follow=copy.deepcopy(follow);linux_peaks=peaks;linux_cells=cells;source_request=copy.deepcopy(request)
        platform,java,convert=platforms[2];deviations=out/'public Linux deviations β'
        for key in ['counts','peak_features','cells','background','motif_labels','annotation_peaks']:linux_follow[key]=convert(Path(linux_follow[key]))
        linux_follow['out']=convert(deviations);path=out/'linux-deviations.json';save(path,linux_follow);command('linux-DEVIATIONS',[*java,'-Xmx768m','-jar',convert(jar),'chromvar-tools','--request',convert(path)]);scores(deviations,reference,linux_peaks,linux_cells);verify('linux-deviations',deviations);runs.append(dict(platform=platform,kind='DEVIATIONS',analysis_path=deviations.relative_to(ROOT).as_posix(),original_statistics_match=True,captured_verifications=3))
        for name in ['chromvar-background-demo-NEW','chromvar-background-legacy-demo-NEW','chromvar-background-deviations-demo-NEW','chromvar-background-legacy-deviations-demo-NEW']:verify(name,ROOT/'build'/name)
        missing=copy.deepcopy(source_request);missing['rscript']=str(out/'unavailable/Rscript.exe');missing['out']=str(out/'public skipped');save(out/'missing-dependency.json',missing);command('missing-dependency',[JAVA21/'java.exe','-Xmx768m','-jar',jar,'chromvar-tools','--request',out/'missing-dependency.json'],3);skipped=out/'public skipped';assert not read(skipped/'manifest.json')['results_admitted'] and not (skipped/'background.tsv').exists();save(out/'skip-verify.json',dict(schema_version=1,mode='VERIFY',analysis=skipped.name))
        for label,java,convert in platforms:command('failed-capture-'+label,[*java,'-jar',convert(jar),'chromvar-tools','--request',convert(out/'skip-verify.json')],2)
        target=out/'public windows-java21 background β';before=inventory(target);command('existing-output-refused',[JAVA21/'java.exe','-jar',jar,'chromvar-tools','--request',out/'inputs-windows-java21/request.json'],2);assert inventory(target)==before
        for label,request_text in [('unknown-mode','{"schema_version":1,"mode":"UNKNOWN"}'),('duplicate-mode','{"schema_version":1,"mode":"BACKGROUND","mode":"DEVIATIONS"}')]:
            path=out/(label+'.json');path.write_text(request_text,encoding='utf8');command(label,[JAVA21/'java.exe','-jar',jar,'chromvar-tools','--request',path],2)
        assert verify_count==27 and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-actual-main-chromvar-routing-source-report-fit-and-native-chain',source_pins=sources,artifact=build['artifact'],runs=runs,help_root_and_source_report_protocols=9,actual_original_BACKGROUND_fits=2,actual_native_DEVIATIONS=3,actual_main_captured_verifications=27,existing_parent_capture_verifications=12,actual_dependency_skip=1,failed_capture_rejections=3,existing_output_preserved=True,malformed_requests_rejected=2,normal_Gradle_executable=True,Unicode_paths_and_IDs=True,historical_whole_packages_executed=False,independent_native_RNG_replay=False,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-actual-main-chromvar-routing-source-report-fit-and-native-chain',original_fits=2,native_analyses=3,actual_main_verifications=27,actual_dependency_skip=1)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ACTUAL_MAIN_CHROMVAR_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual-main workflow checks')
    main()
