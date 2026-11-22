"""Bind catalog admission to actual unchanged workflow/catalog owners and current source audits."""
from pathlib import Path
import json,re,subprocess
from qtltools_catalog import primary as p,model as m

ROOT=p.ROOT
BASE='3ae29f5d622e523039288e54b98d73785e302c06'
CATALOG_HEAD='3408fdcf476fb9c39b83eb30d00bda97262d873b'
FOLDER='docs/literature/qtltools-catalog-admission'
CONTROLS='docs/literature/qtltools-catalog-controls'
PROOF=m.DATA+'/qtltools-catalog-validation.json'
COMPAT=m.DATA+'/qtltools-admission-shared-source-compatibility.json'
OWN=['tools/literature/qtltools_catalog/'+n for n in ['__init__.py','decisions.py','primary.py','audit.py','model.py','evidence.py','catalog.py','test_model.py','test_transaction.py','ADMISSION.md']]+['tools/literature/audit_qtltools_source_roles.py','tools/literature/close_qtltools_catalog.py','tools/literature/check_qtltools_catalog_ui.cjs',m.GUIDE]
require=p.require

def git(root,*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=root,stdin=subprocess.DEVNULL)

def sources():return {n:p.pin((ROOT/n).read_bytes()) for n in OWN}

def component_qualification(checks):
    checks=Path(checks).resolve();require(checks.is_relative_to(ROOT/'build'),'Use current-owner build evidence')
    workflow=ROOT.parent/'qt-3';catalog=ROOT.parent/'mo-9'
    for old,head in [(workflow,BASE),(catalog,CATALOG_HEAD)]:
        require(old.resolve()!=ROOT.resolve() and git(old,'rev-parse','HEAD').decode().strip()==head,'Use actual separate immutable predecessor owner')
        require(not git(old,'status','--porcelain=v1','-z','--untracked-files=all'),'Preserve the clean actual predecessor')
    foundation={}
    for name in m.META:
        raw=git(ROOT,'show',BASE+':'+name);require(raw==(catalog/name).read_bytes(),'Catalog predecessor and baseline metadata differ');foundation[name]=p.pin(raw)
    require(not git(ROOT,'diff',BASE,'--','src/main','src/test','build.gradle','settings.gradle','gradle.properties'),'Catalog admission must preserve qualified scientific/build source')
    require(not git(ROOT,'ls-files','--others','--exclude-standard','--','src/main','src/test'),'Unexpected untracked scientific source')
    folder='docs/literature/qtltools-workflow-development';profile=json.loads((workflow/folder/'PROFILE.json').read_bytes());require((ROOT/folder/'PROFILE.json').read_bytes()==(workflow/folder/'PROFILE.json').read_bytes(),'Changed immutable workflow profile')
    source_inputs={}
    for name,wanted in profile['source_inputs'].items():
        if name.startswith(('src/','tools/literature/qtltools_workflow/')):
            require(p.pin((ROOT/name).read_bytes())==p.pin((workflow/name).read_bytes())==wanted,'Changed qualified component source: '+name);source_inputs[name]=wanted
    for pattern in ['src/main/java/org/jlinalg/genetics/Qtltools*.java','src/main/java/org/jlinalg/genetics/GenomicIntervals.java']:
        for path in ROOT.glob(pattern):
            name=path.relative_to(ROOT).as_posix();raw=path.read_bytes();require(raw==(workflow/name).read_bytes()==git(ROOT,'show',BASE+':'+name),'Changed compiled native scientific source');source_inputs[name]=p.pin(raw)
    jar=workflow/'build/qtltools-workflow-1/normal-gradle-5/project-build/cli/omicsmatic-0.4.0.jar';require(p.pin(jar.read_bytes())==profile['producer'],'Changed actual normal producer')
    readers={};reader_files={}
    for label,old in [('workflow',workflow),('catalog',catalog)]:
        directory=checks/'predecessor-readers'/label;start=json.loads((directory/'STARTED.json').read_bytes());require(Path(start['actual_owner']).resolve()==old.resolve(),'Reader used a different predecessor owner')
        require(json.loads((directory/'PROCESS.json').read_bytes())['exit_code']==0,'Actual immutable predecessor reader failed')
        readers[label]=json.loads((directory/'RESULT.json').read_bytes())
        for name in ['STARTED.json','PROCESS.json','stdout','stderr','RESULT.json']:reader_files[(directory/name).relative_to(checks).as_posix()]=p.pin((directory/name).read_bytes())
    wr=readers['workflow'];require(wr['qualified_JAR_verified'] and wr['tests_each_JVM']==680 and wr['actual_operations']==243 and wr['complete_original_files_compared']==324 and wr['actual_replays']==138 and not wr['scientific_models_executed'],'Different saved workflow qualification')
    cr=readers['catalog'];require(cr['result']['integrations']==145 and cr['result']['reviewed_uses']==1630 and cr['current_manifest']['entries']==841,'Different actual preceding catalog qualification')
    source_hosts={}
    for host in ['windows','linux']:
        directory=checks/('fresh-source-'+host+'-1');require(json.loads((directory/'PROCESS.json').read_bytes())['exit_code']==0,'Fresh XML/PDF source audit failed')
        result=json.loads((directory/'result/RESULT.json').read_bytes());require(result['status']=='PASS_FRESH250_QTLTOOLS9_XML_AND_P190_SUPPLEMENT' and result['census']==p.pin(p.encoded(p.frozen())),'Different actual fresh source scope')
        start=json.loads((directory/'STARTED.json').read_bytes())
        for name,wanted in start['source_inputs'].items():require(p.pin((ROOT/name).read_bytes())==wanted,'Fresh source-audit implementation changed')
        source_hosts[host]=result
        for name in ['STARTED.json','PROCESS.json','stdout','stderr','result/RESULT.json']:reader_files[(directory/name).relative_to(checks).as_posix()]=p.pin((directory/name).read_bytes())
    require(source_hosts['windows']==source_hosts['linux'],'Cross-host source audits disagree')
    result=dict(status='PASS_CURRENT_QTLTOOLS_COMPONENTS_AND_SOURCE_FOR_CATALOG_ADMISSION',producer=profile['producer'],tests_each_JVM=680,JVMs=3,actual_operations=243,completed_operations=237,retained_original_failures=6,complete_direct_original_files=324,actual_replays=138,source_hosts=2,source_roles=9,XML_pointers=19,PDF_pages=2,new_catalog_credit=0,scientific_models_refit_in_admission=False,whole_goal_complete=False)
    return dict(result=result,foundation=foundation,source_inputs=source_inputs,qualified_main_source_tree=git(ROOT,'rev-parse',BASE+':src/main').decode().strip(),workflow_owner_head=BASE,catalog_owner_head=CATALOG_HEAD,profile=p.pin((workflow/folder/'PROFILE.json').read_bytes()),actual_readers=readers,source_hosts=source_hosts,actual_receipts=reader_files)

