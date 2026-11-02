#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Private original mashr0.2.79 reference runtime; no study analysis is admitted."""
import argparse,concurrent.futures,csv,datetime,gzip,hashlib,json,os,re,shutil,subprocess,sys,time,urllib.request,zipfile
from pathlib import Path,PurePosixPath

ROOT=Path(__file__).resolve().parents[2];SOURCE=ROOT/'build/literature-review/mashr-runtime/source-intake-1';OUT=ROOT/'build/literature-review/mashr-runtime/reference-runtime-1';RSCRIPT=Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe')
CACHE_FROM=None
ACTION='Preserve all partial downloads,packages and logs. If installation/download or the required exact binary/dependency is unavailable,keep dependent analysis skipped. Use a supported permitted R4.6.1 Windows x64 host or resolve the named requirement;retry to NEW paths. This reference setup and tiny synthetic health check do not admit study results or reproduce a paper.'
class Unavailable(Exception):pass
def pin(p):
 with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def dump(p,value):
 with p.open('x',encoding='utf-8',newline='\n') as out:json.dump(value,out,indent=2);out.write('\n')
def dcf(raw):
 rows={}
 for block in raw.split('\n\n'):
  row={};key=None
  for line in block.splitlines():
   if line[:1].isspace():assert key;row[key]+=' '+line.strip()
   elif ': ' in line:key,value=line.split(': ',1);row[key]=value
  if row:assert row['Package'] not in rows;rows[row['Package']]=row
 return rows
