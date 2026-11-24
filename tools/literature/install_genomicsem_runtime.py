"""Explicit private Windows R4.6.1 original GenomicSEM installer; not a scientific analysis."""
from pathlib import Path
import argparse,json,platform,shutil,sys
from genomicsem.runtime import ACTION,Unavailable,artifact,extract_source,identity,new_output,pin,profile,require,run_r,source_inventory,write,HERE

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out',required=True,type=Path,help='NEW private runtime; existing physical parent required')
    p.add_argument('--rscript',required=True,type=Path,help='Exact R4.6.1 x86_64 Windows Rscript.exe')
    p.add_argument('--cache',type=Path,help='Directory with the 52 pinned Windows package ZIP files; read only')
    p.add_argument('--source-archive',type=Path)
    p.add_argument('--lavaan-archive',type=Path)
    p.add_argument('--offline',action='store_true',help='Skip unavailable artifacts rather than download')
    a=p.parse_args(argv);root=None
    try:
        pr=profile()
        if platform.system()!='Windows' or platform.machine().lower() not in ('amd64','x86_64'):raise Unavailable('This qualified runtime recipe requires Windows x86_64; Linux qualification remains separate')
        root=new_output(a.out);(root/'WARNINGS.md').write_text('# PRIVATE GENOMIC SEM RUNTIME: NOT A JAVA ADAPTER OR PAPER ANALYSIS\n\n'+ACTION+'\n',encoding='utf-8')
        r=a.rscript.absolute()
        if not r.is_file():raise Unavailable('Missing permitted Rscript.exe: '+str(r))
        code=run_r(root,r,['-e','cat(paste(R.version$major,R.version$minor,sep="."),R.version$platform,packageDescription("Matrix",fields="Version"),sep="\\n")'],[],'probe-R')
        if code:raise Unavailable('R runtime probe failed; read probe-R.stderr')
        version=(root/'probe-R.stdout').read_text(encoding='utf-8').splitlines()
        if version!=[pr['R']['version'],pr['R']['platform'],pr['R']['Matrix']]:raise Unavailable('Require exact R4.6.1 Windows x86_64 / Matrix1.7-5; observed '+repr(version))
        downloads=root/'downloads';downloads.mkdir()
        src=artifact(pr['upstream']['url'],pr['upstream']['archive'],downloads/'GenomicSEM.tar.gz',a.source_archive,a.offline)
        lavaan=artifact(pr['lavaan']['url'],pr['lavaan']['archive'],downloads/'lavaan.tar.gz',a.lavaan_archive,a.offline)
        extract_source(src,root/'source',pr['upstream']['source_files']);shutil.copyfile(lavaan,root/'lavaan.tar.gz')
        packages=[]
        for row in pr['dependencies']:
            packages.append(artifact(row['url'],identity(row),downloads/row['filename'],a.cache/row['filename'] if a.cache else None,a.offline))
        # Include source files and installed dependency recipes, not credentials.
        write(root/'PROFILE.json',pr)
        with (root/'package-archives.txt').open('x',encoding='utf-8',newline='\n') as f:f.write('\n'.join(str(q).replace('\\','/') for q in packages)+'\n')
        shutil.copyfile(HERE/'install.R',root/'install.R')
        if run_r(root,r,[root/'install.R',root,root/'package-archives.txt'],[],'install'):raise Unavailable('Private installation failed or was forbidden; read install.stderr and preserve this attempt')
        require(source_inventory(root/'source')==pr['upstream']['source_files'],'Original source changed during installation')
        files=source_inventory(root/'library');require(files,'Empty installed library')
        value=dict(schema_version=1,status='STAGED_NOT_SCIENTIFICALLY_QUALIFIED',profile=pr['profile'],rscript=str(r),rscript_identity=pin(r),source_identity=pr['upstream']['archive'],library_files=files,profile_identity=pin(root/'PROFILE.json'),catalog_admitted=False,new_family_credit=0,new_review_credit=0,whole_goal_complete=False)
        write(root/'INSTALLATION.json',value)
        print(json.dumps(dict(status=value['status'],profile=pr['profile'],installed_library_files=len(files),required_action='Run tools/literature/qualify_genomicsem_runtime.py into a NEW output; Java adapter and catalog admission remain outstanding.')));return 0
    except (Unavailable,FileNotFoundError) as e:code=3;message='SKIP3: DEPENDENT INSTALLATION SKIPPED: '+str(e)
    except Exception as e:code=2;message='FAIL2: NO QUALIFIED RUNTIME: '+str(e)
    if root is not None:write(root/'FAILURE.json',dict(status='SKIP3' if code==3 else 'FAIL2',error=message,required_action=ACTION,results_admitted=False))
    print(message+'\n'+ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
