# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the guide's exact CSV and scientific request, replacing operational output/runtime paths only."""
import argparse,hashlib,json,re,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.resolve().relative_to(ROOT).as_posix()
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_DOCUMENTED_EXAMPLE',implementation_credit=False,whole_goal_complete=False)
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};guide=ROOT/'docs/literature/SCFATES_WORKFLOW.md';text=guide.read_text(encoding='utf8');(out/'guide-source.md').write_bytes(guide.read_bytes());csv=re.search(r'```csv\n(.*?)```',text,re.S).group(1);raw=re.search(r'```json\n(.*?)```',text,re.S).group(1);request=json.loads(raw);request['runtime']=linux(ROOT/'build/literature-review/scfates-runtime/workflow-setup-3/COMPLETED.json');request['out']=linux(out/'analysis');(out/'coordinates.csv').write_text(csv,encoding='utf8',newline='\n');save(out/'request.json',request);command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx3g','-cp',linux(jar),'org.jlinalg.cli.ScfatesWorkflowCli','--request',linux(out/'request.json')];save(out/'STARTED.json',dict(command=command,artifact=build['artifact']));run=subprocess.run(command,capture_output=True,timeout=2400);(out/'stdout').write_bytes(run.stdout);(out/'stderr').write_bytes(run.stderr);state.update(command=command,exit_code=run.returncode);assert run.returncode==0,run.stderr.decode('utf8',errors='replace')[-3000:];admission=json.loads((out/'analysis/analysis-status.json').read_bytes());assert admission['status']=='PASS_ORIGINAL_SCFATES_WORKFLOWS' and admission['result_admitted'] and len(admission['cases'])==1;assert all(pin(ROOT/n)==want for n,want in build['source_pins'].items());state.update(status='PASS_SCFATES_EXACT_DOCUMENTED_SIX_POINT_EXAMPLE_ONLY',artifact=build['artifact'],analysis_status=admission,scientific_parameters_unchanged_from_guide=True,guide_snapshot=pin(out/"guide-source.md"),documented_csv=dict(bytes=len(csv.encode()),sha256=hashlib.sha256(csv.encode()).hexdigest()),documented_json=dict(bytes=len(raw.encode()),sha256=hashlib.sha256(raw.encode()).hexdigest()),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
