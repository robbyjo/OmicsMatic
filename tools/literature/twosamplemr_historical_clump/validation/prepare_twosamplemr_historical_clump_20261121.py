from pathlib import Path
import hashlib,json,re,shutil
R=Path('D:/projects/JLinAlg');T=R/'build/mr-7';B=T/'build/twosamplemr-historical-clump-1';B.mkdir(parents=True,exist_ok=False);D=T/'src/main/resources/org/jlinalg/omics/twosamplemr-historical-clump';D.mkdir(exist_ok=False);OLD=T/'src/main/resources/org/jlinalg/omics/twosamplemr-clump'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
profile='TwoSampleMR0.6.8-ieugwasr1.2.1-PLINK1.9-20260927-R4.6.1-Windows64';commit='806703f0693cdd1c01cbde1d8ec51b3ab0ae9d14'
for name in ['Cli','Contracts','Files','Runtime','Audit']:
    src=T/f'src/main/java/org/jlinalg/cli/TwoSampleMrClump{name}.java';text=src.read_text(encoding='utf-8').replace('TwoSampleMrClump','TwoSampleMrHistoricalClump').replace('TwoSampleMrWorkflowFiles','TwoSampleMrHistoricalFiles').replace('twosamplemr-clump','twosamplemr-historical-clump').replace('TwoSampleMR0.7.14','TwoSampleMR0.6.8').replace('c08ab8a6','806703f0').replace('c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6',commit)
    if name=='Cli':
        text=text.replace('original-twosamplemr-local-clumping','original-twosamplemr-0.6.8-local-clumping').replace('Historical editions/global registration/catalog admission remain open.','The tagged0.6.8 source is bound by all149 function hashes, not an installed RemoteSha or proven paper execution commit. Dependencies are declared present-day versions; historical dependency-era reproduction, older API clumping, global registration and catalog admission remain open.')
    if name=='Audit':
        text=text.replace('0,152,152)==152','0,149,149)==149').replace('"0.7.14"','"0.6.8"').replace('806703f07b457d8ab87dcb7f6d33d8c8452e4cc6',commit)
    if name=='Runtime':text=text.replace('        TwoSampleMrWorkflowRuntime.sources(root,compare);\n','')
    dst=T/f'src/main/java/org/jlinalg/cli/TwoSampleMrHistoricalClump{name}.java';dst.write_text(text,encoding='utf-8',newline='\n')
meta=json.loads((OLD/'SOURCE-PROFILE.json').read_bytes());meta.update(profile=profile,TwoSampleMR_version='0.6.8',TwoSampleMR_commit=commit,TwoSampleMR_source_functions_guarded=149,dependency_era_reproduced=False,paper_execution_commit_established=False)
for name in meta['files']:shutil.copyfile(OLD/name,D/name)
for name,src in [('TWOSAMPLEMR-0.6.8-PROFILE.json',T/'src/main/resources/org/jlinalg/omics/twosamplemr-historical/0.6.8/PROFILE.json'),('TWOSAMPLEMR-LICENSE',T/'src/main/resources/org/jlinalg/omics/twosamplemr-historical/0.6.8/LICENSE'),('TWOSAMPLEMR-ld.R',R/'build/twosamplemr-historical-intake-20261121-1/0.6.8/source/R/ld.R')]:
    raw=src.read_bytes();(D/name).write_bytes(raw);meta['files'][name]=pin(raw)
