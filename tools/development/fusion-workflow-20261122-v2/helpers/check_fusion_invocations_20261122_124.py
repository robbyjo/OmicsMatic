from pathlib import Path
import hashlib,json,shutil,subprocess,time
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';B=F/'invocation-controls-1';B.mkdir();source=F/'workflow-compile-6/resources/org/jlinalg/omics/fusion-9346c122'
def linux(p):s=str(p).replace('\\','/');return '/mnt/'+s[0].lower()+s[2:]
stub=B/'stub';stub.write_bytes(b'#!/bin/sh\nprintf "%s\\000" "$@" > "$STUB_ARGV"\nprintf "%s\\n" "$STUB_STDOUT"\nprintf "%s\\n" "$STUB_STDERR" >&2\nexit "$STUB_EXIT"\n')
p=subprocess.run(['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','chmod','700',linux(stub)],capture_output=True);assert p.returncode==0
banner='Genome-wide Complex Trait Analysis (GCTA)';diagnostic='Error: no analysis has been launched by the option(s)'
cases=[
 ('gcta-real-probe','gcta',F/'gcta-modern-intake-3/gcta64',[],0,None,None),
 ('gcta-wrong-banner','gcta',stub,[],1,dict(code=1,stdout=diagnostic),None),
 ('gcta-wrong-diagnostic','gcta',stub,[],1,dict(code=1,stdout=banner+'\nError: unrelated startup failure'),None),
 ('gcta-wrong-status','gcta',stub,[],2,dict(code=2,stdout=banner+'\n'+diagnostic),None),
 ('gcta-stderr-only','gcta',stub,[],1,dict(code=1,stderr=banner+'\n'+diagnostic),None),
 ('gcta-analysis-forwarding','gcta',stub,['--bfile','file with spaces','','--out','value'],17,dict(code=17),['--bfile','file with spaces','','--out','value']),
 ('gemma-real-help','gemma',F/'binary-intake-1/gemma',['-h'],0,None,None),
 ('gemma-help-status','gemma',stub,['-h'],13,dict(code=13),['-h']),
 ('gemma-analysis-routing','gemma',stub,['-bfile','file with spaces','-o','tmp/train'],19,dict(code=19),['-bfile','file with spaces','-o','tmp/train','-outdir','.']),
 ('gemma-conflicting-output','gemma',stub,['-outdir','another'],2,dict(code=0),None),
]
rows=[]
for name,tool,binary,args,expected,settings,wanted in cases:
 folder=B/name;folder.mkdir();shutil.copyfile(source/(tool+'-entry.sh'),folder/(tool+'-entry.sh'));(folder/(tool+'-program.txt')).write_text(linux(binary)+'\n',encoding='utf-8',newline='\n');settings=settings or {};values=dict(STUB_ARGV=linux(folder/'argv.nul'),STUB_EXIT=str(settings.get('code',0)),STUB_STDOUT=settings.get('stdout',''),STUB_STDERR=settings.get('stderr',''))
 command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(folder),'--','/usr/bin/env',*[k+'='+v for k,v in values.items()],'/bin/sh',linux(folder/(tool+'-entry.sh')),*args]
 with (folder/'stdout').open('xb') as out,(folder/'stderr').open('xb') as err:p=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);code=p.wait()
 assert code==expected,(name,code,expected,(folder/'stderr').read_text(errors='replace'))
 if wanted is not None:assert (folder/'argv.nul').read_bytes()==b''.join(a.encode()+b'\0' for a in wanted),name
 if name=='gcta-real-probe':assert (folder/'gcta-probe.original-exit').read_text().strip()=='1'
 if name=='gemma-conflicting-output':assert not (folder/'argv.nul').exists()
 row=dict(case=name,actual_exit=code,expected_exit=expected,pass_check=True,scientific_fit=False);rows.append(row);print(name,code,flush=True)
(B/'RESULT.json').write_text(json.dumps(dict(status='PASS_EXPLICIT_INVOCATION_CONTROLS',cases=rows,scientific_fits=0),indent=2)+'\n',encoding='utf-8')