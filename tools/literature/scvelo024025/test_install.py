#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check permission/OS skips, preservation of existing paths and original download-integrity refusal."""
from pathlib import Path
import argparse,json,shutil,subprocess,sys,time
HERE=Path(__file__).resolve().parent

def save(p,v):p.write_bytes((json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode('utf8'))
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);cases=[]
 def execute(name,flags,expected,source=HERE,prefix=None,evidence=None,owned=True):
  prefix=prefix or out/'prefixes'/name;evidence=evidence or out/'evidence'/name;command=[sys.executable,str(source/'install.py'),'--version','0.2.4','--prefix',str(prefix),'--out',str(evidence),*flags];start=time.time();r=subprocess.run(command,capture_output=True,timeout=300);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
  assert r.returncode==expected,(name,r.returncode,r.stdout[-3000:],r.stderr[-1500:])
  if owned:
   failure=json.loads((evidence/'FAILURE.json').read_bytes());assert failure['dependent_analysis_skipped'] and not (evidence/'provider/READY.json').exists();assert 'SKIP3' in (evidence/'WARNINGS.md').read_text() and 'FAIL2' in (evidence/'WARNINGS.md').read_text()
  row=dict(id=name,expected_exit=expected,exit_code=r.returncode,command=command,elapsed_seconds=time.time()-start);cases.append(row);save(out/'CASES.json',cases);print(name,'expected exit',expected,flush=True);return prefix,evidence
 p,e=execute('installation-forbidden',[],3);assert not p.exists()
 p,e=execute('download-forbidden',['--allow-install'],3);assert not p.exists()
 if sys.platform!='linux':
  p,e=execute('unsupported-platform',['--allow-install','--allow-download'],3);assert not p.exists()
 else:
  prefix=out/'prefixes/existing-prefix';prefix.mkdir(parents=True);(prefix/'preserve.txt').write_bytes(b'original existing runtime marker')
  execute('existing-prefix-refusal',['--allow-install','--allow-download'],2,prefix=prefix);assert (prefix/'preserve.txt').read_bytes()==b'original existing runtime marker'
  evidence=out/'evidence/existing-evidence';evidence.mkdir(parents=True);(evidence/'preserve.txt').write_bytes(b'original existing evidence marker')
  p,e=execute('existing-evidence-refusal',['--allow-install','--allow-download'],2,evidence=evidence,owned=False);assert (evidence/'preserve.txt').read_bytes()==b'original existing evidence marker' and not p.exists()
  prefix=out/'overlap-prefix';p,e=execute('overlapping-output-refusal',['--allow-install','--allow-download'],2,prefix=prefix,evidence=prefix/'evidence',owned=False);assert not prefix.exists()
  for name,profile,key in [('wrong-bootstrap-binary','bootstrap-profile.json','binary_sha256'),('wrong-original-source-archive','source-profile.json',None)]:
   source=out/(name+'-source');source.mkdir()
   for n in ['install.py','bootstrap-profile.json','requirements-cpu024.txt','requirements-cpu025.txt','source-profile.json','runtime-profile.json','runtime_audit.py','LICENSE-BSD-3-Clause.txt','uv-bootstrap-notices/LICENSE-APACHE','uv-bootstrap-notices/LICENSE-MIT']:
    (source/n).parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(HERE/n,source/n)
   v=json.loads((source/profile).read_bytes())
   if key:v[key]='0'*64
   else:next(row for p in v['profiles'] if p['version']=='0.2.4' for row in p['source_downloads'] if row['packagetype']=='sdist')['sha256']='0'*64
   save(source/profile,v);p,e=execute(name,['--allow-install','--allow-download'],2,source=source);assert not (e/'standalone-python.live.json').exists()
 result=dict(status='PASS_ORIGINAL_SCVELO_SETUP_PERMISSION_PLATFORM_EXISTING_PATH_AND_HASH_CONTROLS',controls=len(cases),cases=cases,existing_runtimes_preserved=True,dependent_analysis_skipped_on_every_refusal=True,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],flush=True)
if __name__=='__main__':main()
