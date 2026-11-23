from pathlib import Path
import subprocess,sys
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';source=R/'build/check_mrpresso_saved_controls_20261123_166.py'
text=source.read_text().replace("suite=B/'workflow-original-references-1/windows21'","suite=B/'workflow-original-references-2/windows21'")
old=''' command=[java,'-Xmx512m','-jar',str(jar),'mrpresso-tools','--verify',str(root)]
 p=subprocess.run(command,cwd=T,capture_output=True,timeout=60);(D/(label+'.stdout')).write_bytes(p.stdout);(D/(label+'.stderr')).write_bytes(p.stderr)
 row=dict(case=label,exit_code=p.returncode,expected=expected,matched=p.returncode==expected);records.append(row);save(D/'PROGRESS.json',records);print(json.dumps(row),flush=True)'''
new=''' for host in ('windows21','windows25','linux21'):
  if host=='linux21':
   address=Path(root).as_posix();address='/mnt/'+address[0].lower()+address[2:]
   command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx512m','-jar','/var/tmp/omicsmatic-mrpresso-qualification-20261123-168/omicsmatic-0.4.0.jar','mrpresso-tools','--verify',address]
  else:
   executable=java if host=='windows21' else 'C:/Program Files/Java/jdk-25/bin/java.exe'
   command=[executable,'-Xmx512m','-jar',str(jar),'mrpresso-tools','--verify',str(root)]
  with (D/(label+'.'+host+'.stdout')).open('xb') as out,(D/(label+'.'+host+'.stderr')).open('xb') as err:
   p=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);save(D/(label+'.'+host+'.PROCESS.json'),dict(pid=p.pid,command=command));code=p.wait()
  row=dict(case=label,host=host,exit_code=code,expected=expected,matched=code==expected);records.append(row);save(D/'PROGRESS.json',records)
  print(json.dumps(row),flush=True)'''
assert old in text;text=text.replace(old,new)
script=R/'build/check_mrpresso_final_controls_20261123_169.py';script.write_text(text,encoding='utf-8')
raise SystemExit(subprocess.call([sys.executable,'-X','utf8','-B',str(script),'2','4'],cwd=T))
