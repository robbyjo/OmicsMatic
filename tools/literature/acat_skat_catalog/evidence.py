"""Bind admission to immutable native qualification and actual fresh source/control runs."""
from pathlib import Path
import json,re,subprocess
from acat_skat_catalog import primary as p,model as m
ROOT=p.ROOT;BASE='f66c8e5e082d9a0f4fbc4c8bc22df705f325867a'
FOLDER='docs/literature/acat-skat-catalog-admission';CONTROLS='docs/literature/acat-skat-catalog-controls-v2'
PROOF=m.DATA+'/acat-skat-catalog-validation.json';COMPAT=m.DATA+'/acat-skat-admission-shared-source-compatibility.json'
OWN=['tools/literature/acat_skat_catalog/'+n for n in ['__init__.py','decisions.py','primary.py','audit.py','model.py','evidence.py','catalog.py','test_model.py','test_transaction.py','ADMISSION.md']]+['tools/literature/audit_acat_skat_source_roles.py','tools/literature/close_acat_skat_catalog.py','tools/literature/check_acat_skat_catalog_ui.cjs',m.GUIDE]
require=p.require
def git(root,*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=root,stdin=subprocess.DEVNULL)
def sources():return {n:p.pin((ROOT/n).read_bytes()) for n in OWN}

def component_qualification(checks):
    checks=Path(checks).resolve();require(checks.is_relative_to(ROOT/'build'),'Use current-owner build checks');native=ROOT.parent/'sa-1'
    require(native.resolve()!=ROOT.resolve() and git(native,'rev-parse','HEAD').decode().strip()==BASE and not git(native,'status','--porcelain=v1','-z','--untracked-files=all'),'Use actual clean immutable ACAT owner')
    require(not git(ROOT,'diff',BASE,'--','src/main','src/test','build.gradle','settings.gradle','gradle.properties') and not git(ROOT,'ls-files','--others','--exclude-standard','--','src/main','src/test'),'Scientific/build source must remain unchanged')
    profile='docs/literature/acat091-development/QUALIFICATION.json';require((ROOT/profile).read_bytes()==(native/profile).read_bytes()==git(ROOT,'show',BASE+':'+profile),'Changed native qualification profile');q=json.loads((native/profile).read_bytes())
    require(q['normal_build']['tests']==q['packaged']['tests_each_JVM']==500 and q['normal_build']['failures']==0 and set(q['packaged']['hosts'])=={'windows21','windows25','linux21'} and all(r['exit_code']==0 for r in q['packaged']['hosts'].values()),'Missing native build/packaged qualifications')
    inputs={n:w for n,w in q['public_inputs'].items() if n.startswith(('src/','tools/literature/acat091/'))}
    for name,wanted in inputs.items():require(p.pin((ROOT/name).read_bytes())==p.pin((native/name).read_bytes())==wanted,'Changed actual qualified source: '+name)
    jar=native/'build/skat-acat-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar';require(p.pin(jar.read_bytes())==q['producer'],'Changed actual qualified native JAR')
    directory=checks/'native-predecessor-reader';start=json.loads((directory/'STARTED.json').read_bytes());require(Path(start['actual_owner']).resolve()==native.resolve(),'Reader used a different native owner')
    require(json.loads((directory/'PROCESS.json').read_bytes())['exit_code']==0 and b'PASS frozen ACAT component' in (directory/'stdout').read_bytes(),'Actual immutable native checkpoint reader failed')
    receipts={(directory/n).relative_to(checks).as_posix():p.pin((directory/n).read_bytes()) for n in ['STARTED.json','PROCESS.json','stdout','stderr']}
    hosts={}
    for host in ['windows','linux']:
        directory=checks/('fresh-source-'+host+'-3');require(json.loads((directory/'PROCESS.json').read_bytes())['exit_code']==0,'Fresh source audit failed')
        result=json.loads((directory/'result/RESULT.json').read_bytes());require(result['status']=='PASS_FRESH250_ACAT_SKAT22_XML_AND_P190_SUPPLEMENT' and result['census']==p.pin(p.encoded(p.frozen())),'Different fresh source review scope');hosts[host]=result
        for name,wanted in json.loads((directory/'STARTED.json').read_bytes())['source_inputs'].items():require(p.pin((ROOT/name).read_bytes())==wanted,'Source audit code changed')
        for name in ['STARTED.json','PROCESS.json','stdout','stderr','result/RESULT.json']:receipts[(directory/name).relative_to(checks).as_posix()]=p.pin((directory/name).read_bytes())
    require(hosts['windows']==hosts['linux'],'Fresh cross-host source audits disagree')
    result=dict(status='PASS_CURRENT_NATIVE_ACAT091_AND_SOURCE_ROLES_FOR_ADMISSION',producer=q['producer'],tests_each_JVM=500,JVMs=3,source_hosts=2,source_roles=22,original_matrix_cases=274,original_R_sum_mean_pairs=192,new_catalog_credit=0,scientific_models_refit_in_admission=False,whole_goal_complete=False)
    return dict(result=result,source_inputs=inputs,native_owner_head=BASE,profile=p.pin((native/profile).read_bytes()),source_hosts=hosts,actual_receipts=receipts)

def control_evidence():
    from qtltools_catalog.catalog import read_control_archive
    folder=ROOT/CONTROLS;record=json.loads((folder/'CONTROL-CHECKS.json').read_bytes());members=read_control_archive(folder/'checks.zip',record['members'])
    require(record['status']=='PASS_ACAT_SKAT_CATALOG_MODEL_TRANSACTION_AND_SOURCE_REFUSALS' and record['source_code']==sources(),'Missing/changed admission control evidence')
    for host in ['windows','linux']:
        result=json.loads(members[host+'/RESULT.json']);require(result['model_transaction_tests']==record['tests_each_host'] and result['all_source_refusals_passed'] and result['source_refusals']==4,'Incomplete actual admission controls')
        prefix=host+'/model-transaction/';require(json.loads(members[prefix+'PROCESS.json'])['exit_code']==0,'Model/transaction test process failed');transcript=members[prefix+'stderr'].decode().replace('\r\n','\n');match=re.search(r'Ran (\d+) tests',transcript)
        require(match is not None and int(match[1])==record['tests_each_host'] and '\nOK\n' in transcript and 'FAILED' not in transcript and 'skipped=' not in transcript,'Incomplete or failed control transcript')
        for name,code,reason in [('missing-xml',3,'SKIP3'),('changed-xml',2,'Primary XML identity differs'),('missing-pdf',3,'SKIP3'),('changed-pdf',2,'Changed P190 publisher supplement')]:
            prefix=host+'/'+name+'/';process=json.loads(members[prefix+'PROCESS.json']);failure=json.loads(members[prefix+'result/FAILURE.json'])
            require(process['exit_code']==code and reason in failure['status']+' '+failure['error'],'Source refusal hit a different cause')
    return dict(record=p.pin((folder/'CONTROL-CHECKS.json').read_bytes()),archive=p.pin((folder/'checks.zip').read_bytes()),tests_each_host=record['tests_each_host'],source_refusals_each_host=4)
