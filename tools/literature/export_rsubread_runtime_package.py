# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export a previously validated original GPL Rsubread package and corresponding source into NEW private paths."""
import argparse,json,traceback,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--edition',choices=['2260','2232-R441'],required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True)
    action='Permit private software export only when allowed. Use the exported original package and documented exact clean Windows R edition, then bind NEW paths with RsubreadRuntimeCli. If export, R execution or required installation is unavailable/forbidden, skip dependent counting. Never refresh known hashes or substitute a runtime automatically.'
    state=dict(status='SKIP3_PRIVATE_RSUBREAD_RUNTIME_EXPORT_FORBIDDEN',runtime_exported=False,analysis_admitted=False,installation_authorized=a.allow_install,required_action=action,whole_goal_complete=False)
    try:
        if not a.allow_install:raise PermissionError('Private software export/install not explicitly permitted; no package files exported and dependent counting skipped')
        from rsubread_matrix_evidence import verify
        previous,files=verify(False);assert previous['native_matrix_component_available']
        reference=ROOT/'src/main/resources/org/jlinalg/omics/rsubread-workflow-reference-v2';index=json.loads((reference/'profiles.json').read_bytes());profilepath=reference/a.edition/'profile.json';assert pin(profilepath)==index['profiles'][a.edition]['files']['profile.json'];profile=json.loads(profilepath.read_bytes())
        base='build/literature-review/rsubread-runtime/';setup='reference-setup-3' if a.edition=='2260' else 'r441-reported-build-1';prefix=base+setup+'/R-lib/Rsubread/';sourceprefix=base+('reference-intake-1/source/unpacked/' if a.edition=='2260' else 'reported-source-2/source/')
        package={n[len(prefix):]:raw for n,raw in files.items() if n.startswith(prefix)};source={n[len(sourceprefix):]:raw for n,raw in files.items() if n.startswith(sourceprefix)}
        assert {n:byte_pin(raw) for n,raw in package.items()}==profile['known_package_files'] and len(source)>100 and 'R/featureCounts.R' in source and 'src/readSummary.c' in source
        for subdir,payload in [('R-lib/Rsubread',package),('corresponding-original-source',source)]:
            for name,raw in payload.items():
                rel=PurePosixPath(name);assert not rel.is_absolute() and str(rel)==name and '..' not in rel.parts and ':' not in name and '\\' not in name
                target=out/subdir/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
        with zipfile.ZipFile(ROOT/'docs/literature/rsubread-matrix-development/rsubread-count-matrix-api.jar') as z:(out/'COPYING.GPL-3.0.txt').write_bytes(z.read('META-INF/LICENSE'))
        save(out/'EXPECTED-R-HOME.json',dict(edition=a.edition,R_version=profile['R_version'],known_rscript=profile['known_rscript'],known_r_home_files=profile['known_r_home_files'],scope=profile['runtime_scope'],installation_performed=False))
        (out/'ORIGINAL-PROFILE.json').write_bytes(profilepath.read_bytes());assert {q.relative_to(out/'R-lib/Rsubread').as_posix():pin(q) for q in (out/'R-lib/Rsubread').rglob('*') if q.is_file()}==profile['known_package_files']
        state.update(status='passed-exported-previously-validated-original-GPL-Rsubread-private-runtime-and-corresponding-source',runtime_exported=True,edition=a.edition,package_version=profile['package_version'],R_version=profile['R_version'],private_library=str(out/'R-lib'),package_files=profile['known_package_files'],source_files={n:byte_pin(raw) for n,raw in source.items()},source_changed=False,source_built=False,package_manager_executed=False,R_executed=False,shared_library_changed=False,paper_binary_identified=False,license='GPL (>=3)',source_pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),profilepath,reference/'profiles.json']})
    except PermissionError as e:state['error']=str(e)
    except Exception as e:state.update(status='FAIL2_PRIVATE_RSUBREAD_RUNTIME_NOT_EXPORTED',error=repr(e));(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Previously validated original package files and complete corresponding original source were exported. No R/counting ran; the separate original R runtime is not bundled or installed.')+'\n\nRequired action: '+action+'\n\nThe selected2.23.2 source has retained upstream development warnings and does not identify the paper commit/binary. Read the runtime/analysis scope before use.\n',encoding='utf8',newline='\n');print(state['status']);return 0 if state['status'].startswith('passed-') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable archive/source/known-package and permission checks')
    raise SystemExit(main())
