"""Keep the frozen fourteen-role census and separately bound author-code extension."""
from pathlib import Path
import copy,hashlib,json,zipfile
import twosamplemr_primary as old

ROOT=Path(__file__).resolve().parents[3]
CENSUS=old.FOLDER+'/CENSUS.json'
ATTRIBUTION='docs/literature/twosamplemr-author-attribution-2026-11-21/ATTRIBUTION.json'
EXPECTED_CENSUS={'bytes': 126469, 'sha256': 'bea20126bdeb4fc0ee56c29088d5b1ce6880ce283d301cd02001bac4574aefc8'}
EXPECTED_ATTRIBUTION={'bytes': 23325, 'sha256': '651d98dbf4d68bd756370ed6d3fbc6b97caf069f39a8224abf90d96cf75e16cf'}
require=old.require
pin=old.pin
encoded=old.encoded

def frozen():
    source=(ROOT/CENSUS).read_bytes();author=(ROOT/ATTRIBUTION).read_bytes()
    require(pin(source)==EXPECTED_CENSUS and pin(author)==EXPECTED_ATTRIBUTION,'Changed qualified census/attribution bytes')
    base=old.frozen();attribution=json.loads(author)
    require(attribution['new_catalog_credit']==attribution['reviewed_use_credit']==0 and len(attribution['files'])==9,'Different static author attribution')
    require(attribution['P157']['reported_TwoSampleMR_version']=='0.5.7'
        and attribution['P157']['paper_linked_deposit']['current_GitHub_MR_file_identical']
        and attribution['P177']['reported_TwoSampleMR_version'] is None
        and attribution['P177']['TEMR_is_separate_provider'],'Changed provider/edition boundary')
    result=copy.deepcopy(base)
    result.update(status='PASS_TWOSAMPLEMR14_FROZEN_SOURCE_ROLES_WITH_STATIC_AUTHOR_EXTENSION',
        preceding_source_status=base['status'],preceding_census=EXPECTED_CENSUS,
        author_attribution=attribution,author_attribution_pin=EXPECTED_ATTRIBUTION)
    return result

def validate(result):
    require(result==frozen(),'Changed exact source-role/attribution witness or false scope')
    base={k:copy.deepcopy(result[k]) for k in old.frozen()}
    base['status']=result['preceding_source_status'];old.validate(base)

def audit(cache,code_cache=None):
    require(old.audit(cache)==old.frozen(),'Fresh all250 XML/DOI/source audit differs')
    result=frozen()
    if code_cache is not None:
        cache=Path(code_cache)
        for row in result['author_attribution']['files']:
            path=cache/row['paper']/row['repository'].split('/')[1]/'files'/row['path']
            raw=path.read_bytes();require(pin(raw)==dict(bytes=row['bytes'],sha256=row['sha256']),'Changed pinned author source: '+row['path'])
        deposit=result['author_attribution']['P157']['paper_linked_deposit'];raw=(cache/'P157/zenodo-deposit-1/source.zip').read_bytes()
        require(pin(raw)=={k:deposit['archive'][k] for k in ['bytes','sha256']},'Changed paper-linked archived code')
        with zipfile.ZipFile(cache/'P157/zenodo-deposit-1/source.zip') as z:
            require(z.testzip() is None,'Corrupt paper-linked source archive')
            for row in deposit['MR_source_files']:
                require(pin(z.read(row['archive_member']))=={k:row[k] for k in ['bytes','sha256']},'Changed archived MR script')
    validate(result);return result

def role_text(role,primary):
    text=role['usage_summary'];caution=role['required_caution']
    if role['paper_id']=='P157':
        text+=' The paper-linked archived MR script, identical to the pinned author copy, explicitly calls harmonise_data and mr in both eQTL/pQTL directions without a method_list override.'
        caution+=' The earlier native-only proposal is retained unchanged; the separate static code extension resolves these calls but does not establish complete author execution or an execution commit.'
    if role['paper_id']=='P177':
        text+=' In seven pinned simulation scripts, IVW explicitly calls MendelianRandomization::mr_input/mr_ivw; unqualified median/mode names are statically inferred to resolve to TwoSampleMR.'
        caution+=' Keep the median/mode provider inference explicit; the package edition, historical namespace resolution and Egger attribution remain unproven. The source R4.3.2 declaration does not become the qualified R4.6.1 host.'
    caution+=' Qualified original workflows cover declared prepared-data operations; missing historical API/provider/author-preparation scopes remain explicit. This review is not whole-tool or whole-paper reproduction.'
    return text,caution