def run(name,args):
 env=os.environ.copy();env.update(LANG='C',LC_ALL='C',R_LIBS_USER=str(OUT/'library'),R_LIBS_SITE='',R_DEFAULT_PACKAGES='datasets,utils,grDevices,graphics,stats,methods',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
 command=[str(RSCRIPT),'--vanilla',*map(str,args)]
 with (OUT/(name+'.stdout')).open('xb') as stdout,(OUT/(name+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=OUT,env=env,stdout=stdout,stderr=stderr,timeout=600)
 dump(OUT/(name+'.process.json'),dict(command=command,exit_code=p.returncode,environment={k:env[k] for k in ['LANG','LC_ALL','R_LIBS_USER','R_LIBS_SITE','OMP_NUM_THREADS','OPENBLAS_NUM_THREADS','MKL_NUM_THREADS']}));assert p.returncode==0,(OUT/(name+'.stderr')).read_text(errors='replace')[-4000:]
def fetch(row):
 name,metadata=row;filename=metadata['File'];assert re.fullmatch(re.escape(name)+r'_[A-Za-z0-9.+-]+\.zip',filename);url='https://cran.r-project.org/bin/windows/contrib/4.6/'+filename;p=OUT/'cache'/filename
 reused=False
 if CACHE_FROM is not None and (CACHE_FROM/filename).is_file():
  cached=CACHE_FROM/filename
  try:
   with zipfile.ZipFile(cached) as z:valid=z.testzip() is None and dcf(z.read(name+'/DESCRIPTION').decode('utf-8'))[name]['Version']==metadata['Version']
  except (OSError,zipfile.BadZipFile,KeyError):valid=False
  if valid:before=pin(cached);shutil.copyfile(cached,p);assert pin(cached)==pin(p)==before;reused=True
 if not reused:
  for attempt in range(1,4):
   part=p.with_name(p.name+'.part-'+str(attempt))
   try:
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-reference-setup/1'}),timeout=120) as source,part.open('xb') as out:
     count=0
     while True:
      block=source.read(1048576)
      if not block:break
      count+=len(block);assert count<=134217728;out.write(block)
    part.rename(p);break
   except OSError as error:
    dump(OUT/'download-receipts'/(name+'-transport-'+str(attempt)+'.json'),dict(url=url,error=str(error),partial=part.name,analysis_skipped=True))
    if attempt==3:raise Unavailable('Required official CRAN binary unavailable after3 bounded attempts: '+url+';'+str(error)) from error
    time.sleep(attempt)
 with zipfile.ZipFile(p) as z:
  names=z.namelist();assert len(names)<=30000 and len(names)==len(set(n.casefold() for n in names));total=0
  for entry in z.infolist():
   path=PurePosixPath(entry.filename);assert not path.is_absolute() and '\\' not in entry.filename and ':' not in entry.filename and all(part not in ['.','..'] for part in path.parts) and path.parts[0]==name and entry.file_size<=134217728;total+=entry.file_size;assert total<=536870912
  package=dcf(z.read(name+'/DESCRIPTION').decode('utf-8'));assert package[name]['Version']==metadata['Version'];assert z.testzip() is None
 receipt=dict(package=name,version=metadata['Version'],url=url,file=filename,reused_completed_cache=reused,**pin(p));dump(OUT/'download-receipts'/(name+'.json'),receipt);return receipt
def main():
 global OUT,CACHE_FROM
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--allow-download',action='store_true');parser.add_argument('--allow-install',action='store_true');parser.add_argument('--out',type=Path,default=OUT);parser.add_argument('--cache-from',type=Path);args=parser.parse_args();OUT=args.out.resolve();assert OUT.is_relative_to((ROOT/'build/literature-review/mashr-runtime').resolve())
 if args.cache_from is not None:CACHE_FROM=args.cache_from.resolve(strict=True);assert CACHE_FROM.is_relative_to((ROOT/'build/literature-review/mashr-runtime').resolve())
 if not args.allow_download or not args.allow_install:parser.exit(3,'mashr SKIP3: explicit --allow-download and --allow-install are required.\n'+ACTION+'\n')
 if sys.platform!='win32' or not RSCRIPT.is_file():parser.exit(3,'mashr SKIP3: required permitted original Windows R4.6.1 unavailable.\n'+ACTION+'\n')
 OUT.mkdir(parents=True,exist_ok=False);(OUT/'cache').mkdir();(OUT/'library').mkdir();(OUT/'download-receipts').mkdir();record=dict(schema_version=1,status='SETUP_PENDING',user_analysis_admitted=False,recorded_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),rscript=dict(path=str(RSCRIPT),**pin(RSCRIPT)))
 try:
  query=OUT/'base-info.R';query.write_text('args<-commandArgs(TRUE);x<-installed.packages(lib.loc=.Library);write.table(x[,c("Package","Version"),drop=FALSE],file=args[1],sep="\\t",quote=FALSE,row.names=FALSE);writeLines(c(R.version.string,R.home()),args[2])\n',encoding='utf-8',newline='\n');run('base-info',[query,OUT/'base-packages.tsv',OUT/'r-version.txt']);version=(OUT/'r-version.txt').read_text();assert version.startswith('R version 4.6.1 ');base={row['Package']:row['Version'] for row in csv.DictReader((OUT/'base-packages.tsv').open(),delimiter='\t')}
  index=dcf(gzip.decompress((SOURCE/'windows-4.6-PACKAGES.gz').read_bytes()).decode());assert index['mashr']['Version']=='0.2.79';pending=['mashr'];selected={};used_base={}
  while pending:
   name=pending.pop()
   if name=='R' or name in selected or name in used_base:continue
   if name in base:used_base[name]=base[name];continue
   if name not in index:raise Unavailable('Required dependency has no official binary in the pinned4.6 index: '+name)
   metadata=index[name];selected[name]=metadata
   for field in ['Depends','Imports','LinkingTo']:
    for dependency in metadata.get(field,'').split(','):
     if dependency.strip():pending.append(dependency.split('(',1)[0].strip())
  dump(OUT/'selected-package-index.json',dict(packages=selected,base_dependencies=used_base,index=pin(SOURCE/'windows-4.6-PACKAGES.gz')))
  with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:packages=list(pool.map(fetch,sorted(selected.items())))
  dump(OUT/'downloaded-packages.json',packages);(OUT/'package-paths.txt').write_text(''.join(str(OUT/'cache'/p['file'])+'\n' for p in packages),encoding='utf-8',newline='\n');(OUT/'expected-versions.tsv').write_text('Package\tVersion\n'+''.join(p['package']+'\t'+p['version']+'\n' for p in packages),encoding='utf-8',newline='\n')
  install=OUT/'install.R';install.write_text('''options(warn=2)
args<-commandArgs(TRUE);lib<-args[1];.libPaths(c(lib,.Library),include.site=FALSE)
zips<-readLines(args[2],warn=FALSE);install.packages(zips,lib=lib,repos=NULL,type="win.binary")
expected<-read.delim(args[3],stringsAsFactors=FALSE);actual<-installed.packages(lib.loc=lib)
stopifnot(all(expected$Package %in% rownames(actual)))
stopifnot(all(actual[expected$Package,"Version"]==expected$Version))
library(mashr);stopifnot(as.character(packageVersion("mashr"))=="0.2.79")
write.table(actual[,c("Package","Version","Built"),drop=FALSE],file=args[4],sep="\\t",quote=FALSE,row.names=FALSE)
writeLines(capture.output(sessionInfo()),args[5])
''',encoding='utf-8',newline='\n');run('install',[install,OUT/'library',OUT/'package-paths.txt',OUT/'expected-versions.tsv',OUT/'installed-packages.tsv',OUT/'session-info.txt'])
  health=OUT/'health.R';health.write_text('''options(warn=2);args<-commandArgs(TRUE);.libPaths(c(args[1],.Library),include.site=FALSE);library(mashr)
B<-matrix(c(1,-1,2,0),nrow=2);data<-mash_set_data(B,1);g<-list(pi=c(.2,.8),Ulist=list(shared=matrix(1,2,2)),grid=1,usepointmass=TRUE)
fit<-mash(data,g=g,fixg=TRUE,verbose=FALSE,outputlevel=3)
mu<-rowSums(B)/3;bf<-exp(rowSums(B)^2/6)/sqrt(3);w<-.8*bf/(.2+.8*bf)
expected<-cbind(w*mu,w*mu);expected_sd<-sqrt(w*(1/3+mu^2)-(w*mu)^2)
expected_lfsr<-1-w+w*pnorm(-abs(mu)/sqrt(1/3))
stopifnot(max(abs(get_pm(fit)-expected))<1e-10,max(abs(get_psd(fit)-cbind(expected_sd,expected_sd)))<1e-10,max(abs(get_lfsr(fit)-cbind(expected_lfsr,expected_lfsr)))<1e-10)
write.table(cbind(get_pm(fit),get_psd(fit),get_lfsr(fit)),file=args[2],sep="\\t",quote=FALSE,row.names=FALSE)
''',encoding='utf-8',newline='\n');run('health',[health,OUT/'library',OUT/'health.tsv'])
  record.update(status='COMPLETED_PRIVATE_REFERENCE_SETUP_ONLY',package_version='0.2.79',packages=packages,base_dependencies=used_base,health='two-feature/two-condition analytic Gaussian-mixture means,SDs and LFSRs passed',source_to_binary_reproduction_verified=False,required_action=ACTION);dump(OUT/'COMPLETED.json',record);print(json.dumps({k:v for k,v in record.items() if k!='packages'}))
 except Exception as error:
  code=3 if isinstance(error,Unavailable) else 2;record.update(status='SKIP3_DEPENDENCY_UNAVAILABLE' if code==3 else 'FAIL2_NO_REFERENCE_ADMITTED',error=str(error),required_action=ACTION);dump(OUT/'FAILURE.json',record);(OUT/'WARNINGS.md').write_text('# mashr '+record['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n');print(json.dumps(record),file=sys.stderr);raise SystemExit(code)
if __name__=='__main__':main()