save(D/'SOURCE-PROFILE.json',meta)
worker=(OLD/'worker.R').read_text(encoding='utf-8')
start=worker.index('source.guard <- function()');end=worker.index('.omics_ld_calls <-',start)
guard='''reference <- NULL
fingerprint <- function(fun)digest::digest(serialize(list(formals=formals(fun),body=body(fun)),NULL,version=3),serialize=FALSE,algo='sha256')
source.guard <- function() {
    reference<<-jsonlite::fromJSON('../source/clump-reference/TWOSAMPLEMR-0.6.8-PROFILE.json',simplifyVector=FALSE)
    if(reference$version!='0.6.8'||reference$commit!='806703f0693cdd1c01cbde1d8ec51b3ab0ae9d14'||length(reference$guards)!=149L)stop('Different original historical source reference')
    for(name in names(reference$guards)) {
        if(!exists(name,ns,inherits=FALSE)||!identical(fingerprint(get(name,ns,inherits=FALSE)),reference$guards[[name]]$sha256))stop(paste('Original installed body/formals differ from tagged source:',name))
        guards[[name]]<<-reference$guards[[name]]$sha256
    }
}
guard.after <- function()for(name in names(guards))if(!identical(fingerprint(get(name,ns,inherits=FALSE)),guards[[name]]))stop(paste('Original namespace changed during analysis:',name))

'''
worker=worker[:start]+guard+worker[end:]
old="    if(desc$Version!='0.7.14'||is.null(desc$RemoteSha)||desc$RemoteSha!='c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6')unavailable('Supply exact TwoSampleMR0.7.14 c08ab8a6')"
assert old in worker;worker=worker.replace(old,"    if(desc$Version!='0.6.8')unavailable('Supply exact tagged TwoSampleMR0.6.8 806703f0; adjacent editions are not substituted')\n    if(!requireNamespace('digest',quietly=TRUE))unavailable('digest is required for exact original tagged function body/formal hashes')")
worker=worker.replace('TwoSampleMR_commit=desc$RemoteSha','TwoSampleMR_commit=reference$commit,TwoSampleMR_commit_binding=\'Tagged source archive plus all149 original function hashes; not installed RemoteSha or proven paper execution commit\',dependency_era_reproduced=FALSE')
assert '0.7.14' not in worker and 'source/reference/source.zip' not in worker;(D/'worker.R').write_text(worker,encoding='utf-8',newline='\n')
C=T/'tools/literature/twosamplemr_historical_clump';C.mkdir(exist_ok=False)
comparison=(T/'tools/literature/twosamplemr_clump/compare_original.R').read_text(encoding='utf-8');old="stopifnot(two$Version=='0.7.14',two$RemoteSha=='c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6',ld$Version=='1.2.1',ld$RemoteSha=='ce5d0c07c40f1f9cdafff40bb6a644a3913c9ad6')";assert old in comparison
new="""stopifnot(two$Version=='0.6.8',ld$Version=='1.2.1',ld$RemoteSha=='ce5d0c07c40f1f9cdafff40bb6a644a3913c9ad6',requireNamespace('digest',quietly=TRUE))
reference<-jsonlite::fromJSON(file.path(root,'source/clump-reference/TWOSAMPLEMR-0.6.8-PROFILE.json'),simplifyVector=FALSE)
ns<-asNamespace('TwoSampleMR');ld.ns<-asNamespace('ieugwasr')
fingerprint<-function(fun)digest::digest(serialize(list(formals=formals(fun),body=body(fun)),NULL,version=3),serialize=FALSE,algo='sha256')
guard<-function() {
    stopifnot(length(reference$guards)==149L,reference$commit=='806703f0693cdd1c01cbde1d8ec51b3ab0ae9d14')
    for(name in names(reference$guards))stopifnot(identical(fingerprint(get(name,ns,inherits=FALSE)),reference$guards[[name]]$sha256))
    code<-parse(file.path(root,'source/clump-reference/ieugwasr-ld_clump.R'),keep.source=FALSE)
    count<-0L
    for(e in code)if(is.call(e)&&length(e)==3L&&identical(e[[1]],as.name('<-'))&&is.symbol(e[[2]])&&is.call(e[[3]])&&identical(e[[3]][[1]],as.name('function'))) {
        name<-as.character(e[[2]]);actual<-get(name,ld.ns,inherits=FALSE);expected<-eval(e[[3]],envir=ld.ns);stopifnot(identical(formals(actual),formals(expected)),identical(body(actual),body(expected)));count<-count+1L
    }
    stopifnot(count==7L)
}
guard()
"""
comparison=comparison.replace(old,new).replace("actual<-capture(get('clump_data',asNamespace('TwoSampleMR')),input);saveRDS","actual<-capture(get('clump_data',asNamespace('TwoSampleMR')),input);guard();saveRDS").replace("actual<-capture(get(call$label,asNamespace(call$package),inherits=FALSE),input)","actual<-capture(get(call$label,asNamespace(call$package),inherits=FALSE),input);guard()")
comparison=comparison.replace('whole_paper_reproduced=FALSE','whole_paper_reproduced=FALSE,TwoSampleMR_source_functions_guarded=149L,ieugwasr_source_functions_guarded=7L,dependency_era_reproduced=FALSE');(C/'compare_original.R').write_text(comparison,encoding='utf-8',newline='\n')
files=[*T.glob('src/main/java/org/jlinalg/cli/TwoSampleMrHistoricalClump*.java'),*D.iterdir(),C/'compare_original.R'];save(B/'GENERATION.json',dict(source_profile=meta,new_files={p.relative_to(T).as_posix():pin(p.read_bytes()) for p in files if p.is_file()},predecessor_sources_changed=False))
assert all('0.7.14' not in p.read_text(encoding='utf-8') and '806703f07b457' not in p.read_text(encoding='utf-8') for p in T.glob('src/main/java/org/jlinalg/cli/TwoSampleMrHistoricalClump*.java'))
print('Created5 separate historical-clumping classes,149-function source guard, provider observer and independent comparator',flush=True)
