"""Run declared synthetic GenomicSEM source/numerical checks on a staged private runtime."""
from pathlib import Path
import argparse,json,shutil,sys
from genomicsem.runtime import ACTION,HERE,Unavailable,identity,new_output,pin,profile,require,run_r,source_inventory,write

def inspect(runtime):
    root=Path(runtime).absolute();pr=profile()
    if not (root/'INSTALLATION.json').is_file():raise Unavailable('Missing completed private INSTALLATION.json')
    saved=json.loads((root/'INSTALLATION.json').read_bytes())
    require(saved['status']=='STAGED_NOT_SCIENTIFICALLY_QUALIFIED' and saved['profile']==pr['profile'],'Unknown staged runtime/profile')
    require(json.loads((root/'PROFILE.json').read_bytes())==pr and pin(root/'PROFILE.json')==saved['profile_identity'],'Staged profile differs')
    require(source_inventory(root/'source')==pr['upstream']['source_files'],'Original source closure differs')
    require(source_inventory(root/'library')==saved['library_files'],'Installed private library closure differs')
    rscript=Path(saved['rscript'])
    if not rscript.is_file():raise Unavailable('Restore the declared exact Rscript.exe: '+str(rscript))
    require(pin(rscript)==saved['rscript_identity'],'Rscript.exe identity differs')
    return root,pr,saved

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--runtime',required=True,type=Path)
    p.add_argument('--out',required=True,type=Path,help='NEW qualification output; original runtime remains read only')
    a=p.parse_args(argv);out=None
    try:
        runtime,pr,saved=inspect(a.runtime)
        absolute=Path(a.out).absolute();require(not absolute.is_relative_to(runtime) and not runtime.is_relative_to(absolute),'Qualification output and original runtime must be disjoint')
        out=new_output(absolute);write(out/'runtime-binding.json',dict(installation=pin(runtime/'INSTALLATION.json'),source=saved['source_identity'],profile=pr['profile'],library_files=len(saved['library_files']),rscript=saved['rscript_identity']))
        shutil.copyfile(HERE/'qualify.R',out/'qualify.R');shutil.copyfile(HERE/'PROFILE.json',out/'PROFILE.json')
        worker=out/'original';worker.mkdir()
        write(out/'request.json',dict(profile=str(out/'PROFILE.json'),source=str(runtime/'source'),out=str(worker),libraries=[str(runtime/'library')]))
        (out/'WARNINGS.md').write_text('# SYNTHETIC UPSTREAM QUALIFICATION ONLY\n\n'+ACTION+'\n\nOriginal p-value strings, fixed-parameter NA fields, zero P values, source warnings and failed attempts are retained. Independent checks cover selected numerical cells and metadata, not all possible models, convergence behavior, biological calibration, genome-wide throughput or historical paper settings. The R process runs in the foreground without automatic process termination.\n',encoding='utf-8')
        code=run_r(out,Path(saved['rscript']),[out/'qualify.R',out/'request.json'],[runtime/'library'],'qualification')
        inspect(runtime)
        require(pin(runtime/'INSTALLATION.json')==json.loads((out/'runtime-binding.json').read_bytes())['installation'],'Runtime binding changed during qualification')
        if code or not (worker/'RESULT.json').is_file():raise ValueError('Original runtime qualification failed; read qualification.stderr and original/RESULT.json when present')
        results=json.loads((worker/'RESULT.json').read_bytes())
        require(results['status']=='PASS_DECLARED_SYNTHETIC_UPSTREAM_CASES' and len(results['cases'])==13 and all(c['status']=='PASS' for c in results['cases'].values()),'Not all declared original-runtime cases passed')
        require(results['original_namespace_functions']==44 and results['numeric_cells_checked']>=150,'Insufficient original source/numerical comparisons')
        outputs=source_inventory(worker)
        write(out/'QUALIFIED.json',dict(status='PASS_SCOPED_ORIGINAL_RUNTIME_FOUNDATION',profile=pr['profile'],original_namespace_functions=44,cases=13,numeric_cells_checked=results['numeric_cells_checked'],outputs=outputs,worker=pin(out/'qualify.R'),runtime_binding=pin(out/'runtime-binding.json'),catalog_admitted=False,java_adapter_implemented=False,new_family_credit=0,new_review_credit=0,whole_goal_complete=False))
        print(json.dumps(dict(status='PASS_SCOPED_ORIGINAL_RUNTIME_FOUNDATION',cases=13,numeric_cells_checked=results['numeric_cells_checked'],original_namespace_functions=44,java_adapter_implemented=False,catalog_admitted=False,new_family_credit=0)));return 0
    except (Unavailable,FileNotFoundError) as e:code=3;message='SKIP3: DEPENDENT QUALIFICATION SKIPPED: '+str(e)
    except Exception as e:code=2;message='FAIL2: NO QUALIFIED RESULT: '+str(e)
    if out is not None:write(out/'FAILURE.json',dict(status='SKIP3' if code==3 else 'FAIL2',error=message,required_action=ACTION,results_admitted=False))
    print(message+'\n'+ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
