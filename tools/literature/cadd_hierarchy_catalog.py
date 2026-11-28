"""Forward the native CADD hierarchy component without duplicate implementation or review credit."""
import argparse,copy,html,json,os,re,zipfile
from pathlib import Path
import catalog_counts_correction as prior
from catalog_count_invariants import validate
from ascat_catalog import checksums,verify_sums
from catalog_fixed_cohort import verify_fixed_cohort
from verify_cadd_hierarchy import verify,Q
ROOT=Path(__file__).resolve().parents[2];DATA=prior.DATA;META=prior.META
read=prior.read;pin=prior.pin;identity=prior.identity;encoded=prior.encoded;require=prior.require
FOLDER='docs/literature/cadd-hierarchy-admission';COVERAGE=DATA+'/cadd-hierarchy-component-coverage.json';PROOF=DATA+'/cadd-hierarchy-catalog-validation.json'
SCOPE='Existing CADD1.6 lookup, preparation, calibration and complete linear-model components remain qualified. A new native Java library implements original consequence-tier selection, literal consecutive groups, first ties, GPS borrowing and all-annotation score propagation, with complete-stream capacities and explicit nonfinite counts. All168 original option/input cases retain116 successes and52 refusals with byte-identical output on Windows and Linux. The native linear, hierarchy and PHRED chain matches all published fields in both full original examples,30 annotation rows with every original feature. Full raw-VCF annotation/prediction, other CADD releases, clinical interpretation and author-specific recipes remain separately unqualified. No additional family, observation or review credit.'
ACTION='Read docs/cadd-consequence-hierarchy.md and the native linear-model numerical envelope. Supply complete source-scored annotation rows in original order and explicit whole-stream/group capacities. Nonfinite rows remain unassessable. FAIL2 admits no complete result, including any already-written prefix: retain diagnostics, correct the named cause and retry NEW output. SKIP3 skips dependent analysis when exact permitted prerequisites are missing. Never reduce rows/models/references, relax runtime guards or restart live/indeterminate jobs. The entire official GRCh38 annotation archive is checksum-verified; full extraction, resource binding and actual workflow execution remain pending. Preserve its active installation and original archive/parts. Cell Ranger remains excluded; MaxQuant remains pending with runtime skipped.'
NOTICE='250 frozen papers;453 named labels and492 provider definitions;186 scoped families(37 native,149 upstream),1940 reviewed uses and801 pending uses across2741 observations;246 missing labels. This native CADD hierarchy expansion earns zero additional family, source-review or observation credit. Full implementation and review remain open.'

def totals():return prior.totals()

def predecessor(root,before):
    folder=root/prior.FOLDER;base=read(folder/'BASELINE.json');require(pin(folder/'metadata-before.zip')==base['archive'],'Prior count-correction archive differs')
    with zipfile.ZipFile(folder/'metadata-before.zip')as z:
        require(set(z.namelist())==set(META),'Prior metadata population differs');older={n:z.read(n)for n in META}
    registry=prior.predecessor(root,older);expected,registry,_=prior.transition(root,older,registry,read(root/prior.Q))
    require(before==expected and read(folder/'REGISTRY.json')==registry,'Prior exact count-correction transition differs');return registry

