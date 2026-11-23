"""Verify the frozen scoped native MR-PRESSO qualification; no analyses are rerun."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,zipfile,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
PACKET='docs/literature/mrpresso-workflow-qualification'
PRODUCER='af72b8ccaa263eca2aad69ff96e871829981a6475dc598a8c5c5b704dba6f0e6'

def require(condition,message):
    if not condition:raise ValueError(message)
def identity(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def load(raw):
    def pairs(items):
        result={}
        for key,value in items:
            require(key not in result,'Duplicate JSON field: '+key);result[key]=value
        return result
    return json.loads(raw,object_pairs_hook=pairs)

def verify(root=ROOT,jar=None):
    packet=root/PACKET;status=load((packet/'STATUS.json').read_bytes());sources=load((packet/'SOURCES.json').read_bytes());evidence=load((packet/'EVIDENCE.json').read_bytes())
    require(status['status']=='PASS_NATIVE_MRPRESSO_ORIGINAL_PROFILE_SCOPED_WORKFLOW','Not a scoped workflow qualification')
    require(status['native_workflow_qualified'] is True and status['catalog_admitted'] is False,'Changed qualification/admission boundary')
    for key in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete'):require(status[key] is False,'Unsupported full completion claim')
    require(status['new_family_credit']==status['new_reviews']==0,'Workflow qualification cannot award catalog credit')
    require(status['producer']==dict(bytes=198364461,sha256=PRODUCER),'Changed exact producer')
    require(identity((packet/'SOURCES.json').read_bytes())==status['sources_identity'],'Source manifest changed')
    require(identity((packet/'EVIDENCE.json').read_bytes())==status['evidence_manifest_identity'],'Evidence manifest changed')
    for name,expected in sources.items():require(identity((root/name).read_bytes())==expected,'Source differs from frozen checkpoint: '+name)
    if jar is not None:require(identity(Path(jar).read_bytes())==status['producer'],'Different exact producer JAR')
    archive=packet/evidence['archive'];require(identity(archive.read_bytes())==evidence['identity']==status['evidence_identity'],'Evidence archive differs')
    with zipfile.ZipFile(archive) as z:
        names=z.namelist();require(len(names)==len(set(names)) and set(names)==set(evidence['members']),'Changed/duplicate evidence members')
        require(len(names)==status['evidence_members'],'Changed evidence population')
        for name in names:
            path=PurePosixPath(name);require(not path.is_absolute() and '..' not in path.parts and '\\' not in name,'Unsafe archived path')
            require(identity(z.read(name))==evidence['members'][name],'Changed archived bytes: '+name)
        def saved(name):return load(z.read(name))
        build=saved('normal-build-4/RESULT.json');require(build['exit_code']==0 and build['producer']['sha256']==PRODUCER,'Failed/different normal build')
        inputs=saved('normal-build-4/SOURCE-INPUTS.json')
        for name,expected in sources.items():
            if name.startswith('src/'):
                require(inputs.get(name)==expected,'Source not bound to final normal build: '+name)
                require(identity(z.read('normal-build-4/source-context/'+name))==expected,'Different frozen build source: '+name)
        tests={key:0 for key in ('tests','failures','errors','skipped')}
        for name in names:
            if name.startswith('normal-build-4/project-build/test-results/test/') and name.endswith('.xml'):
                suite=ET.fromstring(z.read(name))
                for key in tests:tests[key]+=int(suite.get(key,'0'))
        require(tests==status['normal_build_tests']==dict(tests=797,failures=0,errors=0,skipped=0),'Normal test scope changed')
        component=saved('extended-component-checks-3/RESULT.json');require(component['status']=='PASS' and len(component['cases'])==3 and all(x['exit_code']==0 for x in component['cases']),'Component checks failed')
        original=[]
        for prefix in ('original-reference-windows-1/outputs/','extended-reference-windows-1/outputs/'):
            original += [name for name in names if name.startswith(prefix) and name.endswith('/original.json')]
        require(len(original)==status['original_public_api_calls']==45,'Original reference population changed')
        for name in original:
            record=saved(name);keys=record['names'];values=record['values'];patched=values[keys.index('original_function_patched')]
            require(patched['values']==[False],'Patched original function cannot establish this checkpoint')
        reference=saved('workflow-original-references-2/RESULT.json');require(reference['status']=='PASS','Final packaged matrix failed')
        require(reference['producer']==status['producer'],'Packaged matrix used another producer')
        require({x['host'] for x in reference['hosts']}=={'windows21','windows25','linux21'},'Host population changed')
        for host in reference['hosts']:
            require(host['status']=='PASS' and host['original_cases']==45 and host['commands']==103 and host['comparisons']==51 and host['expected_failures']==9 and host['replays']==7,'Per-host original-reference scope changed')
            base='workflow-original-references-2/'+host['host']+'/'
            commands=saved(base+'COMMANDS.json');comparisons=saved(base+'ORIGINAL-COMPARISONS.json')
            require(len(commands)==103 and all(x['exit_code']==x['expected'] for x in commands),'Unexpected packaged outcome')
            require(len(comparisons)==51 and all(x['exact_rng_after'] is True and x['status'] in ('MATCH','MATCH_WITH_EXPLICIT_DEGENERATE_LIMITS') for x in comparisons),'Independent reference comparisons changed')
        require(status['packaged_commands']==309 and status['independent_original_comparisons']==153 and status['actual_replays']==21,'Aggregate packaged scope changed')
        controls=saved('saved-controls-2/RESULT.json');require(controls['status']=='PASS' and len(controls['cases'])==51 and all(x['matched'] and x['exit_code']==x['expected'] for x in controls['cases']),'Saved integrity controls failed')
        require(status['saved_control_scenarios']==17 and status['saved_control_host_checks']==51,'Saved control scope changed')
        protocol=saved('protocol-controls-1/RESULT.json');require(protocol['status']=='PASS' and len(protocol['commands'])==30 and all(x['exit_code']==x['expected'] for x in protocol['commands']),'Protocol checks failed')
        for key in ('actual_deadline_refused','actual_owned_JVM_returned_after_deadline','existing_output_bytes_unchanged','standalone_child_without_original_parent_path','actual_replay_chain'):require(protocol[key] is True,'Missing actual protocol evidence: '+key)
        independent=saved('independent-controls-1/RESULT.json');require(independent['status']=='PASS' and len(independent['cases'])==16 and all(x['exit_code']==x['expected'] for x in independent['cases']),'Independent scientific controls failed')
        require(independent['general_capture_verifier_claim'] is False,'Reference comparator overclaims arbitrary capture verification')
        require(z.read('source-audit-2/ROLES.json')==z.read('source-audit-linux-2/ROLES.json')==(root/'docs/literature/mrpresso-catalog-sources/ROLES.json').read_bytes(),'Final full250 source roles differ')
        roles=saved('source-audit-2/ROLES.json');require(roles['papers_checked']==250 and [x['paper_id'] for x in roles['roles']]==['P105','P125','P135','P150','P189','P224'] and roles['discussion_only'][0]['paper_id']=='P177','Primary role scope changed')
        compatibility=saved('final-suite-setup-1/PAYLOAD-COMPATIBILITY.json');require(compatibility['modified']==['org/jlinalg/cli/MrPressoWorkflowAudit.class'] and compatibility['scientific_classes_and_resources_byte_identical'] is True,'Final audit-only payload refinement changed science')
        prior=saved('normal-build-4/PAYLOADS.json');require(prior['previous_payloads']==22895 and prior['modified']==['org/jlinalg/cli/JLinAlgCli.class'],'Preceding producer payloads changed')
    return dict(status='PASS_SCOPED_NATIVE_MRPRESSO_SAVED_CHECKPOINT',source_files=len(sources),evidence_members=len(names),
        normal_build_tests=tests,original_public_api_calls=45,packaged_commands=309,independent_original_comparisons=153,
        native_workflow_qualified=True,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path);args=parser.parse_args()
    try:print(json.dumps(verify(jar=args.jar),indent=2))
    except (OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile,ET.ParseError) as error:
        parser.exit(2,'FAIL2: MR-PRESSO saved checkpoint refused: '+str(error)+'\nUse the recorded Git/source context and exact producer. Preserve failures; do not refresh old hashes to conceal changes.\n')

if __name__=='__main__':main()
