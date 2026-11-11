# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify actual FASTA-to-background-to-native-deviation workflows against original public R calls."""
import argparse,csv,json,os,shutil,subprocess
from pathlib import Path
from prepare_chromvar_background_request import prepare,save,ROOT,BASE
from probe_chromvar_background import comparison,CLI
from probe_chromvar_request import equal
from validate_mashr_native import JAVA21,linux,pin
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='reference-background-deviation-original-comparison-in-progress'))
    try:
        build=json.loads((BASE/'background-workflow-build-2/BUILD.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']} and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());names=['tools/literature/probe_chromvar_background_reference.py','tools/literature/probe_chromvar_background_reference.R','tools/literature/prepare_chromvar_background_request.py','tools/literature/probe_chromvar_background.py','tools/literature/probe_chromvar_request.py','src/test/resources/org/jlinalg/sequence/chromvar-gc/original.json'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        def command(name,argv,cwd=ROOT,env=None):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.stderr.decode('utf8',errors='replace')[-1400:])
        data=json.loads((BASE/'background-original-controls-1/cases.json').read_bytes());case=next(v for v in data['cases'] if v['id']=='baseline');analyses=[]
        for profile in ['1.16.0-source','1.34.1-package']:
            target=out/('reference-'+profile+' β');request,peaks,cells=prepare(out/('request-'+profile),target,data['datasets']['baseline'],case,profile,reference=True);request['timeout']=90;save(out/('request-'+profile)/'request.json',request);command(profile+'-BACKGROUND',[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',out/('request-'+profile)/'request.json']);analyses.append((profile,target,request,peaks,cells))
        original_dir=out/'original-reference';original_dir.mkdir();first=analyses[0][1]
        for a,b in [('input/reference.fa','reference.fa'),('input/reference.fa.fai','reference.fa.fai'),('worker/counts.mtx','counts.mtx')]:shutil.copyfile(first/a,original_dir/b)
        captured=json.loads((first/'request.json').read_bytes());rows=list(csv.DictReader((first/captured['peak_ranges']).read_text(encoding='utf8').splitlines(),delimiter='\t'));(original_dir/'ranges.tsv').write_text('chromosome\tstart\tend\n'+''.join(f"{r['chromosome']}\t{r['start']}\t{r['end']}\n" for r in rows),encoding='ascii',newline='\n');save(original_dir/'parameters.json',{k:case[k] for k in ['seed','niterations','w','bs']})
        protocol=original_dir/'protocol.txt';protocol.write_text('\n'.join(map(str,[BASE/'motif-reference-setup-1/R-lib',BASE/'reference-setup-1/R-lib',ROOT/'build/omics-sctransform-r',original_dir/'parameters.json',ROOT/names[-1]]))+'\n',encoding='utf8',newline='\n')
        env={k:v for k,v in os.environ.items() if not k.startswith('LC_') and k not in {'LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS'}};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');command('original-public-reference',['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',ROOT/names[1],protocol],cwd=original_dir,env=env);reference=json.loads((original_dir/'original.json').read_bytes());assert reference['original_public_getBackgroundPeaks_and_computeDeviations_executed']
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)];results=[]
        for profile,target,request,peaks,cells in analyses:
            comparison(target,reference);bias=list(csv.DictReader((target/'bias.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t'));assert len(bias)==len(peaks)
            for i,row in enumerate(bias):assert row['peak_id']==peaks[i];equal(row['bias'],reference['bias'][i])
            motif=out/(profile+'-motifs');motif.mkdir();(motif/'labels.tsv').write_text('motif_id\tmotif_name\nmotif-multi\tMulti\nmotif-single\tSingle\nmotif-empty\tEmpty\n',encoding='utf8',newline='\n');(motif/'members.tsv').write_text('motif_id\tpeak_id\n'+''.join('motif-multi\t'+peaks[i-1]+'\n' for i in [1,3,5,7,11])+'motif-single\t'+peaks[1]+'\n',encoding='utf8',newline='\n');deviations=out/('native-deviations-'+profile+' β');follow=json.loads((target/'prepared-inputs.json').read_bytes())
            for key in ['counts','peak_features','cells','background']:follow[key]=str(target/follow[key])
            follow.update(out=str(deviations),motif_collection='synthetic-original-reference-controls-not-JASPAR',motif_labels=str(motif/'labels.tsv'),annotation_peaks=str(motif/'members.tsv'));save(out/(profile+'-deviations-request.json'),follow);command(profile+'-DEVIATIONS',[JAVA21/'java.exe','-Xmx768m','-cp',jar,'org.jlinalg.cli.ChromVarRequestCli','--request',out/(profile+'-deviations-request.json')])
            z=list(csv.DictReader((deviations/'deviations.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t'));assert len(z)==18
            for i,mid in enumerate(['motif-multi','motif-single','motif-empty']):
                for j,cell in enumerate(cells):row=z[i*len(cells)+j];assert (row['motif_id'],row['cell_id'])==(mid,cell);equal(row['deviation'],reference['deviations'][i][j]);equal(row['z'],reference['z'][i][j])
            for i,row in enumerate(csv.DictReader((deviations/'motif-summary.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t')):equal(row['fraction_matches'],reference['fractionMatches'][i]);equal(row['fraction_background_overlap'],reference['fractionBackgroundOverlap'][i])
            for i,row in enumerate(csv.DictReader((deviations/'expectations.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t')):assert row['peak_id']==peaks[i];equal(row['expectation'],reference['expectation'][i])
            for label,analysis,cli in [('background',target,CLI),('deviations',deviations,'org.jlinalg.cli.ChromVarRequestCli')]:
                verify=out/(profile+'-'+label+'-verify.json');save(verify,dict(schema_version=1,mode='VERIFY',analysis=analysis.name))
                for name,java,convert in platforms:command(profile+'-'+label+'-'+name,[*java,'-Xmx768m','-cp',convert(jar),cli,'--request',convert(verify)])
            results.append(dict(profile=profile,background_analysis=target.relative_to(ROOT).as_posix(),deviations_analysis=deviations.relative_to(ROOT).as_posix(),original_reference_GC_backgrounds_RNG_and_scores_match=True,captured_verifications=6))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-original-reference-GC-background-native-deviation-chain',source_pins=sources,artifact=build['artifact'],runs=results,actual_original_indexed_reference_formula_and_public_background_and_deviations=True,native_reference_GC_used=True,Unicode_paths_and_IDs=True,source_profiles=2,captured_verifications=12,independent_native_RNG_replay=False,historical_whole_packages_executed=False,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-original-reference-GC-background-native-deviation-chain',profiles=2,captured_verifications=12)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ORIGINAL_REFERENCE_BACKGROUND_DEVIATION_CHAIN',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable reference/background/score checks')
    main()