def transition(root,before,registry,q):
    require(q['status']=='PASS_NATIVE_CADD16_CONSEQUENCE_HIERARCHY'and q['hierarchy_component_qualified']and not q['raw_variant_prediction_qualified']and q['new_family_credit']==q['new_source_reviews']==q['recovered_observations']==0 and not q['whole_goal_complete'],'Hierarchy component scope or credit differs')
    papers,inventory,summary,ledger=[json.loads(before[META[i]])for i in [0,1,2,7]]
    require(validate(papers,inventory,summary,ledger,registry)==totals(),'Predecessor complete catalog arithmetic differs')
    registry=copy.deepcopy(registry);item=next(x for x in registry if x['id']=='cadd');tool=next(x for x in inventory if x['id']=='cadd')
    require(item['current_coverage']==tool['implementation_status']=='native-subset-validated'and len(tool['papers'])==19,'Existing CADD family status/membership differs')
    sources=['src/main/java/org/jlinalg/genetics/CaddLinearScoring.java','src/main/java/org/jlinalg/genetics/CaddConsequenceHierarchy.java']
    item.update(coverage_note=SCOPE,additional_implementation_sources=sources)
    tool.update(coverage_note=SCOPE,implementation_scope=SCOPE,additional_implementation_sources=sources,current_qualification=Q,prior_qualified_components='docs/literature/cadd-linear-validation/QUALIFICATION.json',validation_note='12 passing Java contracts;168 complete original option/input cases,116 successes and52 source refusals; every stdout byte including partial failure prefixes and native host statistics agree. Both entire original examples,all30 annotation rows,match every published field through the native linear/hierarchy/PHRED chain. Full raw-VCF prediction remains unqualified.')
    summary.update(current_effective_registry=FOLDER+'/REGISTRY.json',cadd_hierarchy_component_qualification=Q)
    require(validate(papers,inventory,summary,ledger,registry)==totals(),'Proposed complete catalog arithmetic differs')
    after=dict(before);after[META[1]]=encoded(inventory);after[META[2]]=encoded(summary)
    preface=('# Current native CADD consequence hierarchy\n\n'+NOTICE+'\n\n'+SCOPE+'\n\n**Warnings and required actions:** '+ACTION+'\n\nSee [guide](../../cadd-consequence-hierarchy.md), [qualification](../cadd-hierarchy-validation/QUALIFICATION.json) and [handover](../cadd-hierarchy-validation/HANDOVER.md). Earlier checkpoints below are historical.\n\n---\n\n').encode()
    for name in META[3:7]:after[name]=preface+before[name]
    notice='<aside id="cadd-hierarchy-notice"><h2>Native CADD consequence hierarchy</h2><p>'+html.escape(NOTICE)+'</p><p>'+html.escape(SCOPE)+'</p><p><strong>Warnings and required actions:</strong> '+html.escape(ACTION)+'</p></aside>'
    for name in META[8:]:
        page=before[name].decode();require('id="cadd-hierarchy-notice"'not in page,'Hierarchy component already admitted')
        if name==META[8]:
            match=re.search(r'(<script id="review-data" type="application/json">)(.*?)(</script>)',page,re.S)
            require(match and json.loads(match[2])==dict(papers=papers,tools=json.loads(before[META[1]]),summary=json.loads(before[META[2]])),'Previous embedded report differs')
            payload=json.dumps(dict(papers=papers,tools=inventory,summary=summary),ensure_ascii=False).replace('<','\\u003c');page=page[:match.start(2)]+payload+page[match.end(2):]
        body=re.search(r'<body[^>]*>',page,re.I);marker=page.find('<site-header ',body.end());require(marker>=body.end(),'Report body boundary differs')
        after[name]=(page[:body.end()]+notice+'<details id="cadd-hierarchy-prior"><summary>Historical catalog checkpoints</summary>'+page[body.end():marker]+'</details>'+page[marker:]).encode()
    require(after[META[0]]==before[META[0]]and after[META[7]]==before[META[7]],'Paper/review bytes changed without review credit')
    coverage=dict(schema_version=1,scope=SCOPE,required_action=ACTION,qualification=Q,previous_components='docs/literature/cadd-linear-validation/QUALIFICATION.json',new_family_credit=0,new_source_reviews=0,recovered_observations=0,hierarchy_component_qualified=True,raw_variant_prediction_qualified=False,whole_goal_complete=False)
    return after,registry,coverage

def proof():return dict(schema_version=1,status='PASS_CADD_HIERARCHY_COMPONENT_FORWARD_ADMISSION',totals=totals(),new_family_credit=0,new_source_reviews=0,recovered_observations=0,raw_source_text_published=False,literature_pages_published=False)

