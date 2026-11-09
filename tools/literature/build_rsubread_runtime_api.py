# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile the JDK-only original-runtime binding API without replacing any validated result-value class."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--javac',default='javac');a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_RSUBREAD_RUNTIME_API_BUILD',analysis_admitted=False,implementation_credit=False,full_adapter_available=False,whole_goal_complete=False)
    try:
        from rsubread_result_evidence import verify
        previous,_=verify(False);assert previous['native_result_value_component_available'];parent=ROOT/'docs/literature/rsubread-result-development/rsubread-result-values-api.jar'
        sources=[ROOT/('src/main/java/org/jlinalg/cli/'+name+'.java') for name in ['SimpleJson','RsubreadSupport','RsubreadProcess','RsubreadProfiles','RsubreadRuntimeCli']]
        reference=ROOT/'src/main/resources/org/jlinalg/omics/rsubread-workflow-reference-v2';resources=[*sorted(q for q in reference.rglob('*') if q.is_file()),ROOT/'src/main/resources/org/jlinalg/omics/rsubread_runtime_guard.R']
        sourcepins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),ROOT/'tools/literature/prepare_rsubread_workflow_sources.py',ROOT/'tools/literature/prepare_rsubread_runtime_profiles_v2.py',*sources,*resources]}
        javac=Path(shutil.which(a.javac) or a.javac).resolve();classes=out/'classes';classes.mkdir()
        command=[str(javac),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)]
        run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace')
        added={q.relative_to(classes).as_posix():q.read_bytes() for q in classes.rglob('*.class')};added.update({q.relative_to(ROOT/'src/main/resources').as_posix():q.read_bytes() for q in resources})
        with zipfile.ZipFile(parent) as z:entries={n:z.read(n) for n in z.namelist()}
        assert set(entries).isdisjoint(added);oldclasses={n:byte_pin(b) for n,b in entries.items() if n.endswith('.class')};assert len(oldclasses)==14
        entries.update(added);entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.RsubreadRuntimeCli\nImplementation-Title: OmicsMatic original Rsubread runtime binding development\nImplementation-Version: 0.3.7-development\n\n'
        jar=out/'rsubread-runtime-api-development.jar'
        with zipfile.ZipFile(jar,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,raw in sorted(entries.items()):info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
        assert all(pin(ROOT/n)==v for n,v in sourcepins.items())
        for name in sourcepins:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        state.update(status='passed-JDK-only-Rsubread-original-runtime-binding-API-build',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=sourcepins,previous_api=dict(path=parent.relative_to(ROOT).as_posix(),**pin(parent)),unchanged_result_value_classes=oldclasses,added_entries={n:byte_pin(b) for n,b in added.items()},runtime_dependencies='JDK17+ for binding; explicit known original Windows R/Matrix/Rsubread files for the guarded probe; no installation')
    except Exception as e:state['error']=repr(e);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/reference/additive-class guards')
    raise SystemExit(main())
