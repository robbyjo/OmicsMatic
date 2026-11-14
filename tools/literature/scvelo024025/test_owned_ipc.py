#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the actual short owned directory reference and narrow live Unix-socket monitoring."""
from pathlib import Path
import argparse,importlib.util,json,os,socket,sys,traceback
sys.dont_write_bytecode=True
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.absolute();out.mkdir(exist_ok=False);descriptor=None;sockets=[];cases=[]
 try:
  if sys.platform!='linux':raise OSError('Owned procfs/Unix-socket controls require Linux/WSL')
  file=Path(__file__).with_name('run.py');spec=importlib.util.spec_from_file_location('scvelo_owned_ipc_monitor',file);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
  tmp=out/'tmp';tmp.mkdir();manager=tmp/'pymp-abcdefgh';manager.mkdir();descriptor=os.open(tmp,os.O_RDONLY|os.O_DIRECTORY);alias='/proc/'+str(os.getpid())+'/fd/'+str(descriptor)
  assert os.path.samefile(alias,tmp);cases.append('short-reference-is-the-exact-owned-directory')
  owned=manager/'listener-12345678';peer=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);sockets.append((peer,owned));peer.bind(alias+'/pymp-abcdefgh/listener-12345678');peer.listen(1)
  assert owned.exists();cases.append('real-Unix-socket-binds-through-short-reference')
  try:module.tree(out,100,100000,100000);raise AssertionError('Socket admitted without live-IPC option')
  except ValueError:cases.append('sockets-default-to-refusal')
  observed=module.tree(out,100,100000,100000,True,allow_owned_ipc=True);entry=observed['tmp/pymp-abcdefgh/listener-12345678'];assert entry['kind']=='owned-temporary-manager-socket';cases.append('known-owned-manager-socket-metadata-retained')
  (out/'OBSERVED_SOCKET.json').write_text(json.dumps(entry,indent=2),encoding='utf8')
  unexpected=out/'unexpected.sock';extra=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);sockets.append((extra,unexpected))
  # Bind through another directory descriptor to keep even this intentional
  # negative fixture beneath the same test-owned root with a short address.
  root_fd=os.open(out,os.O_RDONLY|os.O_DIRECTORY)
  try:extra.bind('/proc/'+str(os.getpid())+'/fd/'+str(root_fd)+'/unexpected.sock')
  finally:os.close(root_fd)
  try:module.tree(out,100,100000,100000,True,allow_owned_ipc=True);raise AssertionError('Arbitrary socket admitted')
  except ValueError:cases.append('unrecognized-socket-still-refused')
  extra.close();unexpected.unlink();sockets.pop()
  try:module.tree(out,1,100000,100000,True,allow_owned_ipc=True);raise AssertionError('Socket entry capacity bypassed')
  except ValueError:cases.append('all-directory-and-socket-entries-count-toward-capacity')
  result=dict(status='PASS_REAL_SHORT_OWNED_TEMPORARY_REFERENCE_AND_NARROW_MANAGER_IPC_CONTROLS',controls=len(cases),cases=cases,observed_socket=entry,scientific_analysis_executed=False,new_tool_credit=0);(out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_OWNED_IPC_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_OWNED_IPC_CONTROL',cause=repr(error),traceback=traceback.format_exc(),cases=cases,required_action='Preserve this test and complete console. Dependent scVelo execution requires Linux/WSL with permitted procfs references and Unix sockets in its owned output filesystem. Use a permitted suitable NEW output directory; never redirect to an unmonitored global scratch directory.')
  (out/'FAILURE.json').write_text(json.dumps(result,indent=2),encoding='utf8');print(result['status'],repr(error),flush=True);return code
 finally:
  for peer,path in sockets:
   peer.close()
   if path.exists():path.unlink()
  if descriptor is not None:os.close(descriptor)
if __name__=='__main__':raise SystemExit(main())