def check(root):
    folder=root/FOLDER;base=read(folder/'BASELINE.json');verify(root)
    require(pin(folder/'metadata-before.zip')==base['archive']and pin(folder/'PREVIOUS-SHA256SUMS')==base['previous_checksums']and pin(root/'tools/literature/cadd_hierarchy_catalog.py')==base['transformer'],'Hierarchy admission baseline differs')
    with zipfile.ZipFile(folder/'metadata-before.zip')as z:
        require(set(z.namelist())==set(META),'Baseline population differs');before={n:z.read(n)for n in META}
    require({n:identity(raw)for n,raw in before.items()}==base['metadata'],'Baseline metadata bytes differ')
    registry=predecessor(root,before);after,registry,coverage=transition(root,before,registry,read(root/Q))
    require(all((root/n).read_bytes()==raw for n,raw in after.items()),'Current catalog is not the exact zero-credit hierarchy transition')
    require(read(folder/'REGISTRY.json')==registry and read(root/COVERAGE)==coverage and read(root/PROOF)==proof(),'Current registry/coverage/proof differs')
    require(validate(*[json.loads(after[META[i]])for i in [0,1,2,7]],registry)==totals(),'Saved final complete catalog arithmetic differs')
    verify_fixed_cohort(root,root/DATA,json.loads(after[META[0]]));old=checksums((folder/'PREVIOUS-SHA256SUMS').read_bytes());current=checksums((root/DATA/'SHA256SUMS').read_bytes())
    require(set(current)==set(old)|{Path(COVERAGE).name,Path(PROOF).name},'Checksum population differs')
    changed={Path(n).name for n in META if n.startswith(DATA+'/')};require(all(current[n]==v or n in changed for n,v in old.items()),'Unrelated checksum changed');verify_sums(root,current);return totals()

def apply(root,private,out):
    require(not(root/FOLDER).exists()and out.is_relative_to(private)and not out.exists(),'Use NEW admission/evidence paths');verify(root,private);prior.check(root)
    before={n:(root/n).read_bytes()for n in META};registry=predecessor(root,before);after,registry,coverage=transition(root,before,registry,read(root/Q))
    previous=(root/DATA/'SHA256SUMS').read_bytes();rows=checksums(previous);require(len(rows)==935,'Prior checksum population differs');verify_sums(root,rows);out.mkdir();folder=root/FOLDER;folder.mkdir()
    with zipfile.ZipFile(folder/'metadata-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9)as z:
        for name,raw in before.items():z.writestr(name,raw)
    (folder/'PREVIOUS-SHA256SUMS').write_bytes(previous)
    (folder/'BASELINE.json').write_bytes(encoded(dict(schema_version=1,archive=pin(folder/'metadata-before.zip'),previous_checksums=pin(folder/'PREVIOUS-SHA256SUMS'),metadata={n:identity(raw)for n,raw in before.items()},transformer=pin(Path(__file__)))))
    (out/'PLAN.json').write_bytes(encoded(dict(before={n:identity(raw)for n,raw in before.items()},after={n:identity(raw)for n,raw in after.items()},totals=totals(),zero_new_credit=True)))
    for i,(name,raw)in enumerate(after.items()):
        require((root/name).read_bytes()==before[name],'Concurrent catalog edit')
        if raw!=before[name]:stage=out/('stage-'+str(i));stage.write_bytes(raw);os.replace(stage,root/name)
    (folder/'REGISTRY.json').write_bytes(encoded(registry));(root/COVERAGE).write_bytes(encoded(coverage));(root/PROOF).write_bytes(encoded(proof()))
    for name in [n for n in META if n.startswith(DATA+'/')]+[COVERAGE,PROOF]:rows[Path(name).name]=pin(root/name)['sha256']
    newline='\r\n'if previous.count(b'\n')==previous.count(b'\r\n')else'\n'
    (root/DATA/'SHA256SUMS').write_bytes(''.join(value+'  '+name+newline for name,value in rows.items()).encode())
    result=check(root);(out/'COMPLETED.json').write_bytes(encoded(dict(status='PASS_CADD_HIERARCHY_COMPONENT_FORWARD_ADMISSION',totals=result)));return result

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);p.add_argument('--out',type=Path);p.add_argument('--apply',action='store_true');a=p.parse_args()
    try:
        if a.apply:require(a.private and a.out,'Apply needs retained evidence and NEW output');result=apply(a.root.resolve(),a.private.resolve(),a.out.resolve())
        else:result=check(a.root.resolve())
        print(json.dumps(result,indent=2))
    except Exception as error:raise SystemExit('FAIL2: no complete CADD hierarchy admission; retain evidence and correct cause: '+str(error))
