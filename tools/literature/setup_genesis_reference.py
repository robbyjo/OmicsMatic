# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install permitted official GENESIS binaries and pure-data/R prerequisites into a NEW private Windows library; no analysis."""
import argparse
import hashlib
import json
import os
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/genesis-runtime'
ACTION=('No GENESIS analysis is admitted by setup. If exact dependencies cannot be installed or installation is forbidden, '
    'SKIP dependent PC-AiR/PC-Relate/model analyses and retain the named cause. Resolve only the named compatible prerequisites '
    'where permitted, then retry in a NEW private library. Compiled dependencies require official compatible Windows binaries; '
    'missing binaries are not replaced by an unrequested compiler/toolchain installation. Pure-R/data source prerequisites '
    'may be installed without a compiler. Do not alter shared R libraries or substitute ordinary PCA/SNPRelate for GENESIS. '
    'This explicit current release does not reproduce reported2.16.1/2.26.0 paper versions.')
PROGRAM=r'''
.libPaths(c(private_library,file.path(R.home(),"library")),include.site=FALSE)
stopifnot(getRversion()=="4.6.1")
repos <- c(BioCsoft="https://bioconductor.org/packages/3.23/bioc",BioCann="https://bioconductor.org/packages/3.23/data/annotation",
    BioCexp="https://bioconductor.org/packages/3.23/data/experiment",CRAN="https://cloud.r-project.org")
options(timeout=180)
cat("INDEX_DOWNLOAD_START\n")
binary <- utils::available.packages(repos=repos,type="win.binary")
source <- utils::available.packages(repos=repos,type="source")
stopifnot("GENESIS" %in% rownames(binary),binary["GENESIS","Version"]==version)
# Prefer a compatible official Windows binary; source-only prerequisites must
# explicitly require no compilation. Never request Suggests/Enhances packages.
fields <- intersect(colnames(binary),colnames(source))
available <- rbind(binary[,fields,drop=FALSE],source[setdiff(rownames(source),rownames(binary)),fields,drop=FALSE])
deps <- unique(tools::package_dependencies("GENESIS",db=available,which=c("Depends","Imports","LinkingTo"),recursive=TRUE)[[1L]])
base <- rownames(utils::installed.packages(lib.loc=file.path(R.home(),"library")))
required <- setdiff(deps,c(base,"R"))
stopifnot(all(required %in% rownames(available)))
bin_names <- intersect(required,rownames(binary));source_names <- setdiff(required,bin_names)
stopifnot(all(source[source_names,"NeedsCompilation"]=="no"))
manifest <- data.frame(package=c(bin_names,source_names,"GENESIS"),type=c(rep("win.binary",length(bin_names)),rep("source",length(source_names)),"pinned-main-binary"),
    version=c(binary[bin_names,"Version"],source[source_names,"Version"],version),
    repository=c(binary[bin_names,"Repository"],source[source_names,"Repository"],"official-intake-pinned"),check.names=FALSE)
utils::write.table(manifest,file.path(out,"dependency-plan.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
stopifnot(all(grepl("^https://(bioconductor.org|cloud.r-project.org)/",manifest$repository[manifest$type!="pinned-main-binary"])))
cat("DEPENDENCY_PLAN",length(bin_names),"Windows binaries",length(source_names),"pure R/data sources\n")
if(length(bin_names)) {
    cat("BINARY_DEPENDENCY_DOWNLOAD_START\n")
    files <- utils::download.packages(bin_names,destdir=downloads,available=binary,repos=repos,type="win.binary")
    stopifnot(nrow(files)==length(bin_names))
    utils::install.packages(files[,2L],repos=NULL,type="win.binary",lib=private_library)
}
if(length(source_names)) {
    cat("PURE_SOURCE_DEPENDENCY_DOWNLOAD_START\n")
    files <- utils::download.packages(source_names,destdir=downloads,available=source,repos=repos,type="source")
    stopifnot(nrow(files)==length(source_names))
    utils::install.packages(files[,2L],repos=NULL,type="source",lib=private_library,INSTALL_opts="--no-multiarch")
}
cat("PINNED_GENESIS_BINARY_INSTALL_START\n")
utils::install.packages(main_archive,repos=NULL,type="win.binary",lib=private_library)
stopifnot(requireNamespace("GENESIS",quietly=TRUE),utils::packageDescription("GENESIS",fields="Version")==version,
    dirname(find.package("GENESIS"))==normalizePath(private_library,winslash="/"))
for(package in required)if(!package %in% base)stopifnot(requireNamespace(package,quietly=TRUE),dirname(find.package(package))==normalizePath(private_library,winslash="/"))
installed <- utils::installed.packages(lib.loc=private_library,fields=c("License","NeedsCompilation"))
utils::write.table(installed[,intersect(c("Package","Version","License","NeedsCompilation","Built","LibPath"),colnames(installed)),drop=FALSE],
    file.path(out,"installed-packages.tsv"),sep="\t",quote=FALSE,row.names=FALSE)
for(name in c("pcair","pcairPartition","pcrelate","fitNullModel","assocTestSingle"))stopifnot(is.function(getExportedValue("GENESIS",name)))
cat("GENESIS_VERSION",as.character(utils::packageVersion("GENESIS")),"R_VERSION",R.version.string,"\n")
cat("PRIVATE_GENESIS_SETUP_ONLY_NO_ANALYSIS\n")
'''


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--intake',type=Path,default=BASE/'official-intake-1');p.add_argument('--allow-download',action='store_true');p.add_argument('--allow-install',action='store_true');a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='SKIP3_PRIVATE_GENESIS_RUNTIME_UNAVAILABLE',analysis_performed=False,new_catalog_credit=False,whole_goal_complete=False,required_action=ACTION);code=3
    try:
        assert a.allow_download and a.allow_install,'Require explicit permitted download/install flags; skip dependent analyses if permission is absent'
        intake=a.intake.resolve();metadata=json.loads((intake/'COMPLETED.json').read_bytes())
        assert metadata['status']=='PASS_OFFICIAL_GENESIS_SOURCE_AND_WINDOWS_BINARY_INTAKE_ONLY' and metadata['license']=='GPL-3'
        version=metadata['version'];archive=intake/('GENESIS_'+version+'.zip')
        recorded=next(r for r in metadata['receipts'] if r['url'].endswith(archive.name));assert pin(archive)=={k:recorded[k] for k in ['bytes','sha256']}
        rscript=Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe');assert rscript.is_file(),'Require named compatible original Windows R4.6.1'
        runtime_root=rscript.parent.parent
        protected={str(f):pin(f) for f in runtime_root.rglob('*') if f.is_file()}
        library=out/'library';library.mkdir();downloads=out/'downloads';downloads.mkdir();temp=out/'tmp';temp.mkdir()
        def r_literal(value):return json.dumps(str(value).replace('\\','/'))
        values=dict(private_library=library,downloads=downloads,out=out,main_archive=archive,version=version)
        script=out/'install-originals.R';script.write_text('\n'.join(k+' <- '+r_literal(v) for k,v in values.items())+'\n'+PROGRAM,encoding='utf8',newline='\n')
        environment=dict(os.environ,LC_ALL='C',LANG='C',R_LIBS_USER=str(library),R_LIBS_SITE='',R_DEFAULT_PACKAGES='base',TMPDIR=str(temp))
        command=[str(rscript),'--vanilla',str(script)]
        print('Installing only into',str(library),'; original software analysis remains unadmitted',flush=True)
        with (out/'install.stdout').open('xb') as so,(out/'install.stderr').open('xb') as se:
            result=subprocess.run(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=2400)
        save(out/'install.process.json',dict(command=command,exit_code=result.returncode))
        assert result.returncode==0,('Original private GENESIS prerequisite installation failed; read retained install.stdout/install.stderr',result.returncode)
        assert b'PRIVATE_GENESIS_SETUP_ONLY_NO_ANALYSIS' in (out/'install.stdout').read_bytes()
        assert all(pin(Path(name))==value for name,value in protected.items()),'Shared R distribution changed; admit no dependent analysis'
        state.update(status='PASS_PRIVATE_ORIGINAL_GENESIS_RUNTIME_SETUP_ONLY',version=version,private_library=str(library),rscript=dict(path=str(rscript),**pin(rscript)),
            installed_files={f.relative_to(library).as_posix():pin(f) for f in sorted(library.rglob('*')) if f.is_file()},
            downloaded_archives={f.name:pin(f) for f in sorted(downloads.iterdir()) if f.is_file()},source_intake=pin(intake/'COMPLETED.json'),producer=pin(Path(__file__)),
            shared_R_distribution_preserved_files=len(protected),installation_performed=True)
        code=0
    except Exception as error:
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('cause','')[:2800],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact official-main/private-library checks')
    raise SystemExit(main())
