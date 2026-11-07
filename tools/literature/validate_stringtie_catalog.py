# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render all guarded catalog providers and test broad plus StringTie-specific local browser behavior."""
import argparse
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import urllib.request
from catalog_fixed_cohort import verify_fixed_cohort
from catalog_source_integrity import verify_source
from stringtie_catalog import verify as verify_stringtie
from validate_mashr_native import pin
from validate_stringtie_gtf import save

ROOT=Path(__file__).resolve().parents[2]
NODE=Path('C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe')
MODULES=NODE.parent.parent/'node_modules'
EDGE=Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--baseline',type=Path);parser.add_argument('--reuse-success',type=Path);args=parser.parse_args();out=args.out.resolve();base=ROOT/'build/literature-review/stringtie-runtime';assert out.is_relative_to(base.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='STRINGTIE_CATALOG_VALIDATION_IN_PROGRESS'))
    data=ROOT/'docs/literature/omics-2023-2026';baseline=args.baseline.resolve() if args.baseline else None
    if baseline:assert baseline.is_relative_to(base.resolve()) and baseline!=out and json.loads((baseline/'render-all-catalog-providers.process.json').read_bytes())['exit_code']==0
    old_inventory=json.loads((baseline/'before-tool-inventory.json' if baseline else data/'tool-inventory.json').read_bytes());before_papers=pin(data/'papers.json');registry=pin(ROOT/'tools/literature/tool-registry.json');pages=pin(ROOT/'.github/workflows/pages.yml');canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');server=None;owned=False
    assert next(t for t in old_inventory if t['id']=='stringtie')['implementation_status']=='missing'
    save(out/'before-tool-inventory.json',old_inventory);(out/'before-summary.json').write_bytes((baseline/'before-summary.json' if baseline else data/'summary.json').read_bytes())
    try:
        proof=verify_stringtie(ROOT,data)
        def command(label,argv,env=None,timeout=900):
            argv=list(map(str,argv))
            with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout,check=False,env=env)
            save(out/(label+'.process.json'),dict(command=argv,exit_code=result.returncode));assert result.returncode==0,(label,result.returncode)
        reused=args.reuse_success.resolve() if args.reuse_success else None
        if reused:
            assert reused.is_relative_to(base.resolve()) and reused!=out and json.loads((reused/'COMPLETED.json').read_bytes())['status']=='passed-StringTie-catalog-provider-source-preservation-and-browser-integration'
            frozen_inputs=json.loads((reused/'visual-before-source-pins.json').read_bytes());assert all(pin(ROOT/n)==p for n,p in frozen_inputs.items())
            save(out/'reused-render-and-broad-source-pins.json',frozen_inputs)
            for name in ['render-all-catalog-providers','broad-review-ui']:
                assert json.loads((reused/(name+'.process.json')).read_bytes())['exit_code']==0
                for suffix in ['.stdout','.stderr','.process.json']:shutil.copyfile(reused/(name+suffix),out/(name+suffix))
            (out/'broad-ui').mkdir();shutil.copyfile(reused/'broad-ui/ui-validation.json',out/'broad-ui/ui-validation.json')
        else:command('render-all-catalog-providers',[sys.executable,ROOT/'tools/literature/render_review.py'])
        current=json.loads((data/'tool-inventory.json').read_bytes());old={t['id']:t for t in old_inventory};new={t['id']:t for t in current};assert set(old)==set(new) and len(new)==387
        assert {k for k in old if old[k]!=new[k]}=={'stringtie'},'Unrelated tool metadata changed'
        counts=json.loads((data/'summary.json').read_bytes());assert counts['papers']==250 and counts['paper_tool_observations']==2442 and counts['implementation_status_counts']['upstream-adapter-validated']==52 and counts['implementation_status_counts']['native-subset-validated']==16 and counts['implementation_status_counts']['missing']==259
        assert new['stringtie']['implementation_status']=='upstream-adapter-validated' and new['stringtie']['development_candidate_sha256']==proof['artifact']['sha256'] and not new['stringtie']['canonical_adapter_available']
        papers=json.loads((data/'papers.json').read_bytes());verify_fixed_cohort(ROOT,data,papers);assert pin(data/'papers.json')==before_papers and sum(t['review_status']=='usage-summary-reviewed; paper-wide-completeness-review-open' for p in papers for t in p['tools'])==827
        sums=dict(line.split('  ',1)[::-1] for line in (data/'SHA256SUMS').read_text().splitlines());assert set(sums)=={p.name for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'} and all(pin(data/n)['sha256']==h for n,h in sums.items())
        for source in ['src/main/java/org/jlinalg/cli/StringTieWorkflow.java','src/main/java/org/jlinalg/sequence/StringTieNormalization.java']:
            try:verify_source(ROOT,data,source,'0'*64)
            except ValueError:pass
            else:raise AssertionError('A scientific source mismatch was waived')
        assert pin(ROOT/'tools/literature/tool-registry.json')==registry and pin(ROOT/'.github/workflows/pages.yml')==pages and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        assert 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text()
        # Reuse an existing matching local server without taking ownership; otherwise
        # start and close only our own hidden subprocess.
        try:
            with socket.create_connection(('127.0.0.1',8768),timeout=1):pass
        except OSError:
            stdout=(out/'server.stdout').open('xb');stderr=(out/'server.stderr').open('xb')
            server=subprocess.Popen([sys.executable,'-m','http.server','8768','--bind','127.0.0.1','--directory',str(ROOT/'site')],cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0);owned=True
            for _ in range(50):
                if server.poll() is not None:raise RuntimeError('Owned local review server terminated')
                try:
                    with socket.create_connection(('127.0.0.1',8768),timeout=.2):break
                except OSError:time.sleep(.1)
            else:raise RuntimeError('Local review server did not become ready')
        with urllib.request.urlopen('http://127.0.0.1:8768/literature-review.html',timeout=10) as response:assert response.read(67108865)==(ROOT/'site/literature-review.html').read_bytes()
        assert NODE.is_file() and EDGE.is_file() and (MODULES/'playwright').is_dir()
        if not reused:
            env=dict(os.environ);env['JLINALG_REVIEW_UI_OUT']=str(out/'broad-ui');command('broad-review-ui',[NODE,ROOT/'tools/literature/check_review_ui.cjs',MODULES,EDGE],env)
        command('StringTie-review-ui',[NODE,ROOT/'tools/literature/check_stringtie_review_ui.cjs',MODULES,EDGE,out/'stringtie-ui'])
        broad=json.loads((out/'broad-ui/ui-validation.json').read_bytes());focused=json.loads((out/'stringtie-ui/ui-validation.json').read_bytes());assert len(broad['checks'])>=299 and focused['check_count']>=12 and not focused['errors']
        assert focused['actual_theme_control_tested'] and focused['dark_warning_theme_persisted_after_navigation']
        if reused:assert all(pin(ROOT/n)==p for n,p in frozen_inputs.items())
        assert pin(data/'papers.json')==before_papers and pin(ROOT/'tools/literature/tool-registry.json')==registry and pin(ROOT/'.github/workflows/pages.yml')==pages and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        result=dict(status='passed-StringTie-catalog-provider-source-preservation-and-browser-integration',catalog_counts=dict(new_scoped_integrations=68,including_existing=71,missing=259,upstream_adapters=52,native=16,reviewed_uses=827,supporting_pointers=253),only_tool_metadata_changed='stringtie',source_papers=250,observations=2442,broad_browser_checks=len(broad['checks']),stringtie_browser_checks=focused['check_count'],checksummed_catalog_files=len(sums),canonical_executable_unchanged=canonical,pages_workflow_unchanged=pages,literature_pages_still_withheld=True,root_artifact=proof['artifact'],released=False,pushed=False,deployed=False,whole_goal_complete=False)
        result.update(reused_unchanged_render_and_broad_validation=None if not reused else reused.relative_to(ROOT).as_posix(),actual_dark_theme_control_tested=True,dark_warning_theme_persisted_after_navigation=True,dark_background=focused['dark_background'])
        save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(result))
    except BaseException as error:
        save(out/'FAILURE.json',dict(status='FAIL2_CATALOG_VALIDATION_FAILED',error=str(error),required_action='Preserve the attempt and logs. Correct the named catalog/provider/browser issue, retain scientific source guards and retry into NEW output paths. No release/deployment or whole-goal completion is admitted.'));raise
    finally:
        if owned and server is not None:
            server.terminate()
            try:server.wait(timeout=10)
            except subprocess.TimeoutExpired:server.kill();server.wait(timeout=10)
            stdout.close();stderr.close();save(out/'owned-server-closed.json',dict(pid=server.pid,return_code=server.returncode,only_owned_server_terminated=True))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable catalog/provider/browser validation')
    main()
