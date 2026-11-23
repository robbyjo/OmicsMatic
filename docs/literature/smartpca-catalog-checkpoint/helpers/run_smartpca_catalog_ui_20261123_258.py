from pathlib import Path
import functools,http.server,json,subprocess,sys,threading,time,urllib.request
T=Path('D:/projects/JLinAlg/build/fu-1');R=T.parents[1];B=T/'build/smartpca-catalog-255';D=B/('ui-'+sys.argv[1]);D.mkdir();profile=D/'owned-edge-profile';profile.mkdir();out=D/'browser';out.mkdir();site=Path(sys.argv[2]).resolve();assert site.is_relative_to(T) and site.is_dir()
node=Path('C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/node');edge=Path('C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
def save(path,j):path.write_text(json.dumps(j,indent=2)+'\n',encoding='utf-8')
def rows(label):
 shell='C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe';literal="'"+str(profile).replace("'","''")+"'";code='[Console]::OutputEncoding=[Text.UTF8Encoding]::new(); $ownedProfile='+literal+'; $ownedRows=@(Get-CimInstance Win32_Process -Filter "Name=\'msedge.exe\'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($ownedProfile) } | Select-Object ProcessId,ParentProcessId,CreationDate); [Console]::Out.Write((ConvertTo-Json -InputObject $ownedRows -Compress))'
 p=subprocess.run([shell,'-NoLogo','-NoProfile','-NonInteractive','-Command',code],capture_output=True);assert p.returncode==0,p.stderr;j=json.loads(p.stdout.decode('utf-8-sig'));save(D/(label+'.json'),j);return j
class Handler(http.server.SimpleHTTPRequestHandler):
 def translate_path(self,path):
  candidate=Path(super().translate_path(path)).resolve();assert candidate.is_relative_to(site)
  if candidate.is_file():return str(candidate)
  fallback=(T/'site'/candidate.relative_to(site)).resolve();assert fallback.is_relative_to((T/'site').resolve());return str(fallback)
 def log_message(self,*args):pass
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),functools.partial(Handler,directory=str(site)));thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
args=[str(edge),'--headless=new','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0','--user-data-dir='+str(profile),'--no-first-run','--no-default-browser-check','--disable-extensions','about:blank']
save(D/'BROWSER-START.json',{'command':args,'scope':'NEW unique profile; graceful CDP closure only; no OS process termination'})
with (D/'edge.stdout').open('xb') as log,(D/'edge.stderr').open('xb') as err:
 p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=log,stderr=err)
 try:
  deadline=time.monotonic()+40;port=None
  while time.monotonic()<deadline:
   active=profile/'DevToolsActivePort'
   if active.is_file():
    lines=active.read_text().splitlines()
    if len(lines)>=2 and lines[0].isdigit():
     try:
      with urllib.request.urlopen('http://127.0.0.1:'+lines[0]+'/json/version',timeout=1) as response:version=json.load(response)
      if version.get('webSocketDebuggerUrl','').endswith(lines[1]):port=int(lines[0]);break
     except (OSError,ValueError):pass
   time.sleep(.05)
  assert port,'Own CDP endpoint not available; retain profile and inspect own handle'
  before=rows('own-processes-before');assert before
  command=[str(node/'bin/node.exe'),str(T/'tools/literature/smartpca_catalog/check_ui.cjs'),str(node/'node_modules/playwright'),str(out),'http://127.0.0.1:'+str(server.server_port),'http://127.0.0.1:'+str(port)]
  save(D/'START.json',{'command':command,'site':str(site),'own_browser_launch_pid':p.pid})
  with (D/'stdout').open('xb') as stdout,(D/'stderr').open('xb') as stderr:
   process=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);code=process.wait()
  save(D/'NODE-PROCESS.json',{'exit_code':code});assert code==0,(D/'stderr').read_text(errors='replace')
 finally:
  close=[str(node/'bin/node.exe'),str(R/'build/close_smartpca_owned_browser_20261123_259.cjs'),str(node/'node_modules/playwright'),str(profile)]
  with (D/'close.stdout').open('xb') as stdout,(D/'close.stderr').open('xb') as stderr:
   done=subprocess.Popen(close,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);closed=done.wait()
  save(D/'CLOSE.json',{'exit_code':closed,'scope':'Exact own profile CDP endpoint; no OS process tree kill'});assert closed==0,'Owned CDP closure failed'
  server.shutdown();server.server_close();thread.join()
  remaining=rows('own-processes-after-0');attempt=0;deadline=time.monotonic()+15
  while remaining and time.monotonic()<deadline:
   time.sleep(.2);attempt+=1;remaining=rows('own-processes-after-'+str(attempt))
  assert not remaining,'Own isolated browser remains; inspect recorded exact profile'
  terminal=p.wait();save(D/'LAUNCHER.json',{'exit_code':terminal})
save(D/'COMPLETED.json',{'status':'PASS_SMARTPCA_UI_AND_GRACEFUL_OWN_BROWSER_CLOSURE','page_result':json.loads((out/'COMPLETED.json').read_bytes()),'unique_profile_processes_before':len(before),'unique_profile_processes_after':0,'no_OS_process_termination':True});print(json.dumps({'UI':sys.argv[1],'status':'PASS','profile_processes_after':0}))
