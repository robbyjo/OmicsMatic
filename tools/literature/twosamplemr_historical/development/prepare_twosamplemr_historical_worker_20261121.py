from pathlib import Path
import hashlib,json
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';I=R/'build/twosamplemr-historical-intake-20261121-1';D=T/'src/main/resources/org/jlinalg/omics/twosamplemr-historical';assert not D.exists();D.mkdir();profiles={}
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
for version in ['0.4.9','0.5.6','0.5.7','0.6.8']:
    source=json.loads((I/version/'RESULT.json').read_bytes());reference=json.loads((B/'namespace-profiles-1'/version/'PROFILE.json').read_bytes());assert reference['status']=='PASS_ALL_SOURCE_FUNCTION_BODIES_FORMALS_EXACT';directory=D/version;directory.mkdir();profile='TwoSampleMR'+version+'-'+source['commit'][:8]+'-R4.6.1-Windows64'
    value=dict(profile=profile,version=version,commit=source['commit'],tag=source['tag'],tag_aliases=source.get('tag_aliases',[source['tag']]),source_archive=source['archive'],source_files=source['files'],source_functions=reference['source_functions'],guards=reference['guards'],formals=reference['formals'],methods=reference['methods'],default_parameters=reference['default_parameters'],package_build_variant=reference['package_build_variant'],source_code_redistributed=False,source_and_license_location='https://github.com/MRCIEU/TwoSampleMR/tree/'+source['commit'],paper_execution_commit_established=False,dependency_era_reproduced=False,original_R_host='WindowsR4.6.1',original_local_clump_supported=version=='0.6.8',api_data_upload_qualified=False,new_catalog_credit=0,whole_goal_complete=False)
    (directory/'PROFILE.json').write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n');(directory/'METHODS.tsv').write_bytes((B/'namespace-profiles-1'/version/'METHODS.tsv').read_bytes())
    if version=='0.4.9':license_path=Path('C:/Program Files/R/R-4.6.1/share/licenses/Artistic-2.0')
    else:license_path=I/version/'source/LICENSE'
    assert license_path.is_file();(directory/('Artistic-2.0.txt' if version=='0.4.9' else 'LICENSE')).write_bytes(license_path.read_bytes())
    profiles[profile]=dict(version=version,commit=source['commit'],files={p.name:pin(p.read_bytes()) for p in directory.iterdir()},source_functions=reference['source_functions'])
(D/'PROFILES.json').write_text(json.dumps(profiles,indent=2)+'\n',encoding='utf-8',newline='\n')
s=(T/'src/main/resources/org/jlinalg/omics/twosamplemr_workflow.R').read_text();start=s.index('ns <- NULL');end=s.index('main <- function()',start)
s=s[:start]+r'''ns <- NULL;reference<-NULL
fingerprint <- function(fun)digest::digest(serialize(list(formals=formals(fun),body=body(fun)),NULL,version=3),serialize=FALSE,algo='sha256')
source.guard <- function() {
    if(length(reference$guards)!=reference$source_functions)stop('Different original function population in packaged reference')
    for(name in names(reference$guards)) {
        if(!exists(name,ns,inherits=FALSE)||!identical(fingerprint(get(name,ns,inherits=FALSE)),reference$guards[[name]]$sha256))stop(paste('Original installed body/formals differ from tagged source:',name))
        guards[[name]]<<-reference$guards[[name]]$sha256
    }
}
guard.after <- function()for(name in names(guards))if(!identical(fingerprint(get(name,ns,inherits=FALSE)),guards[[name]]))stop(paste('Original namespace changed during analysis:',name))
'''+s[end:]
s=s.replace("if(as.character(getRversion())!='4.6.1')unavailable(paste('Exact R4.6.1 profile required; found',getRversion()))", "if(as.character(getRversion())!='4.6.1'||.Platform$OS.type!='windows'||.Machine$sizeof.pointer!=8L)unavailable('This source-edition profile requires 64-bit Windows R4.6.1; qualify other hosts separately')")
s=s.replace("json.ready<<-TRUE;r<-jsonlite::fromJSON('../effective-request.json',simplifyVector=FALSE)","json.ready<<-TRUE;r<-jsonlite::fromJSON('../effective-request.json',simplifyVector=FALSE);reference<<-jsonlite::fromJSON('../source/historical-profile.json',simplifyVector=FALSE)\n    if(!identical(r$profile,reference$profile))stop('Request and original tagged-source profile differ')\n    if(!requireNamespace('digest',quietly=TRUE))unavailable('digest is required for exact original AST guards')")
start=s.index("    if(d$Version!='0.7.14'");end=s.index("    ns<<-",start);s=s[:start]+"    if(d$Version!=reference$version)unavailable('The declared exact historical TwoSampleMR edition is not supplied; adjacent editions are not substituted')\n"+s[end:]
s=s.replace("commit=d$RemoteSha,source_functions_guarded=length(guards),namespace_rewritten=FALSE", "commit=reference$commit,commit_binding='Tagged source archive plus all source function body/formal hashes; no installed RemoteSha or paper execution commit is inferred',source_functions_guarded=length(guards),namespace_rewritten=FALSE,package_build_variant=reference$package_build_variant,dependency_era_reproduced=FALSE")
needle="    op<-r$operation\n";assert needle in s
s=s.replace(needle,needle+r'''    if(op=='FORMAT') {
        dat<-read.delim('../inputs/data',check.names=FALSE,quote='',comment.char='',colClasses='character',na.strings='NA',encoding='UTF-8')
        args<-c(list(dat=dat),r$format_options);value<-invoke('format_data',ns$format_data,args);table.out(value,'formatted')
        report$preparation_complete<<-TRUE;report$no_statistical_estimate_produced<<-TRUE;report$original_population<<-list(input_rows=nrow(dat),formatted_rows=nrow(value));return(invisible(NULL))
    }
''')
s=s.replace("        methods<-unlist(r$methods);parameters<-r$parameters\n        method.list<-ns$mr_method_list();table.out(method.list,'method-list')", "        methods<-unlist(r$methods);parameters<-invoke('default_parameters',ns$default_parameters,list())\n        for(name in names(r$parameters)){if(!name%in%names(parameters))stop(paste('Parameter is absent from original defaults:',name));parameters[name]<-r$parameters[name]}\n        json(parameters,'effective-parameters.json')\n        method.list<-invoke('mr_method_list',ns$mr_method_list,list());table.out(method.list,'method-list')")
s=s.replace("warnings.seen<<-c(warnings.seen,'Original mr omits inapplicable/all-NA methods and skips multi-SNP Wald when multiple methods are selected. Read the saved method list, mr rows and population record; missing rows are not estimates. Source sensitivity function defaults are preserved in the guarded definitions and captured arguments.')", "warnings.seen<<-c(warnings.seen,'Original tagged mr may omit inapplicable/all-NA methods. Read the exact source method list, mr rows and population record; missing rows are not estimates. Source sensitivity and parameter defaults are preserved. Source edition qualification under declared current dependencies does not reproduce the paper runtime or dependency era.')")
(D/'worker.R').write_text(s,encoding='utf-8',newline='\n');print('Prepared four original-source profiles and historical worker')
