"""Read a frozen development checkpoint; no numerical qualification is implied."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,json,zipfile,xml.etree.ElementTree as ET

def check(condition,message):
    if not condition:raise ValueError(message)

def identity(data):return {'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}

def load(data):
    def pairs(items):
        result={}
        for key,value in items:
            check(key not in result,'Duplicate JSON field: '+key);result[key]=value
        return result
    return json.loads(data,object_pairs_hook=pairs)

def verify(root,packet):
    inventory=load((packet/'CHECKPOINT_FILES.json').read_bytes())
    actual={p.relative_to(packet).as_posix() for p in packet.rglob('*') if p.is_file() and '__pycache__' not in p.parts and p.name!='CHECKPOINT_FILES.json'}
    check(actual==set(inventory),'Checkpoint file population differs')
    for name,expected in inventory.items():
        check(identity((packet/name).read_bytes())==expected,'Changed checkpoint file: '+name)
    sources=load((packet/'SOURCES.json').read_bytes())
    for name,expected in sources.items():
        check(identity((root/name).read_bytes())==expected,'Changed source: '+name)
    status=load((packet/'STATUS.json').read_bytes())
    for key in ('whole_goal_complete','full_workflow_qualified','catalog_admitted'):
        check(status[key] is False,'Development checkpoint cannot claim '+key)
    check(status['new_family_credit']==status['new_reviews']==0,'Development cannot award credit')
    check(status['current_catalog']==dict(papers=250,scoped_families=152,native=27,upstream=125,including_existing_adapters=155,reviewed_uses=1685,pending_uses=1037),'Catalog scope changed')
    check(status['checkpoint_source_files']==len(sources)==29,'Source population differs')
    evidence=load((packet/'EVIDENCE.json').read_bytes());archive=packet/evidence['archive']
    check(identity(archive.read_bytes())==evidence['identity']==status['evidence_identity'],'Archive identity differs')
    with zipfile.ZipFile(archive) as z:
        names=z.namelist();check(len(names)==len(set(names)),'Duplicate archived member')
        check(set(names)==set(evidence['members']),'Archive member population differs')
        check(len(names)==status['evidence_members']==873,'Evidence population differs')
        for name in names:
            path=PurePosixPath(name)
            check(not path.is_absolute() and '..' not in path.parts and '\\' not in name,'Unsafe archive member')
            check(identity(z.read(name))==evidence['members'][name],'Changed archive member: '+name)
        def saved(name):return load(z.read(name))
        old=saved('INVALIDATED-PRELIMINARY-VERIFIER.json')
        check(old==status['preliminary_verifier_invalidated'],'Invalidation evidence changed')
        check(old['status']=='PRELIMINARY_PRODUCER_NOT_QUALIFIED_FOR_CAPTURE_VERIFICATION','Old invalidation lost')
        check(old['producer']['sha256']=='7b9bedea257c085da227f49223f032585450ad7101df60fe8ed26d5dab52c52b','Wrong old producer')
        finding=saved('small-unit-verifier-probe-1/FINDING.json')
        check(finding['status']=='PRELIMINARY_VERIFIER_FALSE_ACCEPTANCE_CONFIRMED' and finding['incorrect_coefficient_and_standard_error_multiplier']==1_000_000,'Original failure witness lost')
        build=saved('normal-build-2/RESULT.json')
        check(build==status['normal_build'] and build['exit_code']==0 and build['full_workflow_qualified'] is False,'Build status differs')
        check(build['producer']['sha256']=='0e44e4609a0895c2d2f67e5034325e8563531015b8ae6f59775ff7deb513ce85','Wrong current producer')
        counts={key:0 for key in ('tests','failures','errors','skipped')}
        for name in names:
            if name.startswith('normal-build-2/project-build/test-results/test/') and name.endswith('.xml'):
                suite=ET.fromstring(z.read(name))
                for key in counts:counts[key]+=int(suite.get(key,'0'))
        check(counts==status['normal_build_tests']==dict(tests=791,failures=0,errors=0,skipped=0),'Test scope differs')
        smoke=saved('handover-packaged-1/RESULT.json');cases=smoke['cases']
        check(smoke['status']=='PASS' and len(cases)==status['packaged_handover_cases']==30,'Packaged scope differs')
        check(smoke['producer']=={k:build['producer'][k] for k in ('bytes','sha256')},'Smoke producer differs')
        check(all(x['exit_code']==x['expected'] for x in cases),'Unexpected packaged outcome')
        check(sum(x['expected']==2 for x in cases)==status['expected_packaged_refusals']==8,'Refusal population differs')
        check({x['host'] for x in cases}=={'windows21','windows25','linux21'},'Host scope differs')
        for host in status['packaged_hosts']:
            for label,fragment in [('inflated-tiny-refusal',b'Scientific numeric contradiction'),('table-lie-refusal',b'Result table contradicts typed source-profile result')]:
                check(fragment in z.read('handover-packaged-1/'+host+'/'+label+'.stderr'),'Wrong refusal cause')
        check(smoke['full_workflow_qualified'] is False and smoke['new_family_credit']==smoke['new_reviews']==0,'Smoke overclaims qualification')
        payloads=saved('normal-build-2/PAYLOADS.json')
        check(payloads['previous_payloads']==22895 and payloads['modified']==['org/jlinalg/cli/JLinAlgCli.class'],'Prior payload compatibility changed')
    return dict(status='PASS_UNFINISHED_DEVELOPMENT_CHECKPOINT',source_files=len(sources),evidence_members=len(names),normal_build_tests=counts,packaged_cases=len(cases),full_workflow_qualified=False,new_family_credit=0,new_reviews=0)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path);args=parser.parse_args()
    packet=Path(__file__).resolve().parent;root=args.root.resolve() if args.root else packet.parents[2]
    try:print(json.dumps(verify(root,packet),indent=2))
    except (OSError,ValueError,KeyError,TypeError,zipfile.BadZipFile,ET.ParseError) as error:
        parser.exit(2,'FAIL2: saved development checkpoint refused: '+str(error)+'\nPreserve the original evidence; use the recorded Git checkout. Do not refresh old hashes to hide source changes.\n')

if __name__=='__main__':main()