def control_evidence():
    from qtltools_catalog.catalog import read_control_archive
    folder=ROOT/CONTROLS;record=json.loads((folder/'CONTROL-CHECKS.json').read_bytes());members=read_control_archive(folder/'checks.zip',record['members'])
    require(record['status']=='PASS_QTLTOOLS_CATALOG_MODEL_TRANSACTION_AND_SOURCE_REFUSALS','Different admission-control scope')
    for host in ['windows','linux']:
        result=json.loads(members[host+'/RESULT.json']);require(result['model_transaction_tests']==record['tests_each_host'] and result['all_source_refusals_passed'] and result['source_refusals']==4,'Failed/missing actual admission controls')
        unit=host+'/model-transaction/';require(json.loads(members[unit+'PROCESS.json'])['exit_code']==0,'Model/transaction process failed')
        transcript=members[unit+'stderr'].decode().replace('\r\n','\n');matched=re.search(r'Ran (\d+) tests',transcript)
        require(matched is not None and int(matched[1])==record['tests_each_host'] and '\nOK\n' in transcript and 'FAILED' not in transcript and 'skipped=' not in transcript,'Model/transaction transcript is incomplete or failed')
        expected={'missing-xml':(3,'SKIP3'),'changed-xml':(2,'Primary XML identity differs'),'missing-pdf':(3,'SKIP3'),'changed-pdf':(2,'Changed P190 publisher supplement')}
        require(set(result['source_refusal_results'])==set(expected),'Different actual source-refusal population')
        for name,(code,reason) in expected.items():
            prefix=host+'/'+name+'/';process=json.loads(members[prefix+'PROCESS.json']);failure=json.loads(members[prefix+'result/FAILURE.json']);reported=result['source_refusal_results'][name]
            require(process['exit_code']==reported['exit_code']==code and failure['status']==reported['status'] and failure['error']==reported['error'] and reason in failure['status']+' '+failure['error'],'Source refusal did not hit the intended guard')
    require(record['source_code']==sources(),'Admission control source changed')
    return dict(record=p.pin((folder/'CONTROL-CHECKS.json').read_bytes()),archive=p.pin((folder/'checks.zip').read_bytes()),tests_each_host=record['tests_each_host'],source_refusals_each_host=4)
