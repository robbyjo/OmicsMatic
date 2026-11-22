"""Bind Monocle admission to the actual preceding owner and current original/native controls."""
from pathlib import Path
import hashlib,json,os,subprocess
from monocle_catalog import primary as p,model as m

ROOT=Path(__file__).resolve().parents[3]
BASE='67a3b45b86c93eab12216b257a9ab9b399d329b9'
FOLDER='docs/literature/monocle-catalog-admission'
PROOF=m.DATA+'/monocle-components-admission-validation.json'
COMPAT=m.DATA+'/monocle-admission-reviews-shared-source-compatibility.json'
CONTROLS='docs/literature/monocle-catalog-controls'
OWN=['tools/literature/monocle_catalog/'+name for name in ['__init__.py','decisions.py','primary.py','audit.py','model.py','evidence.py','catalog.py','controls.py','test_model.py','test_transaction.py','ADMISSION.md']]
OWN+=['tools/literature/close_monocle_catalog.py','tools/literature/audit_monocle_source_roles.py','tools/literature/check_monocle_catalog_controls.py','tools/literature/check_monocle_catalog_ui.cjs',m.GUIDE]
require=p.require

def git(root,*args):return subprocess.check_output([os.environ.get('MONOCLE_CATALOG_GIT','git'),'--no-replace-objects',*args],cwd=root)

def sources():return {name:p.pin((ROOT/name).read_bytes()) for name in OWN}

