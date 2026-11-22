from pathlib import Path
import csv,json,subprocess
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';P=F/'workflow-packaged-6';B=F/'workflow-rounded-gate-control-3';B.mkdir();jar=F/'workflow-normal-build-9/project-build/cli/omicsmatic-0.4.0.jar';assert (P/'assoc-w21/COMPLETED.json').is_file()
r=B/'choose.R';r.write_text('''args<-commandArgs(TRUE)
s<-readRDS(args[1])$association
x<-read.table(args[2],header=TRUE,sep="\\t",as.is=TRUE)
s<-s[match(x$ID,s$ID),]
i<-which(is.finite(s$TWAS.P)&is.finite(x$TWAS.P)&s$TWAS.P>x$TWAS.P)[1]
stopifnot(length(i)==1L,!is.na(i))
write.table(data.frame(ID=as.character(x$ID[i]),RAW=sprintf("%.17g",s$TWAS.P[i]),EXPORTED=sprintf("%.17g",x$TWAS.P[i]),THRESHOLD=sprintf("%.17g",(s$TWAS.P[i]+x$TWAS.P[i])/2)),args[3],sep="\\t",quote=FALSE,row.names=FALSE)
''',encoding='utf-8')
p=subprocess.run(['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(r),str(P/'assoc-w21/worker/source-association-state.rds'),str(P/'assoc-w21/worker/out/result'),str(B/'BOUNDARY.tsv')],cwd=T,capture_output=True);(B/'choose.stdout').write_bytes(p.stdout);(B/'choose.stderr').write_bytes(p.stderr);assert p.returncode==0,p.stderr
boundary=list(csv.DictReader((B/'BOUNDARY.tsv').open(encoding='utf-8'),delimiter='\t'))[0];threshold=float(boundary['THRESHOLD']);assert float(boundary['EXPORTED'])<threshold<float(boundary['RAW'])
r=json.loads((P/'assoc-w21.json').read_bytes());r['out']=str(B/'run');r['options']={'coloc_P':threshold,'GWASN':10000};r['expected_packages']['coloc']='5.2.3';file=B/'request.json';file.write_text(json.dumps(r,indent=2)+'\n');args=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx2g','-jar',str(jar),'fusion-tools','--request',str(file)]
with (B/'stdout').open('xb') as out,(B/'stderr').open('xb') as err:p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);code=p.wait()
assert code==0,(B/'stderr').read_text(errors='replace')[-1500:]
summary=json.loads((B/'run/summary.json').read_bytes());assert summary['coloc_rounded_gate_disagreements']>=1 and summary['missing_requested_coloc_rows']==0 and summary['source_csv_rounding_verified']
values=list(csv.DictReader((B/'run/worker/out/result').open(encoding='utf-8'),delimiter='\t'));row=next(row for row in values if row['ID']==boundary['ID']);assert float(row['TWAS.P'])<threshold and row['COLOC.PP4'].strip()=='NA'
result=dict(status='PASS_UNROUNDED_SOURCE_GATE_AT_REAL_ROUNDING_BOUNDARY',exit_code=code,boundary=boundary,summary=summary,original_missing_posterior_preserved=True,incorrect_export_based_rejection_avoided=True,original_scripts_or_scientific_results_modified=False)
(B/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result),flush=True)