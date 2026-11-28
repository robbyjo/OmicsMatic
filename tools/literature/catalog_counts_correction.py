"""Forward metadata correction of one stale aggregate, with zero implementation/review credit."""
import argparse,copy,json,os,re,zipfile
from pathlib import Path
import pbmm2_catalog as prior
from catalog_count_invariants import validate
from ascat_catalog import checksums,verify_sums
from catalog_fixed_cohort import verify_fixed_cohort
ROOT=Path(__file__).resolve().parents[2];DATA=prior.DATA;META=prior.META
read=prior.read;pin=prior.pin;identity=prior.identity;encoded=prior.encoded;require=prior.require
FOLDER='docs/literature/catalog-counts-correction';Q=FOLDER+'/QUALIFICATION.json';PROOF=DATA+'/integration-counts-validation.json'
def totals():return dict(papers=250,named_labels=453,provider_definitions=492,observations=2741,reviewed_uses=1940,pending_uses=801,scoped_families=186,native=37,upstream=149,missing_labels=246,whole_goal_complete=False)
def predecessor(root,before):
    folder=root/prior.FOLDER;base=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==base['archive'],'Prior pbmm2 archive differs')
    with zipfile.ZipFile(folder/'metadata-before.zip')as z:require(set(z.namelist())==set(META),'Prior metadata population differs');older={n:z.read(n)for n in META}
    registry=prior.predecessor(root,older);expected,registry,_=prior.transition(root,older,registry,read(root/prior.Q))
    require(before==expected and read(folder/'REGISTRY.json')==registry,'Prior exact pbmm2 transition differs');return registry
def transition(root,before,registry,q):
    require(q['status']=='PASS_CATALOG_AGGREGATE_COUNT_CORRECTION'and q['prior_stale_upstream']==148 and q['corrected_upstream']==149 and q['new_family_credit']==q['new_source_reviews']==q['recovered_observations']==0 and not q['whole_goal_complete'],'Counter correction scope/credit differs')
    papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in [0,1,2,7]]
    require(summary['integration_family_counts']['upstream']==148 and summary['implementation_status_counts']['upstream-adapter-validated']==149,'Expected stale summary field differs')
    corrected=copy.deepcopy(summary);corrected['integration_family_counts']['upstream']=149;corrected['current_effective_registry']=FOLDER+'/REGISTRY.json';corrected['catalog_count_qualification']=Q
    require(validate(papers,inventory,corrected,ledger,registry)==totals(),'Corrected catalog arithmetic differs')
    after=dict(before);after[META[2]]=encoded(corrected)
    page=before[META[8]].decode();match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
    require(match and json.loads(match[2])==dict(papers=papers,tools=inventory,summary=summary),'Prior embedded report differs')
    payload=json.dumps(dict(papers=papers,tools=inventory,summary=corrected),ensure_ascii=False).replace('<','\\u003c');after[META[8]]=(page[:match.start(2)]+payload+page[match.end(2):]).encode()
    require({name for name in META if after[name]!=before[name]}=={META[2],META[8]},'Unrelated catalog bytes changed')
    return after,copy.deepcopy(registry),dict(schema_version=1,status='PASS_CATALOG_AGGREGATE_COUNT_CORRECTION',totals=totals(),new_family_credit=0,new_source_reviews=0,recovered_observations=0,scientific_methods_inputs_results_or_tool_statuses_changed=False,literature_pages_published=False)
def check(root):
    folder=root/FOLDER;base=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==base['archive']and pin(folder/'PREVIOUS-SHA256SUMS')==base['previous_checksums'],'Count-correction baseline differs')
    for name,expected in base['source_files'].items():require(pin(root/name)==expected,'Count-correction validator source differs')
    with zipfile.ZipFile(folder/'metadata-before.zip')as z:require(set(z.namelist())==set(META),'Baseline metadata population differs');before={n:z.read(n)for n in META}
    require({n:identity(raw)for n,raw in before.items()}==base['metadata'],'Baseline bytes differ');registry=predecessor(root,before)
    after,registry,proof=transition(root,before,registry,read(root/Q));require(all((root/n).read_bytes()==raw for n,raw in after.items()),'Catalog is not the exact zero-credit count correction')
    require(read(folder/'REGISTRY.json')==registry and read(root/PROOF)==proof,'Provider registry or correction proof differs')
    verify_fixed_cohort(root,root/DATA,json.loads(after[META[0]]));old=checksums((folder/'PREVIOUS-SHA256SUMS').read_bytes());current=checksums((root/DATA/'SHA256SUMS').read_bytes())
    require(set(current)==set(old)|{Path(PROOF).name},'Checksum population differs');require(all(current[n]==value or n==Path(META[2]).name for n,value in old.items()),'Unrelated checksum changed');verify_sums(root,current)
    actual=validate(*[json.loads(after[META[i]])for i in [0,1,2,7]],registry);require(actual==totals(),'Current inventory arithmetic differs');return actual
