# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute both original profiles on Windows Java25 and verify explicit Linux-profile skip without interop."""
import argparse,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from probe_soupx_workflow_worker import inputs,fixed,manual
from validate_soupx_workflow_runtime import adjustment,case,matrix_equal


def wsl(path):
    p=Path(path).resolve();return '/mnt/'+p.drive[0].lower()+'/'+p.as_posix().split(':/',1)[1]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/soupx-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),ROOT/'tools/literature/probe_soupx_workflow_worker.py',ROOT/'tools/literature/validate_soupx_workflow_runtime.py']};reports=[]
    for platform,version in [('windows-java25','1.6.2'),('windows-java25','1.3.0'),('linux-java21-profile-skip','1.6.2')]:
        folder=out/(platform+'-'+version);folder.mkdir();data=inputs(folder/'inputs');reference=base/('reference-1.6.2-1' if version=='1.6.2' else 'reference-1.3.0-2');libraries=[reference/'R-lib',*map(Path,json.loads((reference/'STARTED.json').read_bytes())['libraries'])];source=base/('source-intake-1/1.6.2/source' if version=='1.6.2' else 'source-recovery-1/1.3.0/source');cases=[case('fixed',fixed(version),adjustment()),case('manual-provided-adjust',manual(version,True),adjustment('supplied-clusters'))];request=dict(schema_version=1,mode='AMBIENT',version=version,count_units='raw-integer-counts',matrix_format='matrix-market',clusters='supplied',counts_relation='raw-subset' if data['raw_subset_equal'] else 'supplied-independent',raw_counts='inputs/raw-counts',filtered_counts='inputs/filtered-counts',gene_metadata='inputs/genes.tsv',droplet_metadata='inputs/droplets.tsv',cell_metadata='inputs/cells.tsv',gene_sets='inputs/gene-sets.tsv',nonexpression_mask='inputs/nonexpression-mask.tsv',soup_source=str(source),soup_range=[0,100],analyses=cases,out='result',rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_home='C:/Program Files/R/R-4.6.1',r_libraries=list(map(str,libraries)),transport_chunk_entries=1000)
        if platform.startswith('linux'):
            request['soup_source']=wsl(source);request['rscript']=wsl(Path(request['rscript']));request['r_home']=wsl(Path(request['r_home']));request['r_libraries']=[wsl(path) for path in libraries];command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-Xmx1g','-cp',wsl(jar),'org.jlinalg.cli.SoupXWorkflowCli','--request',wsl(folder/'request.json')];expected=3
        else:command=['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-Xmx1g','-cp',str(jar),'org.jlinalg.cli.SoupXWorkflowCli','--request',str(folder/'request.json')];expected=0
        save(folder/'request.json',request)
        with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        save(folder/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==expected,(platform,version,(folder/'stderr').read_text(encoding='utf8',errors='replace')[-1600:]);result=folder/'result';state=json.loads((result/'analysis-status.json').read_bytes());assert state['result_admitted']==(expected==0);comparisons=[]
        if expected==0:
            old=base/('ambient-controls-1.6.2-2' if version=='1.6.2' else 'ambient-controls-1.3.0-3');control=next(row['corrected'] for row in json.loads((old/'controls.json').read_bytes())['cases'] if row['id']=='fixed-cell-only-subtraction-FALSE');direct=next(row for row in json.loads((base/f'adjustment-reference-{version}-1/controls.json').read_bytes())['cases'] if row['id']=='manual-provided-adjust');comparisons=[matrix_equal(result/'results/fixed/corrected-counts.json',control),matrix_equal(result/'results/manual-provided-adjust/corrected-counts.json',direct)];receipt=json.loads((result/'worker/process-receipt.json').read_bytes());assert all(not row['alive_after_cleanup'] for row in receipt['owned_process_cleanup'])
        else:assert state['status']=='SKIP3_DEPENDENCY_UNAVAILABLE' and 'Windows Java' in state['cause'] and not (result/'worker/process-receipt.json').exists() and not (result/'results').exists()
        reports.append(dict(platform=platform,version=version,exit_code=expected,result_admitted=expected==0,corrected_entries_compared=comparisons,source_or_runtime_fallback=False))
    assert all(pin(ROOT/n)==value for n,value in [*pins.items(),*build['source_pins'].items()]);save(out/'COMPLETED.json',dict(status='passed-original-SoupX-Windows25-and-explicit-Linux-profile-skip',artifact=build['artifact'],source_pins=pins,protocols=reports,ambient_workflow_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Passed2 Java25 original executions and explicit Linux SKIP3 without starting R')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable platform/source checks')
    main()
