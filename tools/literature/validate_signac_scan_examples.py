# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run literal scan examples, a Unicode Java25 fit, exported PFMs, and generated Java-only enrichment."""
import argparse,copy,csv,json,os,subprocess
from pathlib import Path
from prepare_signac_scan_request import prepare,save,ROOT,BASE
from validate_signac_motif_workflow import compare
from validate_mashr_native import JAVA21,linux,pin

EXAMPLES=ROOT/'examples/signac-scan'
CLI='org.jlinalg.cli.SignacScanRequestCli'
NATIVE='org.jlinalg.cli.SignacMotifsRequestCli'
def read(path):return json.loads(path.read_bytes())

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--reuse-examples-from',type=Path);p.add_argument('--reuse-unicode-from',type=Path);a=p.parse_args();out=a.out.resolve()
    assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='scan-examples-in-progress'))
    try:
        build=read(BASE/'scan-workflow-build-2/BUILD.json');jar=ROOT/build['artifact']['path']
        assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        names=['tools/literature/prepare_signac_scan_examples.py','tools/literature/validate_signac_scan_examples.py','tools/literature/export_signac_pfms.R','tools/literature/prepare_signac_scan_request.py','tools/literature/validate_signac_motif_workflow.py']
        names += [p.relative_to(ROOT).as_posix() for p in EXAMPLES.rglob('*') if p.is_file()]
        sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        reuse=a.reuse_examples_from.resolve() if a.reuse_examples_from else None
        if reuse:
            assert reuse.is_relative_to(BASE.resolve()) and read(reuse/'FAILURE.json')['status']=='FAILED_SCAN_EXAMPLES'
            earlier=read(reuse/'SOURCE-PINS.json')
            for n,v in earlier.items():
                assert pin(reuse/'source-snapshot'/n)==v
                if n!='tools/literature/validate_signac_scan_examples.py':assert pin(ROOT/n)==v
            failed=reuse/'Unicode β analysis';assert read(failed/'analysis-status.json')['status']=='failed'
            assert 'Motif names in data matrix and PWM list are inconsistent' in (failed/'WARNINGS.md').read_text(encoding='utf8')
            assert not (failed/'enrichment.tsv').exists() and not (failed/'motif-presence.mtx').exists()
        for n in sources:
            target=out/'source-snapshot'/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/n).read_bytes())
        def command(name,argv,env=None):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=240,check=False)
            (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode))
            assert r.returncode==0,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1600:])
        def prior_command(name,argv):
            old=read(reuse/(name+'.process.json'));assert old==dict(command=list(map(str,argv)),exit_code=0)
            save(out/(name+'.reused-process-receipt.json'),dict(previous_attempt=reuse.relative_to(ROOT).as_posix(),receipt=pin(reuse/(name+'.process.json')),original_command=old,analysis_reexecuted=False))
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        verifications=0
        def verify(name,target,cli):
            nonlocal verifications
            for platform,java,convert in platforms:
                path=out/(name+'-'+platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)))
                command(name+'-'+platform,[*java,'-Xmx768m','-cp',convert(jar),cli,'--request',convert(path)]);verifications+=1
        originals=read(BASE/'scan-original-7/original.json');data=read(BASE/'scan-original-7/cases.json');runs=[];native_runs=[]
        def check(target,case,profile,unicode_ids=False):
            ref=copy.deepcopy(next(r['result']['value'] for r in originals['results'] if r['id']==case and r['profile']==profile))
            if unicode_ids:
                for row in ref['enrichment']:row['motif']+=' α'
            compare(target/'enrichment.tsv',dict(rows=ref['enrichment']),profile=='1.6.0-source')
            for file,key in [('rng-before.tsv','seed_before'),('rng-after.tsv','seed_after')]:
                with (target/'worker'/file).open(encoding='utf8',newline='') as stream:got=[int(r['value']) for r in csv.DictReader(stream,delimiter='\t')]
                assert got==ref[key]
            return ref
        for name,case in [('request.json','matched'),('legacy-request.json','matched'),('all-background-request.json','all-background')]:
            path=EXAMPLES/name;r=read(path);target=(EXAMPLES/r['out']).resolve();assert target.is_relative_to((ROOT/'build').resolve()) and (target.exists() if reuse else not target.exists())
            (prior_command if reuse else command)(name+'-FIT',[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',path]);ref=check(target,case,r['profile']);verify(name,target,CLI)
            runs.append(dict(request_path=path.relative_to(ROOT).as_posix(),analysis_path=target.relative_to(ROOT).as_posix(),original_enrichment_and_RNG_match=True))
            generated=target/'native-enrichment-request.json';nr=read(generated);nt=(target/nr['out']).resolve();assert (nt.exists() if reuse else not nt.exists())
            (prior_command if reuse else command)(name+'-native-ENRICH',[JAVA21/'java.exe','-Xmx768m','-cp',jar,NATIVE,'--request',generated]);compare(nt/'enrichment.tsv',dict(rows=ref['enrichment']),r['profile']=='1.6.0-source');verify(name+'-native',nt,NATIVE)
            native_runs.append(dict(analysis_path=nt.relative_to(ROOT).as_posix(),generated_request_executed_unchanged=True,requires_R=False))
        command('literal-VERIFY',[JAVA21/'java.exe','-Xmx768m','-cp',jar,CLI,'--request',EXAMPLES/'verify-request.json']);verifications+=1
        # A fresh Java25 fit with Unicode cells and paths; the motif-ID failure is retained.
        ud=copy.deepcopy(data);case=next(c for c in ud['cases'] if c['id']=='matched');dataset=ud['datasets'][case['dataset']]
        dataset['cells']=[v+' β' for v in dataset['cells']]
        unicode_reuse=a.reuse_unicode_from.resolve() if a.reuse_unicode_from else None
        if unicode_reuse:
            assert unicode_reuse.is_relative_to(BASE.resolve()) and read(unicode_reuse/'FAILURE.json')['status']=='FAILED_SCAN_EXAMPLES'
            for n,v in read(unicode_reuse/'SOURCE-PINS.json').items():
                assert pin(unicode_reuse/'source-snapshot'/n)==v
                if n!='tools/literature/validate_signac_scan_examples.py':assert pin(ROOT/n)==v
            path=unicode_reuse/'unicode-request.json';r=read(path);target=Path(r['out'])
            receipt=read(unicode_reuse/'unicode-Java25-FIT.process.json');assert receipt==dict(command=list(map(str,[platforms[1][1][0],'-Xmx768m','-cp',jar,CLI,'--request',path])),exit_code=0)
            save(out/'unicode-reused-process-receipt.json',dict(previous_attempt=unicode_reuse.relative_to(ROOT).as_posix(),receipt=pin(unicode_reuse/'unicode-Java25-FIT.process.json'),analysis_reexecuted=False))
        else:
            target=out/'Unicode β analysis';r=prepare(ud,case,'1.17.1-package',out/'Unicode α inputs',target);path=out/'unicode-request.json';save(path,r)
            command('unicode-Java25-FIT',[platforms[1][1][0],'-Xmx768m','-cp',jar,CLI,'--request',path])
        check(target,'matched',r['profile']);verify('unicode',target,CLI)
        runs.append(dict(analysis_path=target.relative_to(ROOT).as_posix(),platform='windows-java25',Unicode_cells_and_paths=True,original_enrichment_and_RNG_match=True))
        # Test actual valid TFBSTools PFM objects and exact fractional counts/background.
        script=out/'export-pfm-probe.R';protocol=out/'export-paths.json';exported=out/'exported-pfms.tsv'
        save(protocol,dict(libraries=[str(BASE/'motif-reference-setup-1/R-lib'),str(BASE/'reference-setup-1/R-lib'),str(ROOT/'build/omics-sctransform-r')],source=str(ROOT/'tools/literature/export_signac_pfms.R'),out=str(exported),id='list-identity β',name='factor α'))
        script.write_text('''args<-commandArgs(TRUE);p<-readLines(args[[1]],warn=FALSE,encoding="UTF-8");.libPaths(c(p,.Library),include.site=FALSE)
library(jsonlite);x<-fromJSON(args[[2]]);source(x$source,encoding="UTF-8")
m<-matrix(c(9.5,0.5,0,0,0,7.25,2.75,0),nrow=4,dimnames=list(c("A","C","G","T"),NULL));b<-c(A=.1,C=.2,G=.3,T=.4)
pf<-TFBSTools::PFMatrix(ID="matrix-internal",name=x$name,bg=b,profileMatrix=m);pfms<-TFBSTools::PFMatrixList(pf);names(pfms)<-x$id
effective<-methods::slot(pf,"profileMatrix");stopifnot(identical(as.numeric(effective),trunc(as.numeric(m))))
write_json(list(declared=unname(m),effective=unname(effective),source_constructor_coerces_to_integer=TRUE),paste0(x$out,".constructor.json"),digits=17,auto_unbox=TRUE,pretty=TRUE)
receipt<-write_signac_pfms(pfms,x$out);stopifnot(receipt$positions==2L,receipt$counts_and_background_unchanged)
before<-readBin(x$out,"raw",n=file.info(x$out)$size);stopifnot(inherits(try(write_signac_pfms(pfms,x$out),silent=TRUE),"try-error"),identical(before,readBin(x$out,"raw",n=file.info(x$out)$size)))
write_signac_pfms(pfms,paste0(x$out,".matrix-id"),id_source="matrix_id")
dups<-TFBSTools::PFMatrixList(pf,pf);names(dups)<-c("duplicate","duplicate");stopifnot(inherits(try(write_signac_pfms(dups,paste0(x$out,".bad")),silent=TRUE),"try-error"),!file.exists(paste0(x$out,".bad")))
cat("PASS exported exact PFM slots/background; upstream constructor integer coercion recorded; duplicate and overwrite rejection\\n")
''',encoding='utf8',newline='\n')
        libs=out/'export-libraries.txt';libs.write_text('\n'.join(read(protocol)['libraries'])+'\n',encoding='utf8',newline='\n')
        env={k:v for k,v in os.environ.items() if not k.startswith('LC_') and k not in {'LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS'}};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        command('export-PFM',[r['rscript'],'--vanilla',script,libs,protocol],env)
        with exported.open(encoding='utf8',newline='') as stream:rows=list(csv.DictReader(stream,delimiter='\t'))
        assert len(rows)==2 and all(v['motif_id']=='list-identity β' and v['motif_name']=='factor α' for v in rows)
        assert [[float(v[k]) for k in ['A','C','G','T']] for v in rows]==[[9,0,0,0],[0,7,2,0]]
        assert all([float(v[k]) for k in ['bg_A','bg_C','bg_G','bg_T']]==[.1,.2,.3,.4] for v in rows)
        harness=out/'ReadExportedPfm.java';harness.write_text('''import java.nio.file.*;import org.jlinalg.sequence.SignacPfmIO;public class ReadExportedPfm {public static void main(String[] args)throws Exception {var m=SignacPfmIO.read(Path.of(args[0]),SignacPfmIO.Limits.defaults(),()->false).getFirst();if(!m.id().equals("list-identity β")||m.positions()[1][1]!=7||m.background()[3]!=.4)throw new AssertionError();System.out.println("PASS actual Java PFM import");}}''',encoding='utf8')
        command('compile-export-import',[JAVA21/'javac.exe','-encoding','UTF-8','-cp',jar,'-d',out,harness]);command('native-PFM-import',[JAVA21/'java.exe','-cp',str(out)+os.pathsep+str(jar),'ReadExportedPfm',exported])
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        result=dict(status='passed-literal-scan-examples-Unicode-export-and-native-followup',source_pins=sources,artifact=build['artifact'],runs=runs,native_runs=native_runs,literal_FIT_requests=3,successful_literal_fits_reused_without_reexecution=bool(reuse),previous_attempt=reuse.relative_to(ROOT).as_posix() if reuse else None,retained_source_Unicode_motif_ID_failure=bool(reuse),fresh_Java25_Unicode_fit=True,original_enrichment_and_RNG_comparisons=4,generated_native_enrichment_comparisons=3,captured_verifications=verifications,literal_VERIFY_request_executed=True,actual_PFM_export_and_Java_import=True,inputs_and_requests_unchanged=True,full_Signac_workflow_available=False,catalog_implementation_credit=False)
        save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],captured_verifications=verifications)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_SCAN_EXAMPLES',error=str(error)));raise

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable literal scan example checks')
    main()
