# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install the source-checked official Windows reference privately, or prominently skip when installation is forbidden."""
import argparse,csv,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,RSCRIPT,pin,save,environment


def inventory(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--intake',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);shared=RSCRIPT.parents[1]/'library';before=inventory(shared);save(out/'protected-library-before.json',before);action='Permit installation only when allowed and supply the exact official Windows2.26.0 archive and original R source, R4.6.1 and Matrix in a NEW private library. Otherwise skip dependent Rsubread analysis. Preserve all previous attempts and shared libraries.';state=dict(status='SKIP3_RSUBREAD_REFERENCE_UNAVAILABLE',required_action=action,analysis_admitted=False,implementation_credit=False,installation_authorized=a.allow_install,whole_goal_complete=False)
    try:
        if not a.allow_install:raise PermissionError('Installation forbidden by the explicit request; no package was installed and dependent analysis is skipped')
        if not RSCRIPT.is_file():raise FileNotFoundError('Required R4.6.1 executable unavailable')
        intake=a.intake.resolve();proof=json.loads((intake/'COMPLETED.json').read_bytes());assert proof['status']=='passed-official-Rsubread2260-source-and-Windows-binary-intake-only';record=proof['archives']['windows'];archive=ROOT/record['archive']['path'];assert pin(archive)=={k:record['archive'][k] for k in ['bytes','sha256']};source=intake/'source/unpacked/R/featureCounts.R';assert pin(source)==proof['archives']['source']['members']['R/featureCounts.R'];script=ROOT/'tools/literature/setup_rsubread_reference.R';command=[str(RSCRIPT),'--vanilla',str(script),str(out/'R-lib'),str(out),str(archive),str(source)]
        with (out/'installer.stdout').open('xb') as first,(out/'installer.stderr').open('xb') as second:run=subprocess.run(command,cwd=out,env=environment(),stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=180,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/'process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Read original installer.stdout/stderr; no reference or analysis admitted';status=(out/'R-status.tsv').read_text(encoding='utf8').splitlines();assert len(status)==2 and status[1].startswith('INSTALLED_ORIGINAL_RSUBREAD_REFERENCE_ONLY\t');assert inventory(shared)==before,'Protected R library changed; preserve the discrepancy and do not bind this runtime';private=inventory(out/'R-lib');assert private and 'Rsubread/libs/x64/Rsubread.dll' in private
        with (out/'loaded-dlls.tsv').open(encoding='utf8',newline='') as stream:dlls=list(csv.DictReader(stream,delimiter='\t'))
        dllpins={};builtins=[]
        for row in dlls:
            if row['name']=='base' and row['path']=='base':
                provider=RSCRIPT.parents[1]/'bin/x64/R.dll';assert provider.is_file();builtins.append(dict(name='base',reported_path='base',kind='built-in-base-namespace',provider=dict(path=str(provider),**pin(provider))))
            else:dllpins[row['path']]=pin(Path(row['path']))
        assert len(builtins)==1;state.update(status='passed-private-original-Rsubread2260-reference-installed-and-source-body-checked-only',private_library=dict(path=(out/'R-lib').relative_to(ROOT).as_posix(),files=private),loaded_dlls=dllpins,built_in_namespace_providers=builtins,protected_library_unchanged=True,rscript=dict(path=str(RSCRIPT),**pin(RSCRIPT)),reference_archive=record['archive'],original_function_body_equal=True,original_formals_equal=True,source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),script]},runtime_execution_platform='Windowsx86_64 R4.6.1; Linux R is currently unavailable and no Linux analysis is admitted')
    except (PermissionError,FileNotFoundError) as error:state['error']=str(error)
    except Exception as error:state.update(status='FAIL2_RSUBREAD_REFERENCE_SETUP',error=repr(error));(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        state['protected_library_unchanged']=inventory(shared)==before;save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Rsubread reference setup only\n\n'+state['status']+'\n\n'+state.get('error','No scientific analysis has run. This is an original-package installation/reference check only.')+'\n\nRequired action: '+action+'\n\nLinux R is currently unavailable; dependent Linux analysis remains skipped. Installation does not grant workflow, paper reproduction or catalog implementation credit.\n',encoding='utf8')
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/archive/private-library admission gates')
    raise SystemExit(main())
