# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test installation from a minimal clean repository snapshot and explicit refusals."""
import argparse,hashlib,json,subprocess,sys,traceback
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/sniffles-runtime';DISTRO='JLinAlg-Omics-SAIGE-20261026'
INSTALLER='tools/literature/install_sniffles_runtime_linux.py'


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNIFFLES_CLEAN_INSTALL_VALIDATION',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,cases=[])
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
    copy_names=[INSTALLER,'tools/literature/sniffles_native_evidence.py','tools/literature/setup_deseq2_reference.py',
        'src/main/resources/org/jlinalg/omics/sniffles-execution/profiles.json',
        *['docs/literature/sniffles-vcf-development/'+n for n in ['VALIDATION.json','controls.zip','sniffles-vcf-development.jar','sniffles-vcf-javac25-development.jar']],
        *['docs/literature/sniffles-runtime-distribution/'+n for n in ['DISTRIBUTION.json','assets.zip','NOTICE.md']],
        'docs/literature/sniffles-execution-development/sniffles-execution-development.jar']
    source_pins={name:pin(ROOT/name) for name in [*copy_names,Path(__file__).relative_to(ROOT).as_posix()]}
    try:
        project=out/'clean-project';project.mkdir()
        for name in copy_names:
            source=ROOT/name;target=project/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(source.read_bytes());assert pin(target)==source_pins[name]
        empty=project/'build/literature-review/sniffles-runtime';empty.mkdir(parents=True);assert not list(empty.iterdir())
        save(out/'clean-source-files.json',{name:source_pins[name] for name in copy_names})
        def execute(name,command,want,target=None,timeout=300):
            home=out/name;home.mkdir();save(home/'command.json',command)
            run=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=timeout)
            (home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr)
            save(home/'process.json',dict(exit_code=run.returncode,expected_exit_code=want))
            assert run.returncode==want,(name,run.returncode,run.stdout.decode(errors='replace'),run.stderr.decode(errors='replace'))
            item=dict(id=name,exit_code=want)
            if target is not None:
                record=json.loads((target/'COMPLETED.json').read_bytes());item['receipt']=pin(target/'COMPLETED.json')
                assert not record['analysis_admitted'] and not record['implementation_credit']
                assert 'ACTION' in (target/'WARNINGS.md').read_text(encoding='utf8')
                if want==3:assert not record['downloads'] and not record['processes'] and not (target/'runtime').exists()
            state['cases'].append(item);return run
        for name,flags in [('permission-absent',[]),('download-only',['--allow-download']),('install-only',['--allow-install']),('unsupported-windows',['--allow-download','--allow-install'])]:
            target=empty/name
            execute(name,[sys.executable,str(project/INSTALLER),'--out',str(target),*flags],3,target)
        existing=empty/'existing-output';existing.mkdir();(existing/'sentinel').write_bytes(b'retain original output\n');sentinel=pin(existing/'sentinel')
        rejected=execute('existing-output',[sys.executable,str(project/INSTALLER),'--out',str(existing),'--allow-download','--allow-install'],2)
        assert pin(existing/'sentinel')==sentinel and {p.name for p in existing.iterdir()}=={'sentinel'} and b'ACTION' in rejected.stderr
        target=empty/'installed'
        execute('fresh-install',['wsl','-d',DISTRO,'--exec','/usr/bin/python3.12','-I','-B','-S','-X','pycache_prefix='+linux(target/'never-created-startup-cache'),linux(project/INSTALLER),
            '--out',linux(target),'--allow-download','--allow-install'],0,target,600)
        record=json.loads((target/'COMPLETED.json').read_bytes())
        assert record['status']=='PASS_INSTALLED_EXACT_PRIVATE_SNIFFLES_RUNTIME_ONLY' and record['preparation_inputs_unchanged']
        assert record['shared_python_and_standard_library_unchanged'] and not record['system_packages_installed'] and not record['source_namespace_patched']
        assert record['original_runtime_health_and_binding_still_required'] and len(record['downloads'])==4 and len(record['processes'])==1
        assert not record['processes'][0]['owned_live_after_cleanup']
        profile=json.loads((project/'src/main/resources/org/jlinalg/omics/sniffles-execution/profiles.json').read_bytes())
        assert json.loads((target/'installed-site-files.json').read_bytes())==profile['private_site_members']
        assert (target/'shared-before.json').read_bytes()==(target/'shared-after.json').read_bytes()
        expected=json.loads((target/'runtime-files.json').read_bytes())
        assert len(expected)==1721 and all(pin(target/'runtime'/name)==value for name,value in expected.items())
        for name in copy_names:assert pin(project/name)==source_pins[name]
        assert all(pin(ROOT/name)==value for name,value in source_pins.items()) and pin(canonical)==before
        state.update(status='PASS_SNIFFLES_CLEAN_INSTALL_AND_FIVE_PUBLIC_REFUSALS_ONLY',source_pins=source_pins,
            minimal_clean_source_files=len(copy_names),preexisting_ignored_runtime_required=False,installed_runtime_files=len(expected),
            private_site_files=len(profile['private_site_members']),official_downloads=4,
            installed_runtime_root=linux(target/'runtime'),installed_receipt=pin(target/'COMPLETED.json'),
            original_health_binding_and_calling_still_required=True,canonical_executable_unchanged=before)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable clean-install/source-preservation checks')
    raise SystemExit(main())
