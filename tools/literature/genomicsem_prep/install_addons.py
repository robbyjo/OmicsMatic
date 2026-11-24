"""Explicitly stage optional pinned Rmpfr/GMP binaries in NEW private output.

The original base runtime is read only. This runs selected dependency arithmetic,
not a paper analysis, full package qualification or catalog transition.
"""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,shutil,sys,zipfile
from genomicsem.runtime import Unavailable,artifact,identity,new_output,pin,require,run_r,source_inventory,write
from qualify_genomicsem_runtime import inspect as inspect_base

HERE=Path(__file__).resolve().parent
PROFILE_SHA='5341c838652dc122ae5d3b0e8d4342cea2d9a5cd165778d78d094d43157bfcfa'
ACTION=('SKIP3: dependent setup/analysis skipped. Obtain the exact named permitted prerequisite using an allowed installation method; if installation is forbidden, retain the skip. '
        'FAIL2: no qualified add-on library; preserve partial output/logs, resolve the stated cause and retry NEW output. '
        'The pinned CRAN binaries report Built R4.6.2. Their selected mpfr/log/sqrt/as.numeric operations are checked under R4.6.1; build warnings remain explicit, and R is never silently upgraded. '
        'Analysis commands never install or download. Raw runtime bindings and process logs are private.')

def profile():
    raw=(HERE/'ADDONS.json').read_bytes();require(hashlib.sha256(raw).hexdigest()==PROFILE_SHA,'Changed reviewed optional dependency profile')
    p=json.loads(raw);require([(x['package'],x['version'])for x in p['packages']]==[('gmp','0.7-5.1'),('Rmpfr','1.1-3')],'Changed optional dependency population');return raw,p

PROBE=r'''options(warn=1,digits=17)
a<-commandArgs(TRUE);stopifnot(length(a)==4L);.libPaths(c(a[1],a[2],.Library),include.site=FALSE)
stopifnot(as.character(getRversion())=='4.6.1',R.version$platform=='x86_64-w64-mingw32')
warnings<-list();withCallingHandlers(library(Rmpfr),warning=function(w){warnings[[length(warnings)+1L]]<<-conditionMessage(w)})
stopifnot(as.character(packageVersion('gmp'))=='0.7.5.1',as.character(packageVersion('Rmpfr'))=='1.1.3')
ps<-c('1e-1000','5e-5000','1e-310');zs<-as.numeric(sqrt(-2*log(mpfr(ps,precBits=2000))))
stopifnot(all(is.finite(zs)),all(zs>37),abs(zs[1]^2/(2000*log(10))-1)<1e-14)
jsonlite::write_json(list(status='PASS_SELECTED_ORIGINAL_MPFR_GMP_ARITHMETIC',R=as.character(getRversion()),gmp=as.character(packageVersion('gmp')),Rmpfr=as.character(packageVersion('Rmpfr')),input_P=as.list(ps),original_approximate_Z=as.list(zs),dependency_warnings=warnings,full_package_qualification=FALSE,catalog_admitted=FALSE),a[3],auto_unbox=TRUE,pretty=TRUE,digits=17)
'''

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--cache',type=Path);parser.add_argument('--offline',action='store_true');a=parser.parse_args(argv);out=None
    try:
        raw,pr=profile();base,base_profile,saved=inspect_base(a.runtime);target=a.out.absolute();require(not target.is_relative_to(base)and not base.is_relative_to(target),'Add-on output must be disjoint from the original runtime');before=source_inventory(base/'library');out=new_output(target)
        (out/'PROFILE.json').write_bytes(raw);(out/'WARNINGS.md').write_text('# OPTIONAL ORIGINAL DEPENDENCIES: REVIEW REQUIRED\n\n'+ACTION+'\n',encoding='utf-8');lib=out/'library';lib.mkdir();cache=out/'download-cache';cache.mkdir();all_files={}
        for package in pr['packages']:
            filename=package['url'].rsplit('/',1)[-1];supplied=(a.cache/filename)if a.cache is not None else None
            archive=artifact(package['url'],package['archive'],cache/filename,supplied=supplied,offline=a.offline)
            if archive.parent!=cache:shutil.copyfile(archive,cache/filename)
            with zipfile.ZipFile(archive)as z:
                seen=set()
                for member in z.infolist():
                    path=PurePosixPath(member.filename);require(path.parts and not path.is_absolute()and path.parts[0]==package['package']and '..'not in path.parts and '\\'not in member.filename and ':'not in member.filename,'Unsafe package member')
                    if member.is_dir():continue
                    require(member.filename in package['files']and member.filename not in seen and member.file_size==package['files'][member.filename]['bytes'],'Unexpected/duplicate/changed package member');data=z.read(member);require(dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())==package['files'][member.filename],'Changed upstream binary-package payload')
                    destination=lib/Path(*path.parts);destination.parent.mkdir(parents=True,exist_ok=True)
                    with destination.open('xb')as f:f.write(data)
                    seen.add(member.filename);all_files[member.filename]=package['files'][member.filename]
                require(seen==set(package['files']),'Incomplete original binary package')
        require(source_inventory(lib)==all_files,'Changed private dependency file closure');(out/'probe.R').write_text(PROBE,encoding='utf-8',newline='\n');result=out/'probe-result.json'
        # run_r records the foreground setup process; it does not install or
        # change the original package library. This is a tiny synthetic check.
        code=run_r(out,Path(saved['rscript']),[out/'probe.R',lib,base/'library',result,out],[lib,base/'library'],'probe')
        require(code==0 and result.is_file(),'Optional dependency arithmetic failed; read probe.stderr');v=json.loads(result.read_bytes());require(v['status']=='PASS_SELECTED_ORIGINAL_MPFR_GMP_ARITHMETIC','Missing actual arithmetic qualification')
        inspect_base(base);require(source_inventory(base/'library')==before and source_inventory(lib)==all_files,'Original/optional library changed during setup')
        write(out/'INSTALLATION.json',dict(status='PASS_PINNED_OPTIONAL_GENOMICSEM_PREPARATION_ADDONS',profile=pr['profile'],profile_identity=pin(out/'PROFILE.json'),library_files=all_files,rscript_identity=saved['rscript_identity'],base_profile=base_profile['profile'],arithmetic_result=pin(result),probe=pin(out/'probe.R'),dependency_warnings=v['dependency_warnings'],full_package_qualification=False,catalog_admitted=False,new_family_credit=0))
        print(json.dumps(dict(status='PASS_OPTIONAL_PREPARATION_ADDONS',files=len(all_files),base_library_files_unchanged=len(before),dependency_warnings=v['dependency_warnings'],catalog_admitted=False)));return 0
    except (Unavailable,FileNotFoundError)as e:code=3;message='SKIP3_DEPENDENT_ANALYSIS_SKIPPED: '+str(e)
    except Exception as e:code=2;message='FAIL2_NO_QUALIFIED_ADDONS: '+str(e)
    if out is not None:write(out/'FAILURE.json',dict(status='SKIP3'if code==3 else'FAIL2',message=message,required_action=ACTION,results_admitted=False))
    print(message+'\n'+ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
