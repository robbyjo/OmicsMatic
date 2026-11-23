from pathlib import Path
import copy,json,shutil,subprocess,time
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'handover-packaged-1';D.mkdir()
jar=B/'normal-build-2/project-build/cli/omicsmatic-0.4.0.jar'
assert json.loads((B/'normal-build-2/RESULT.json').read_bytes())['exit_code']==0
template=json.loads((B/'packaged-smoke-1/request.json').read_bytes())
table=(B/'packaged-smoke-1/associations.tsv').read_text()
hosts={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
       'windows25':['C:/Program Files/Java/jdk-25/bin/java.exe'],
       'linux21':['wsl.exe','--distribution','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java']}
records=[]
def address(path,host):
    value=Path(path).as_posix()
    return '/mnt/'+value[0].lower()+value[2:] if host=='linux21' else value
def run(host,label,args,expected):
    out=D/host;out.mkdir(exist_ok=True)
    command=[*hosts[host],'-jar',address(jar,host),'mrpresso-tools',*args]
    started=time.monotonic();result=subprocess.run(command,cwd=T,capture_output=True,timeout=90)
    (out/(label+'.stdout')).write_bytes(result.stdout);(out/(label+'.stderr')).write_bytes(result.stderr)
    row=dict(host=host,case=label,command=command,exit_code=result.returncode,expected=expected,seconds=time.monotonic()-started)
    records.append(row);save(D/'PROGRESS.json',records);print(json.dumps({k:row[k] for k in ('host','case','exit_code','expected')}),flush=True)
    assert result.returncode==expected,(row,result.stderr.decode(errors='replace'))
def reseal(root):
    manifest=root/'capture-files.json';saved=json.loads(manifest.read_bytes())
    for name in saved['files']:saved['files'][name]=pin(root/name)
    save(manifest,saved);marker=json.loads((root/'COMPLETED.json').read_bytes());marker['seal_identity']=pin(manifest);save(root/'COMPLETED.json',marker)
for host in hosts:
    out=D/host;out.mkdir()
    for tiny in (False,True):
        name='tiny' if tiny else 'normal';text=table
        if tiny:
            lines=text.splitlines();header=lines[0].split('\t');output=[lines[0]]
            for line in lines[1:]:
                cells=line.split('\t')
                for key in ('Y','SY'):cells[header.index(key)]=format(float(cells[header.index(key)])*1e-20,'.17g')
                output.append('\t'.join(cells))
            text='\n'.join(output)+'\n'
        path=out/(name+'.tsv');path.write_text(text,encoding='utf-8')
        request=copy.deepcopy(template);request['out']=address(out/name,host);request['inputs']['associations']=address(path,host)
        path=out/(name+'.json');save(path,request)
        run(host,name+'-run',['--request',address(path,host)],0)
        run(host,name+'-verify',['--verify',address(out/name,host)],0)
    path=out/'replay.json';save(path,dict(schema_version=1,mode='REPLAY',analysis=address(out/'normal',host),out=address(out/'replayed',host)))
    run(host,'replay',['--request',address(path,host)],0)
    run(host,'replay-verify',['--verify',address(out/'replayed',host)],0)
    bad=out/'resealed-inflated-tiny';shutil.copytree(out/'tiny',bad)
    path=bad/'native-result.json';value=json.loads(path.read_bytes())
    for name in ('raw_mr','corrected_mr'):
        for key in ('beta','standard_error'):
            for cell in value[name][key]:
                assert cell['kind']=='FINITE' and 0<abs(cell['value'])<1e-18
                cell['value']*=1_000_000
    save(path,value)
    path=bad/'mr-results.tsv';lines=path.read_text().splitlines();modified=[lines[0]]
    for line in lines[1:]:
        values=line.split('\t')
        for j in (2,3):values[j]=str(float(values[j])*1_000_000)
        modified.append('\t'.join(values))
    path.write_text('\n'.join(modified)+'\n',encoding='utf-8');reseal(bad)
    run(host,'inflated-tiny-refusal',['--verify',address(bad,host)],2)
    assert b'Scientific numeric contradiction' in (out/'inflated-tiny-refusal.stderr').read_bytes()
    bad=out/'resealed-table-lie';shutil.copytree(out/'normal',bad)
    path=bad/'mr-results.tsv';text=path.read_text();assert '\tRaw\t' in text
    path.write_text(text.replace('\tRaw\t','\tFalse-label\t'),encoding='utf-8');reseal(bad)
    run(host,'table-lie-refusal',['--verify',address(bad,host)],2)
    assert b'Result table contradicts typed source-profile result' in (out/'table-lie-refusal.stderr').read_bytes()
for host in ('windows25','linux21'):
    run(host,'verify-windows21-normal',['--verify',address(D/'windows21/normal',host)],0)
    run(host,'verify-windows21-replay',['--verify',address(D/'windows21/replayed',host)],0)
    run(host,'refuse-windows21-inflated-tiny',['--verify',address(D/'windows21/resealed-inflated-tiny',host)],2)
save(D/'RESULT.json',dict(status='PASS',cases=records,producer=pin(jar),scoped_handover_smoke_only=True,
    fixed_absolute_tolerance_false_acceptance_reproduced_on_new_producer=False,
    full_workflow_qualified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
