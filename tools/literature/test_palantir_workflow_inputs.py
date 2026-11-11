# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject incompatible inputs/identities explicitly; exercise kit export, permission skips and owned-worker timeout."""
import copy,csv,json,shutil,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.resolve().relative_to(ROOT).as_posix()
def main():
 p=__import__('argparse').ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/palantir-runtime');out.mkdir();state=dict(status='FAIL2_PALANTIR_INPUT_CONTRACT_CONTROLS',checks=[],analysis_admitted=False)
 try:
  built=json.loads((a.build.resolve()/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};reference=a.reference.resolve();ref=json.loads((reference/'COMPLETED.json').read_bytes());assert ref['status']=='PASS_TWO_ORIGINAL_EDITIONS_THREE_JVM_PALANTIR_COORDINATE_WORKFLOWS_ONLY';request=json.loads((reference/'1.4.6-request.json').read_bytes());request['analyses']=request['analyses'][:1];source=reference/'1.4.6-analysis/worker';raw=(reference/'components.csv').read_text(encoding='utf8');sourcepins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve()),**built['source_pins']}
  def run(label,cmd,expected):
   r=subprocess.run(cmd,capture_output=True,timeout=120);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==expected,(label,r.returncode,r.stderr.decode(errors='replace'));return r
  kit=out/'exported-kit';run('export',[str(JAVA21/'java.exe'),'-cp',str(jar),'org.jlinalg.cli.PalantirSetupCli','--export',str(kit)],0)
  def reject(label,mutate=None,text=None,needle=None):
   home=out/label;home.mkdir();fixture=copy.deepcopy(request)
   if mutate:mutate(fixture)
   (home/'components.csv').write_text(raw if text is None else text,encoding='utf8',newline='');req=home/'request.json';save(req,fixture);report=home/'must-not-exist.json';r=subprocess.run([str(JAVA21/'java.exe'),'-cp',str(jar),'org.jlinalg.cli.PalantirWorkflowMatrix',str(req),str(source),str(report)],capture_output=True,timeout=120);(home/'stdout').write_bytes(r.stdout);(home/'stderr').write_bytes(r.stderr);assert r.returncode!=0 and not report.exists() and needle in r.stderr.decode('utf8',errors='replace'),(label,r.returncode,r.stderr.decode(errors='replace'));state['checks'].append(dict(label=label,exit_code=r.returncode,required_message=needle))
  for label,token,needle in [('nonzero-underflow','1e-999','Nonzero decimal coordinates underflow'),('overflow','1e309','Coordinate overflows float64'),('nan','NaN','finite decimal coordinate'),('infinite','Inf','finite decimal coordinate'),('numeric-whitespace',' 0.0','finite decimal coordinate')]:
   rows=list(csv.reader(raw.splitlines()));rows[1][1]=token
   import io
   f=io.StringIO(newline='');w=csv.writer(f);w.writerows(rows);reject(label,text=f.getvalue(),needle=needle)
  rows=list(csv.reader(raw.splitlines()));rows[2][0]=rows[1][0];f=io.StringIO(newline='');csv.writer(f).writerows(rows);reject('duplicate-cells',text=f.getvalue(),needle='Duplicate cell ID')
  rows=list(csv.reader(raw.splitlines()));rows[0][2]=rows[0][1];f=io.StringIO(newline='');csv.writer(f).writerows(rows);reject('duplicate-components',text=f.getvalue(),needle='Duplicate component column')
  reject('wrong-cell-header',text=raw.replace('cell_id,','Cell_ID,',1),needle='literal cell_id header')
  reject('missing-early',lambda v:v['analyses'][0]['trajectory'].__setitem__('early_cells',['absent-cell']),needle='Requested early cell absent')
  reject('partial-terminals',lambda v:v['analyses'][0]['trajectory']['terminal_states'].__setitem__(1,dict(cell='absent-terminal',annotation='branch-B')),needle='Requested terminal cell absent')
  reject('missing-waypoint',lambda v:v['analyses'][0]['trajectory'].__setitem__('waypoints',dict(cells=['cell-000','absent-waypoint'])),needle='Explicit waypoint cell absent')
  reject('no-id-whitespace-repair',text=raw.replace('cell-000,',' cell-000 ,',1),needle='Requested early cell absent')
  reject('unknown-option',lambda v:v['analyses'][0]['trajectory'].__setitem__('invented_option',True),needle='Unknown Palantir workflow field')
  reject('unreported-empty-policy',lambda v:v['analyses'][0]['trajectory'].pop('empty_fates'),needle='Expected')
  reject('multiple-roots-old-edition',lambda v:(v.__setitem__('edition','1.4.4'),v['analyses'][0]['trajectory'].__setitem__('early_cells',['cell-000','cell-048'])),needle='Original1.4.4 accepts one early cell')
  # All permitted/forbidden controls run the exported kit. No setup may create a runtime here.
  for label,args,receipt in [('install-forbidden',[],ROOT/'build/cellphonedb-workflow-case-sensitive-1/setup-1/COMPLETED.json'),('base-missing',['--allow-install'],out/'no-base.json')]:
   target=out/(label+'-setup');prefix=out/(label+'-must-not-install');cmd=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(kit/'setup/prepare.py'),'--out',linux(target),'--prefix',linux(prefix),'--base-runtime',linux(receipt),*args];run(label,cmd,3);report=json.loads((target/'COMPLETED.json').read_bytes());assert report['status']=='SKIP3_PALANTIR_RUNTIME_UNAVAILABLE' and not report['processes'] and not prefix.exists();state['checks'].append(dict(label=label,exit_code=3))
  home=out/'owned-timeout';home.mkdir();runtime=json.loads((ROOT/'build/literature-review/palantir-runtime/workflow-setup-1/COMPLETED.json').read_bytes());save(home/'runtime.original.json',runtime);worker_request=json.loads((source/'request.json').read_bytes());worker_request['analyses']=worker_request['analyses'][:1];save(home/'request.json',worker_request)
  for name in ['worker.py','codec.py','supervisor.py']:shutil.copyfile(kit/name,home/name)
  cmd=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec',runtime['runtime_python'],'-I','-B',linux(home/'supervisor.py'),'--runtime',linux(home/'runtime.original.json'),'--out',linux(home),'--worker',linux(home/'worker.py'),'--request',linux(home/'request.json'),'--timeout','1'];run('owned-timeout',cmd,2);report=json.loads((home/'original.process.json').read_bytes());assert 'TimeoutError' in report['error'] and not report['owned_live_after_cleanup'] and not (home/'COMPLETED.json').exists();state['checks'].append(dict(label='owned-timeout',exit_code=2,owned_live_after_cleanup=[]))
  assert all(pin(ROOT/n)==v for n,v in sourcepins.items());state.update(status='PASS_PALANTIR_INPUT_EXPORT_PERMISSION_AND_TIMEOUT_CONTROLS_ONLY',artifact=built['artifact'],source_pins=sourcepins,exported_kit_installed_software=False)
 except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
 save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':raise SystemExit(main())
