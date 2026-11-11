# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare scanned motif-matrix native scoring with the original public chromVAR matrix interface."""
import argparse,json,os,shutil,subprocess
from pathlib import Path
from prepare_signac_scan_request import ROOT,BASE,save
from probe_chromvar_request import equal
from validate_mashr_native import JAVA21,linux,pin
def read(p):return json.loads(p.read_bytes())
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='scanned-motif-matrix-original-interface-comparison-in-progress'))
    try:
        build=read(BASE/'chromvar-workflow-build-1/BUILD.json');jar=ROOT/build['artifact']['path'];assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};names=['tools/literature/probe_chromvar_scan_matrix.py','tools/literature/probe_chromvar_request.py','tools/literature/collect_chromvar_deviations.R'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            t=out/'source-snapshot'/n;t.parent.mkdir(parents=True,exist_ok=True);t.write_bytes((ROOT/n).read_bytes())
        inputs=out/'Unicode α inputs';inputs.mkdir();example=ROOT/'examples/signac-scan/inputs';scan=ROOT/'build/signac-scan-effective-demo-NEW';original_inputs={}
        for source,target in [(example/'counts.mtx','counts.mtx'),(example/'peak-features.tsv','peaks.tsv'),(example/'cells.tsv','cells.original.tsv'),(scan/'motif-presence.mtx','motifs.mtx'),(scan/'motif-labels.tsv','motifs.tsv')]:shutil.copyfile(source,inputs/target);original_inputs[source.relative_to(ROOT).as_posix()]=pin(source)
        peaks=(inputs/'peaks.tsv').read_text(encoding='utf8').splitlines();old=(inputs/'cells.original.tsv').read_text(encoding='utf8').splitlines();cells=[v+' β' for v in old];(inputs/'cells.tsv').write_text('\n'.join(cells)+'\n',encoding='utf8',newline='\n');(inputs/'UTF8-ID-MAPPING.tsv').write_text('original_cell_id\tprobe_cell_id\n'+''.join(a+'\t'+b+'\n' for a,b in zip(old,cells,strict=True)),encoding='utf8',newline='\n')
        background=[[((i+17*j)%len(peaks))+1 for j in range(1,8)] for i in range(len(peaks))];(inputs/'background.tsv').write_text('peak_id\t'+'\t'.join('iteration_'+str(i) for i in range(1,8))+'\n'+''.join('\t'.join([peak,*map(str,row)])+'\n' for peak,row in zip(peaks,background,strict=True)),encoding='utf8',newline='\n')
        reference=out/'reference-paths.json';save(reference,dict(counts=str(inputs/'counts.mtx'),peaks=str(inputs/'peaks.tsv'),cells=str(inputs/'cells.tsv'),motifs=str(inputs/'motifs.mtx'),labels=str(inputs/'motifs.tsv'),background=background,encoder=str(ROOT/'tools/literature/collect_chromvar_deviations.R'),out=str(out/'original.json')))
        (out/'libraries.txt').write_text('\n'.join([str(BASE/'motif-reference-setup-1/R-lib'),str(BASE/'reference-setup-1/R-lib'),str(ROOT/'build/omics-sctransform-r')])+'\n',encoding='utf8',newline='\n')
        script=out/'reference.R';script.write_text('''args<-commandArgs(TRUE);.libPaths(c(readLines(args[[1]],encoding="UTF-8"),.Library),include.site=FALSE);library(Matrix);library(chromVAR);library(jsonlite);BiocParallel::register(BiocParallel::SerialParam());stopifnot(as.character(packageVersion("chromVAR"))=="1.34.1",as.character(packageVersion("Matrix"))=="1.7.5")
p<-read_json(args[[2]],simplifyVector=FALSE);for(e in parse(p$encoder,encoding="UTF-8"))if(is.call(e)&&identical(e[[1L]],as.name("<-"))&&is.symbol(e[[2L]])&&as.character(e[[2L]])%in%c("number","encode"))eval(e,.GlobalEnv)
peaks<-readLines(p$peaks,encoding="UTF-8");cells<-readLines(p$cells,encoding="UTF-8");labels<-read.delim(p$labels,encoding="UTF-8",quote="",check.names=FALSE,stringsAsFactors=FALSE)
counts<-as(readMM(p$counts),"dgCMatrix");dimnames(counts)<-list(peaks,cells);annotations<-readMM(p$motifs);dimnames(annotations)<-list(peaks,labels$motif_id);background<-do.call(rbind,lapply(p$background,unlist));e<-computeExpectations(counts);v<-computeDeviations(counts,annotations,background_peaks=background,expectation=e)
write_json(list(z=encode(SummarizedExperiment::assay(v,"z")),deviations=encode(SummarizedExperiment::assay(v,"deviations")),fractionMatches=encode(SummarizedExperiment::rowData(v)$fractionMatches),fractionBackgroundOverlap=encode(SummarizedExperiment::rowData(v)$fractionBackgroundOverlap),expectation=encode(e),motifs=rownames(v),cells=colnames(v)),p$out,pretty=TRUE,auto_unbox=FALSE,na="string");writeLines(capture.output(sessionInfo()),file.path(dirname(p$out),"sessionInfo.txt"),useBytes=TRUE)
''',encoding='utf8',newline='\n')
        def command(name,argv,env=None):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1600:])
        env={k:v for k,v in os.environ.items() if not k.startswith('LC_') and k not in {'LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS'}};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1');command('original-public-interface',['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',script,out/'libraries.txt',reference],env)
        target=out/'Unicode β analysis';request=dict(schema_version=1,mode='DEVIATIONS',profile='1.34.1-package',out=str(target),genome_build='synthetic-scan-controls-not-a-reference',motif_collection='synthetic-PFM-scan-controls',background_provenance='explicit cyclic synthetic indices; not GC/intensity matched')
        for key,name in [('counts','counts.mtx'),('peak_features','peaks.tsv'),('cells','cells.tsv'),('motif_labels','motifs.tsv'),('motif_matrix','motifs.mtx'),('background','background.tsv')]:request[key]=str(inputs/name)
        path=out/'request.json';save(path,request);cli='org.jlinalg.cli.ChromVarRequestCli';command('DEVIATIONS',[JAVA21/'java.exe','-Xmx768m','-cp',jar,cli,'--request',path]);got=read(out/'original.json');import csv
        rows=list(csv.DictReader((target/'deviations.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t'));assert len(rows)==len(got['motifs'])*len(got['cells'])
        for i,motif in enumerate(got['motifs']):
            for j,cell in enumerate(got['cells']):
                row=rows[i*len(got['cells'])+j];assert (row['motif_id'],row['cell_id'])==(motif,cell);equal(row['z'],got['z']['rows'][i][j]);equal(row['deviation'],got['deviations']['rows'][i][j])
        for platform,java,convert in [('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]:
            path=out/(platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(platform,[*java,'-Xmx768m','-cp',convert(jar),cli,'--request',convert(path)])
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in original_inputs.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-scanned-motif-matrix-original-public-chromVAR-comparison',source_pins=sources,artifact=build['artifact'],original_input_pins=original_inputs,peaks=len(peaks),cells=len(cells),motifs=len(got['motifs']),Unicode_cell_IDs=True,actual_original_public_computeDeviations_executed=True,original_statistics_match=True,captured_verifications=3,background_sampling_performed=False,biological_dataset=False,full_Signac_workflow_available=False,catalog_implementation_credit=False));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-scanned-motif-matrix-original-public-chromVAR-comparison',captured_verifications=3)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_SCANNED_MOTIF_MATRIX_CHROMVAR_COMPARISON',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable public-interface integration checks')
    main()
