#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise frozen-verifier refusals using separate evidence copies, never scientific-source overlays."""
from pathlib import Path
import argparse,hashlib,importlib.util,io,json,shutil,subprocess,sys,time,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];DATA=ROOT/'docs/literature/ggpubr-workflow-development'

def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return rawpin(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
 if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
 out.mkdir(exist_ok=False);base=json.loads((DATA/'VALIDATION.json').read_bytes());before={n:pin(ROOT/n) for n in base['sources']};base_pin=pin(DATA/'VALIDATION.json');archive_files=base.get('archive_parts') or {'controls.zip':base['archive']}
 spec=importlib.util.spec_from_file_location('frozen_original_ggpubr_verification',HERE/'verify.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 raw=module.archive_bytes(DATA,base);layout=json.loads((DATA/'CONTROLS_LAYOUT.json').read_bytes());calls=[]
 launcher=out/'invoke-frozen-verifier.py'
 launcher.write_text('from pathlib import Path\nimport importlib.util,sys\ns=importlib.util.spec_from_file_location("frozen_ggpubr_verifier",'+repr(str(HERE/'verify.py'))+')\nm=importlib.util.module_from_spec(s);s.loader.exec_module(m)\nm.DATA=Path(sys.argv[1]);sys.argv=["verify.py","--out",sys.argv[2]]\nraise SystemExit(m.main())\n',encoding='utf8',newline='\n')
 def run(name,data,expected,needle=None):
  result=out/(name+'-verification');command=list(map(str,[sys.executable,launcher,data,result]));start=time.time();r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=600)
  (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
  row=dict(case=name,command=command,exit_code=r.returncode,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(name+'.process.json'),row)
  assert r.returncode==expected,(name,row,r.stdout[-1800:],r.stderr[-1800:])
  if expected:
   failure=json.loads((result/'FAILURE.json').read_bytes());assert needle is None or needle in failure['cause'],(name,failure)
   assert 'Required action:' in (result/'WARNINGS_FAILURE.md').read_text()
  print(name,'expected exit',expected,flush=True)
 def fixture(name):
  target=out/name;target.mkdir()
  for n in ['catalog-before.zip','CONTROLS_LAYOUT.json','ARTIFACTS.json','CENSUS.json','PAPER_USE.json','VALIDATION.json']:
   shutil.copyfile(DATA/n,target/n)
  return target
 def sums(target):
  (target/'SHA256SUMS').write_text(''.join(pin(p)['sha256']+'  '+p.name+'\n' for p in sorted(target.iterdir()) if p.is_file() and p.name!='SHA256SUMS'),encoding='ascii',newline='\n')
 def store_archive(target,payload,proof):
  proof['archive']=rawpin(payload);proof['archive_parts']={}
  if len(payload)<=90000000:(target/'controls.zip').write_bytes(payload)
  else:
   for i,start in enumerate(range(0,len(payload),90000000),1):
    name='controls.zip.part'+str(i).zfill(3);part=payload[start:start+90000000];(target/name).write_bytes(part);proof['archive_parts'][name]=rawpin(part)
  save(target/'VALIDATION.json',proof)
 def mutate_archive(target,drop=None,change=None):
  modified=io.BytesIO();changed_layout=json.loads(json.dumps(layout))
  with zipfile.ZipFile(io.BytesIO(raw)) as src,zipfile.ZipFile(modified,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as dst:
   for info in src.infolist():
    if info.filename==drop:continue
    payload=src.read(info.filename)
    if change and info.filename==change:
     record=json.loads(payload);record.update(result_admitted=True,exit_code=0);payload=(json.dumps(record,indent=2)+'\n').encode();changed_layout['files'][info.filename]=rawpin(payload)
    dst.writestr(info,payload)
  proof=json.loads((target/'VALIDATION.json').read_bytes())
  if change:save(target/'CONTROLS_LAYOUT.json',changed_layout);proof['controls_layout']=pin(target/'CONTROLS_LAYOUT.json')
  store_archive(target,modified.getvalue(),proof);sums(target)
 run('baseline',DATA,0)
 target=fixture('source-pin-contradiction');proof=json.loads((target/'VALIDATION.json').read_bytes());proof['sources']['tools/literature/ggpubr_workflow/run.py']['sha256']='0'*64;save(target/'VALIDATION.json',proof);sums(target)
 run('source-pin-contradiction',target,2,'Current qualified source differs')
 target=fixture('missing-complete-evidence');sums(target);run('missing-complete-evidence',target,3)
 target=fixture('missing-original-plot')
 with zipfile.ZipFile(io.BytesIO(raw)) as z:
  case=next(v for v in json.loads(z.read('complete/current/fixtures/fixture-manifest.json'))['cases'].values() if v['kind']=='plot')
 name='complete/current/actual-0.4.0/results/plots/'+case['id']+'.svg';assert name in layout['files'];mutate_archive(target,drop=name)
 run('missing-original-plot',target,2,'Incomplete or duplicate retained scientific evidence population')
 target=fixture('forged-failed-layer-admission');name='protocols/current/protocols-linux-0.4.0/original-statistical-layer-failure/analysis-status.json';assert name in layout['files'];mutate_archive(target,change=name)
 run('forged-failed-layer-admission',target,2,'Original warning-only statistical failure was admitted or erased')
 assert pin(DATA/'VALIDATION.json')==base_pin and all(pin(ROOT/n)==wanted for n,wanted in before.items())
 assert all(pin(DATA/n)==wanted for n,wanted in archive_files.items())
 result=dict(status='PASS_FOUR_FROZEN_GGPUBR_VERIFIER_REFUSALS_WITHOUT_SCIENTIFIC_SOURCE_CHANGES',baseline_controls=1,refusal_controls=4,calls=calls,
  base_validation=base_pin,base_scientific_archive=base['archive'],scientific_sources_changed=False,scientific_sources_overridden=False,verifier_source_changed=False,
  source_or_result_hashes_refreshed_in_original_checkpoint=False,negative_evidence_copies_explicitly_labelled=True,new_tool_credit=0,whole_goal_complete=False)
 save(out/'COMPLETED.json',result);print(result['status'],flush=True)
if __name__=='__main__':main()