def apply(root,private,out):
    require(not(root/FOLDER).exists()and out.is_relative_to(private)and not out.exists(),'Use NEW correction/evidence paths');prior.check(root)
    before={n:(root/n).read_bytes()for n in META};registry=predecessor(root,before)
    # Preserve the previous field and show why a transition-only proof was insufficient.
    rejected=False
    try:validate(*[json.loads(before[META[i]])for i in [0,1,2,7]],registry)
    except Exception as error:rejected=True;reason=str(error)
    require(rejected and 'Native/upstream/scoped'in reason,'Stale-field negative control did not fail as expected')
    q=dict(schema_version=1,status='PASS_CATALOG_AGGREGATE_COUNT_CORRECTION',prior_stale_upstream=148,corrected_upstream=149,new_family_credit=0,new_source_reviews=0,recovered_observations=0,whole_goal_complete=False,reason='The inventory and reported186=37+149 totals were correct; one aggregate upstream field lagged behind. New cross-field arithmetic guards reject that mismatch. No tool, paper, review or scientific result is added or changed.')
    after,registry,proof=transition(root,before,registry,q);previous=(root/DATA/'SHA256SUMS').read_bytes();rows=checksums(previous);require(len(rows)==934,'Prior checksum population differs');verify_sums(root,rows);out.mkdir();folder=root/FOLDER;folder.mkdir()
    with zipfile.ZipFile(folder/'metadata-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9)as z:
        for name,raw in before.items():z.writestr(name,raw)
    names=['tools/literature/catalog_count_invariants.py','tools/literature/catalog_counts_correction.py'];(folder/'PREVIOUS-SHA256SUMS').write_bytes(previous)
    (folder/'BASELINE.json').write_bytes(encoded(dict(schema_version=1,archive=pin(folder/'metadata-before.zip'),previous_checksums=pin(folder/'PREVIOUS-SHA256SUMS'),metadata={n:identity(raw)for n,raw in before.items()},source_files={n:pin(root/n)for n in names})))
    (root/Q).write_bytes(encoded(q));(folder/'REGISTRY.json').write_bytes(encoded(registry));(out/'PLAN.json').write_bytes(encoded(dict(before={n:identity(raw)for n,raw in before.items()},after={n:identity(raw)for n,raw in after.items()},stale_field_control=dict(rejected=True,reason=reason),new_family_credit=0,new_source_reviews=0)))
    for i,(name,raw)in enumerate(after.items()):
        require((root/name).read_bytes()==before[name],'Concurrent metadata edit')
        if raw!=before[name]:stage=out/('stage-'+str(i));stage.write_bytes(raw);os.replace(stage,root/name)
    (root/PROOF).write_bytes(encoded(proof));rows[Path(META[2]).name]=pin(root/META[2])['sha256'];rows[Path(PROOF).name]=pin(root/PROOF)['sha256']
    newline='\r\n'if previous.count(b'\n')==previous.count(b'\r\n')else'\n';(root/DATA/'SHA256SUMS').write_bytes(''.join(value+'  '+name+newline for name,value in rows.items()).encode())
    result=check(root);(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_ZERO_CREDIT_AGGREGATE_CORRECTION',totals=result)));return result
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);p.add_argument('--out',type=Path);p.add_argument('--apply',action='store_true');a=p.parse_args()
    try:
        if a.apply:require(a.private and a.out,'Apply needs retained private evidence and NEW output');result=apply(a.root.resolve(),a.private.resolve(),a.out.resolve())
        else:result=check(a.root.resolve())
        print(json.dumps(result,indent=2))
    except Exception as e:raise SystemExit('FAIL2: no complete catalog count correction; retain evidence and correct cause: '+str(e))