def component_qualification(checks):
    checks=Path(checks).resolve();old=ROOT.parent/'mo-7';require(old.resolve()!=ROOT.resolve() and git(old,'rev-parse','HEAD').decode().strip()==BASE,'Use actual separate P99 predecessor owner')
    require(not git(old,'status','--porcelain=v1','-z','--untracked-files=all'),'Preserve the actual clean qualified predecessor')
    profile_path='docs/literature/monocle-response-checkpoint/PROFILE.json';profile=json.loads((old/profile_path).read_bytes());require((ROOT/profile_path).read_bytes()==(old/profile_path).read_bytes(),'Changed retained P99 profile')
    require(profile['status']=='PASS_NATIVE_MONOCLE_RESPONSES_AND_EXACT_GLOBAL_STAGES' and profile['new_catalog_credit']==0 and not profile['whole_goal_complete'],'Different prior component scope')
    checked={profile_path:p.pin((ROOT/profile_path).read_bytes())}
    for name,wanted in profile['source_inputs'].items():
        if name.startswith('src/main/'):
            require(p.pin((ROOT/name).read_bytes())==wanted,'Changed qualified native/graph source: '+name);checked[name]=wanted
    command_folder='docs/literature/monocle-command-development';commands=json.loads((ROOT/command_folder/'VALIDATION.json').read_bytes());require(commands['status']=='PASS_SOURCE_BUILD_MONOCLE_EXACT_EDITION_ROUTES_AND_BUNDLED_NUMERICAL_IDENTITY','Different current command qualification')
    for name in ['src/main/java/org/jlinalg/cli/Monocle131ModelCli.java','src/main/java/org/jlinalg/cli/Monocle133GraphCli.java','src/main/java/org/jlinalg/cli/Monocle137WorkflowCli.java','src/main/java/org/jlinalg/cli/MonocleMathIdentity.java','src/main/resources/org/jlinalg/omics/monocle-commands/jdistlib-0.10.2-classes.json']:
        require(p.pin((ROOT/name).read_bytes())==commands['source_pins'][name],'Changed later-qualified exact-edition source');checked[name]=commands['source_pins'][name]
    transitions={}
    permitted={'src/main/java/org/jlinalg/cli/Monocle131ModelCli.java','src/main/java/org/jlinalg/cli/Monocle133GraphCli.java','src/main/java/org/jlinalg/cli/Monocle137WorkflowCli.java','tools/literature/monocle131_models/build.py'}
    for folder in ['monocle131-models-development','monocle133-graph-development','monocle137-workflow-development']:
        path='docs/literature/'+folder+'/VALIDATION.json';metadata=json.loads((ROOT/path).read_bytes());checked[path]=p.pin((ROOT/path).read_bytes());changes=[]
        for name,wanted in metadata['sources'].items():
            actual=p.pin((ROOT/name).read_bytes())
            if actual!=wanted:
                require(name in permitted and actual==commands['source_pins'][name],'Unqualified scientific source change');changes.append(dict(path=name,before=wanted,after=actual,qualification=command_folder+'/VALIDATION.json'))
            checked[name]=actual
        transitions[folder]=changes
    require(sum(len(rows) for rows in transitions.values())==4,'Different known packaging-guard/source-builder transition')
    # The admission changes metadata only. Bind every Monocle entrypoint/native
    # component and selected worker/profile to the actual compiled P99 ancestor.
    patterns=['src/main/java/org/jlinalg/cli/Monocle*.java','src/main/java/org/jlinalg/singlecell/Monocle*.java','src/main/java/org/jlinalg/stats/Monocle*.java','src/main/resources/org/jlinalg/omics/monocle*/worker.R','src/main/resources/org/jlinalg/omics/monocle*/source-profile.json','src/main/resources/org/jlinalg/omics/monocle*/PROFILE.json']
    for pattern in patterns:
        for path in ROOT.glob(pattern):
            name=path.relative_to(ROOT).as_posix();raw=path.read_bytes();require(raw==git(ROOT,'show',BASE+':'+name),'Changed compiled-ancestor Monocle scientific source: '+name);checked[name]=p.pin(raw)
    folder=checks/'current-original-3';actual=json.loads((folder/'RESULT.json').read_bytes());require(actual['status']=='PASS4_CURRENT_NORMAL_JAR_EXACT_EDITION_ORIGINAL_EXECUTIONS' and actual['producer']==profile['normal_producer'] and actual['scientific_files_compared']==29 and actual['original_R_executed'] and not actual['source_or_old_capture_modified'],'Current normal-JAR original executions required')
    expected={'models':'MODELS-1.3.1','graph':'GRAPH-1.3.3','alignment':'ALIGN-1.3.7','differential':'DIFFERENTIAL-2.26.0'};require(set(actual['results'])==set(expected),'Different exact-edition action set')
    earlier=[commands['current_reader_gene_model_call']['records'][0]]+[row for row in commands['original_profile_calls']['records'] if row['name']!='models']
    reference_maps={row['name']:row['scientific_outputs_byte_identical_to_original_qualified_run'] for row in earlier}
    for name,action in expected.items():
        result=actual['results'][name];require(result['action']==action and result['original_R_executed'] and not result['old_source_or_capture_modified'] and not result['whole_paper_reproduced'],'False current/paper execution scope');require(json.loads((folder/name/'PROCESS.json').read_bytes())['exit_code']==0,'Incomplete actual original process')
        require(result['scientific_outputs_byte_identical_to_retained_original']==reference_maps[name],'Missing/extra/changed original scientific output population')
        for field,wanted in reference_maps[name].items():require(p.pin((folder/name/'capture'/field).read_bytes())==wanted,'Changed actual scientific result')
    require(sum(len(rows) for rows in reference_maps.values())==29,'Different complete reference population')
    for host in ['windows','linux']:
        row=json.loads((checks/('fresh-source-'+host+'-1')/'RESULT.json').read_bytes());require(row['status']=='PASS_FRESH250_MONOCLE9_SOURCE_CENSUS' and (row['papers'],row['roles'],row['pointers'])==(250,9,15) and row['census']==p.pin(p.encoded(p.frozen())),'Fresh Windows/Linux primary census required')
    for label in ['P99','catalog']:require(json.loads((checks/'qualification-readers'/label/'PROCESS.json').read_bytes())['exit_code']==0,'Actual predecessor reader did not pass')
    dispatch=(ROOT/m.PROGRAM).read_text(encoding='utf-8');require('"RESPONSES-1.0.0"' in dispatch and '"MODELS-1.3.1"' in dispatch and '"ALIGN-1.3.7"' in dispatch,'Qualified global action missing')
    result=dict(status='PASS_CURRENT_MONOCLE_COMPONENTS_FOR_CATALOG_ADMISSION',producer=profile['normal_producer'],packaged_tests_per_JVM=889,packaged_JVMs=3,current_exact_edition_original_runs=4,current_scientific_files_byte_identical=29,native_response_cases=6,native_response_values=984,admitted_original_gene_tests=54,retained_degenerate_family_refusals=3,source_papers_per_host=250,source_roles=9,source_hosts=2,global_command_registered=True,new_catalog_credit=0,whole_goal_complete=False)
    return dict(result=result,source_inputs=checked,known_standalone_to_bundled_guard_transitions=transitions,current_original_results=actual,actual_predecessor=dict(path=str(old),head=BASE,clean=True,metadata_views_or_hash_waivers_used=False),source_census=p.pin(p.encoded(p.frozen())))
