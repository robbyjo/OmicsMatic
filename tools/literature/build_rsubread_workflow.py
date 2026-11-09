# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile the original counting workflow additively over the JDK-only runtime/result API."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['runtime-build','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--javac',default='javac');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True)
    state=dict(status='FAIL2_RSUBREAD_WORKFLOW_BUILD',analysis_admitted=False,implementation_credit=False,full_adapter_available=False,whole_goal_complete=False)
    try:
        previous=json.loads((a.runtime_build/'COMPLETED.json').read_bytes());assert previous['status']=='passed-JDK-only-Rsubread-original-runtime-binding-API-build';parent=ROOT/previous['artifact']['path'];assert pin(parent)=={k:previous['artifact'][k] for k in ['bytes','sha256']};assert all(pin(ROOT/n)==v for n,v in previous['source_pins'].items())
        sources=[ROOT/('src/main/java/org/jlinalg/cli/'+name+'.java') for name in ['RsubreadContracts','RsubreadWorkflowCli']];worker=ROOT/'src/main/resources/org/jlinalg/omics/rsubread_count_worker.R';pins={q.relative_to(ROOT).as_posix():pin(q) for q in [Path(__file__).resolve(),*sources,worker]}
        javac=Path(shutil.which(a.javac) or a.javac).resolve();classes=out/'classes';classes.mkdir();command=[str(javac),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)]
        run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace')
        added={q.relative_to(classes).as_posix():q.read_bytes() for q in classes.rglob('*.class')};added['org/jlinalg/omics/rsubread_count_worker.R']=worker.read_bytes()
        with zipfile.ZipFile(parent) as z:entries={n:z.read(n) for n in z.namelist()}
        assert set(added).isdisjoint(entries);preserved={n:byte_pin(b) for n,b in entries.items() if n!='META-INF/MANIFEST.MF'};entries.update(added)
        entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.RsubreadWorkflowCli\nImplementation-Title: OmicsMatic original Rsubread bulk counting development\nImplementation-Version: 0.3.7-development\n\n'
        jar=out/'rsubread-workflow-development.jar'
        with zipfile.ZipFile(jar,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for name,raw in sorted(entries.items()):info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
        assert all(pin(ROOT/n)==v for n,v in pins.items())
        for name in pins:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        state.update(status='passed-additive-JDK-only-Rsubread-original-counting-workflow-build',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_receipt=dict(path=(a.runtime_build/'COMPLETED.json').resolve().relative_to(ROOT).as_posix(),**pin(a.runtime_build/'COMPLETED.json')),preserved_parent_entries=preserved,added_entries={n:byte_pin(b) for n,b in added.items()})
    except Exception as e:state['error']=repr(e);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable immutable-runtime/additive-workflow checks')
    raise SystemExit(main())
