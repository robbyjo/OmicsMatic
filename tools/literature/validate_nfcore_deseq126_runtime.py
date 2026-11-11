# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise original postfit execution and prerequisite/integrity/capacity outcomes without changing host security."""
import argparse,json,subprocess,tarfile,traceback,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,DISTRO,linux

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar21',type=Path,required=True);p.add_argument('--jar25',type=Path,required=True);p.add_argument('--engine-result',type=Path,required=True);p.add_argument('--linux-out-root',required=True);p.add_argument('--container-home',required=True);p.add_argument('--image',required=True);a=p.parse_args()
 out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_POSTFIT_RUNTIME_CONTROLS',nfcore_tool_credit=0,scientific_result_admitted=False)
 canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');jars={'21':a.jar21.resolve(),'25':a.jar25.resolve()};jar_pins={k:pin(v) for k,v in jars.items()}
 def wsl(args,timeout=60):
  r=subprocess.run(['wsl.exe','-d',DISTRO,'--exec',*args],capture_output=True,timeout=timeout);assert r.returncode==0,(args,r.stderr[-2000:]);return r.stdout
 try:
  root=a.linux_out_root;assert root.startswith('/var/tmp/omicsmatic-nfcore-deseq126-') and str(PurePosixPath(root))==root and '..' not in PurePosixPath(root).parts
  wsl(['/usr/bin/mkdir',root]);wsl(['/usr/bin/mkdir',root+'/incomplete-runtime']);wsl(['/usr/bin/touch',root+'/bad-image.sif'])
  changed=out/'changed-worker.jar'
  with zipfile.ZipFile(jars['21']) as original,zipfile.ZipFile(changed,'x',compression=zipfile.ZIP_DEFLATED) as z:
   for item in original.infolist():z.writestr(item,original.read(item)+(b'\n# deliberate control mutation\n' if item.filename=='org/jlinalg/omics/nfcore-deseq126/inspect.R' else b''))
  common=['--engine-result',linux(a.engine_result.resolve()),'--peak-type','broadPeak','--min-replicates','1','--read-mode','paired','--container-home',a.container_home,'--image',a.image]
  checks=[]
  specs=[('windows_unavailable','21',3,[],None),('missing_runtime','21',3,['--container-home',root+'/absent'],None),('missing_image','21',3,['--image',root+'/absent.sif'],None),('incomplete_runtime','21',2,['--container-home',root+'/incomplete-runtime'],None),('wrong_image','21',2,['--image',root+'/bad-image.sif'],None),('changed_worker','21',2,[],changed),('workspace_capacity','21',2,['--workspace-bytes','1000'],None),('complete21','21',0,[],None),('complete25','25',0,[],None)]
  for name,compiler,expected,overrides,artifact in specs:
   options=dict(zip(common[::2],common[1::2]));options.update(zip(overrides[::2],overrides[1::2]));destination=root+'/'+name;options['--out']=str(out/'windows-unavailable') if name=='windows_unavailable' else destination
   jar=artifact or jars[compiler];flat=[v for pair in options.items() for v in pair]
   cmd=[str(JAVA/'java.exe'),'-Xmx2048m','-jar',str(jar),*flat] if name=='windows_unavailable' else ['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx2048m','-jar',linux(jar),*flat]
   save(out/(name+'.STARTED.json'),dict(command=cmd,expected_exit=expected,artifact=pin(jar)))
   with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
   save(out/(name+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==expected,(name,r.returncode,(out/(name+'.stderr')).read_bytes()[-1500:])
   if name=='windows_unavailable':folder=out/'windows-unavailable'
   else:
    archive=out/(name+'.tar')
    with archive.open('xb') as so:rr=subprocess.run(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/tar','-C',root,'-cf','-',name],stdin=subprocess.DEVNULL,stdout=so,stderr=subprocess.PIPE,timeout=120)
    assert rr.returncode==0,rr.stderr
    folder=out/'evidence'/name;folder.parent.mkdir(exist_ok=True)
    with tarfile.open(archive) as tar:
     for item in tar.getmembers():
      path=PurePosixPath(item.name);assert not path.is_absolute() and '..' not in path.parts and path.parts[0]==name and (item.isdir() or item.isfile())
     tar.extractall(out/'evidence',filter='data')
   completed=folder/'COMPONENT_AUDIT_COMPLETED.json'
   if expected==0:
    assert completed.is_file();report=json.loads((folder/'audit.json').read_bytes());assert len(report['antibodies'])==1 and report['antibodies'][0]['complete_genes']==1472 and report['nfcore_tool_credit']==0
   else:
    assert not completed.exists();failure=json.loads((folder/'FAILURE.json').read_bytes());assert failure['status']==('SKIP3_ANALYSIS_SKIPPED' if expected==3 else 'FAIL2_NO_COMPONENT_RESULT') and 'Required action:' in (folder/'WARNINGS.md').read_text()
   for ledger in folder.rglob('process.json'):
    data=json.loads(ledger.read_bytes());assert data.get('owned_live_after_cleanup')==[],(name,ledger)
   checks.append(dict(case=name,passed=True,exit_code=expected,evidence_directory=folder.relative_to(out).as_posix()));print('PASS',name,flush=True)
  assert all(pin(jars[k])==v for k,v in jar_pins.items())
  state.update(status='PASS_ORIGINAL_POSTFIT_PACKAGED_RUNTIME_CONTROLS_ONLY',checks=checks,artifacts=jar_pins,host_security_configuration_changed=False,namespace_prohibition_branch_exercised=False,original_analysis_parameters_reduced=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable runtime outcome, cleanup or integrity checks')
 raise SystemExit(main())
