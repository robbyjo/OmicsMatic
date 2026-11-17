#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently audit source policy records in Java, including fully resealed contradictions."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,time,uuid
if not __debug__:raise RuntimeError('Qualification assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3];HERE=Path(__file__).resolve().parent;sys.path.insert(0,str(HERE))
from adopt_deployment import verify_source_records
from setup_runtime import pin,write

def native(text):
    if sys.platform=='win32' and text.startswith('/mnt/') and len(text)>7 and text[6]=='/':return Path(text[5].upper()+':/'+text[7:])
    return Path(text)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--compiled',type=Path,required=True);p.add_argument('--source-controls',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-qualifier.py');source=a.source_controls.resolve();verify_source_records(source,source/'EXPORTED.json');complete=json.loads((source/'COMPLETED.json').read_bytes());assert complete['status']=='PASS_TWELVE_ORIGINAL_SPAWNED_PROCESS_INSTALLATION_POLICY_CONTROLS_NOT_JAVA_ADAPTER' and len(complete['cases'])==12
    compiled=json.loads((a.compiled/'COMPILED.json').read_bytes());jar=native(compiled['baseline_jar']['path']);assert pin(jar)=={k:compiled['baseline_jar'][k] for k in ['bytes','sha256']};classes=a.compiled/'classes'
    for name,expected in compiled['classes'].items():assert pin(classes/name)==expected
    java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' if sys.platform=='win32' else '/usr/bin/java';command=[java,'-cp',str(classes)+os.pathsep+str(jar),compiled['probe'],'installation-policy'];rows=[];specifications={};python=json.loads((source/'SOURCE.json').read_bytes())['engine_python']
    def run(name,request,positive,expected_blocked=None):
        case=out/name;case.mkdir(exist_ok=False);path=case/'REQUEST.json';write(path,request);start=time.monotonic()
        with (case/'stdout').open('xb') as so,(case/'stderr').open('xb') as se:r=subprocess.run([*command,str(path)],stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=90)
        value=dict(name=name,command=[*command,str(path)],exit_code=r.returncode,expected_success=positive,seconds=round(time.monotonic()-start,3));write(case/'PROCESS.json',value);assert (r.returncode==0)==positive,(name,'Inspect retained Java diagnostics')
        if positive:
            audit=json.loads((case/'stdout').read_bytes())['audit'];assert audit['originalFailures']==0
            if expected_blocked is not None:assert audit['blockedCalls']==expected_blocked
            value.update(processes=len(audit['processes']),spawns=audit['spawnCalls'],blocked=audit['blockedCalls'])
        else:
            first=(case/'stderr').read_text(encoding='utf-8',errors='replace').splitlines()[0];assert 'java.io.IOException:' in first;value['rejection']=first
        rows.append(value);print(name+' PASS_EXPECTED_'+str(r.returncode),flush=True)
    for row in complete['cases']:
        case=source/'cases'/row['case'];controller=json.loads((case/'CONTROLLER.json').read_bytes());env=json.loads((case/'ENVIRONMENT.json').read_bytes());request=json.loads((case/'COMMAND.json').read_bytes());argv=request['argv'];data=dict(records=str(case/'policy-records'),configuration=str(case/'INSTALLATION-POLICY.json'),controller_pid=controller['controller_pid'],owned_processes=[v['pid'] for v in controller['policy_processes']],run_id=env['OMICS_SNAKEMAKE_RUN_ID'],allow_install=row['scenario']=='install-permitted',python=python,conda_prefix=argv[argv.index('--conda-prefix')+1],image_prefix=argv[argv.index('--apptainer-prefix')+1]);specifications[row['case']]=data;run(row['case'],data,True,1 if row['exit_code']==3 else 0)
    base=specifications['child-missing-forbidden-conda-apptainer'];original=source/'cases/child-missing-forbidden-conda-apptainer'
    for name in ['valid-resealed-copy','missing-child','missing-opening','unlisted-record','relabelled-permission','relabelled-run','different-python','different-source','suppressed-skip','blocked-original-called','missing-recovery','changed-image-url','changed-conda-marker','changed-spawn','unowned-child','unclosed-policy','relabelled-local-image','impossible-external-success']:
        copy=out/('copy-'+name);copy.mkdir();shutil.copytree(original/'policy-records',copy/'policy-records');shutil.copyfile(original/'INSTALLATION-POLICY.json',copy/'INSTALLATION-POLICY.json');records=copy/'policy-records';config_file=copy/'INSTALLATION-POLICY.json';config=json.loads(config_file.read_bytes());closed={int(p.name[7:-5]):json.loads(p.read_bytes()) for p in records.glob('policy-*.json') if p.name.count('.')==1};child=next(pid for pid in closed if pid!=base['controller_pid']);parent=closed[base['controller_pid']];denied=next(r for r in closed[child]['records'] if r['blocked']);image=next(r for r in parent['records'] if r['kind']=='container-image');data=dict(base,records=str(records),configuration=str(config_file))
        if name=='relabelled-permission':
            config['allow_install']=True
            for value in closed.values():
                value['allow_install']=True;value['blocked']=False
                for decision in value['records']:
                    decision['allow_install']=True;decision['blocked']=False;decision['upstream_method_called']=True;decision['upstream_returned']=True;decision.pop('required_action',None)
        elif name=='relabelled-run':config['run_id']=str(uuid.uuid4())
        elif name=='different-python':
            for value in closed.values():value['executable']='/different/python'
        elif name=='different-source':denied['upstream_file_sha256']='0'*64
        elif name=='suppressed-skip':closed[child]['blocked']=False
        elif name=='blocked-original-called':denied['upstream_method_called']=True
        elif name=='missing-recovery':denied.pop('required_action')
        elif name=='changed-image-url':image['url']='docker://example.invalid/different@sha256:'+'a'*64
        elif name=='relabelled-local-image':image['local']=True
        elif name=='impossible-external-success':
            decision=next(r for r in parent['records'] if r['kind']=='conda-environment');decision.update(externally_managed=True,embedded_container_environment=False,address=None,setup_done_flag=None,container_url=None)
        elif name=='changed-conda-marker':denied['setup_done_flag']+='.different'
        elif name=='changed-spawn':parent['spawn_arguments'][0]['propagated_arguments']+=' --dry-run'
        elif name=='unowned-child':data['owned_processes']=[base['controller_pid']]
        elif name=='unclosed-policy':closed[child]['closed']=False
        config_file.write_text(json.dumps(config,sort_keys=True,indent=2)+'\n');config_pin=pin(config_file)
        for pid,value in closed.items():
            value['config']=config_pin;value['run_id']=config['run_id'];(records/('policy-'+str(pid)+'.json')).write_text(json.dumps(value,sort_keys=True,indent=2)+'\n');opened=records/('policy-'+str(pid)+'.OPENED.json');header=json.loads(opened.read_bytes())
            for field in ['config','run_id','allow_install','executable']:header[field]=value[field]
            opened.write_text(json.dumps(header,sort_keys=True,indent=2)+'\n')
        if name in ['missing-child','missing-opening']:
            (records/('policy-'+str(child)+'.OPENED.json')).unlink()
            if name=='missing-child':(records/('policy-'+str(child)+'.json')).unlink()
        elif name=='unlisted-record':(records/'unexpected.txt').write_text('extra')
        write(copy/'COPY-INDEX.json',dict(schema_version=1,files={p.relative_to(copy).as_posix():pin(p) for p in copy.rglob('*') if p.is_file()}));run(name,data,name=='valid-resealed-copy',1 if name=='valid-resealed-copy' else None)
    verify_source_records(source,source/'EXPORTED.json');assert pin(jar)=={k:compiled['baseline_jar'][k] for k in ['bytes','sha256']}
    for name,expected in compiled['classes'].items():assert pin(classes/name)==expected
    write(out/'COMPLETED.json',dict(schema_version=1,status='PASS_PORTABLE_JAVA_PROPAGATED_POLICY_SOURCE_AND_RESEALED_CONTRADICTIONS',cases=rows,source_index=pin(source/'EXPORTED.json'),compiled=pin(a.compiled/'COMPILED.json'),source_cases=12,valid_resealed_copies=1,rejected_contradictions=17,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False))
if __name__=='__main__':main()
