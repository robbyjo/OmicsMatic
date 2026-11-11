# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Copy the tested original Sniffles programs and three dependencies to a NEW private runtime.

Requires --allow-install. No installer, package manager, analysis or global write
is performed. Source preparation must already have completed successfully.
"""
import argparse,json,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--allow-install',action='store_true');args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime');out.mkdir(parents=True)
    action='Dependent Sniffles calling remains skipped until a permitted original runtime is exported and bound. Where copying/installing is permitted rerun with --allow-install into a NEW workspace path. Otherwise retain the skip and supply a permitted tested runtime. Preserve all existing sources, data and failed attempts.'
    state=dict(status='SKIP3_SNIFFLES_RUNTIME_EXPORT_UNAVAILABLE',required_action=action,analysis_admitted=False,implementation_credit=False)
    try:
        assert args.allow_install,'Runtime copying/installing not authorized; no program or library copied'
        profile_path=ROOT/'src/main/resources/org/jlinalg/omics/sniffles-execution/profiles.json'
        profile=json.loads(profile_path.read_bytes())
        base=ROOT/'build/literature-review/sniffles-runtime';reference=base/'reference-setup-3';intake=base/'source-intake-3'
        receipt=json.loads((reference/'COMPLETED.json').read_bytes())
        assert receipt['status']=='PASS_PRIVATE_FIVE_ORIGINAL_SNIFFLES_REFERENCE_RUNTIMES_ONLY'
        before={str(p.relative_to(ROOT)):pin(p) for p in [profile_path,reference/'COMPLETED.json',intake/'COMPLETED.json']}
        root=out/'runtime';root.mkdir()
        copied={}
        def copy(source,name,want):
            assert pin(source)==want,('Original prepared runtime changed',source)
            target=root/name;assert target.resolve().is_relative_to(root)
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read_bytes())
            assert pin(target)==want and pin(source)==want
            copied[name]=want
        copy(reference/'venv/bin/python','python',profile['python'])
        copy(reference/'sources/v1.0.7/bin/sniffles-core-1.0.7/sniffles','legacy-sniffles',profile['legacy_binary'])
        for name,value in profile['private_site_members'].items():copy(reference/'venv/lib/python3.12/site-packages'/name,'site/'+name,value)
        for tag,row in profile['editions'].items():
            for name,value in row['members'].items():copy(intake/tag/'source'/name,'sources/'+tag+'/'+name,value)
        assert before=={str(p.relative_to(ROOT)):pin(p) for p in [profile_path,reference/'COMPLETED.json',intake/'COMPLETED.json']}
        state.update(status='PASS_EXPORTED_EXACT_ORIGINAL_SNIFFLES_RUNTIME_ONLY',runtime_root=str(root),
            files=copied,source_profile=pin(profile_path),preexisting_sources_and_runtime_unchanged=True,
            system_packages_installed=False,analysis_executed=False,
            source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Original runtime copied; native pre-execution binding and calling validation remain required.')+'\n\nREQUIRED ACTION: '+action+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original runtime copy checks')
    raise SystemExit(main())
