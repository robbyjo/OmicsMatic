"""Audit all frozen sources and witness only literally named edgeR functions."""
from pathlib import Path
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from collect_omics_review import paragraphs
from edger_source_roles import audit as source_audit, require

ROOT = Path(__file__).resolve().parents[3]
CENSUS = 'docs/literature/edger-normalization-checks/CENSUS.json'
CENSUS_SHA256 = 'ac91e9c1055068797fedd0add3a7d117b008b21f8200a7a5e7e9f29bb0939075'
FUNCTIONS = {
    'P037': [(49, 'glmQLFTest')],
    'P053': [(73, 'glmFit')],
    'P087': [(65, 'calcNormFactors'), (65, 'cpm')],
    'P104': [(35, 'filterByExpr')],
    'P182': [(13, 'filterByExpr')],
    'P228': [(9, 'estimateDisp')],
    'P237': [(12, 'sumTechReps'), (12, 'cpm')],
}


def frozen():
    raw = (ROOT / CENSUS).read_bytes()
    require(hashlib.sha256(raw).hexdigest() == CENSUS_SHA256, 'Changed frozen edgeR source census')
    return json.loads(raw)


def audit(cache):
    census = source_audit(cache)
    require(census == frozen(), 'Fresh 250-source edgeR census differs')
    witnesses = []
    by_id = {r['paper_id']: r for r in census['roles']}
    for pid, functions in FUNCTIONS.items():
        role = by_id[pid]
        with gzip.open(Path(cache) / (role['pmcid'] + '.xml.gz'), 'rb') as stream:
            raw = stream.read(64 * 1024 * 1024 + 1)
        require(len(raw) <= 64 * 1024 * 1024, 'Oversize primary XML')
        blocks = {b['paragraph_index']: b for b in paragraphs(ET.fromstring(raw))}
        anchors = {a['paragraph_index']: a for a in role['anchors']}
        for index, function in functions:
            text = blocks[index]['text']
            digest = hashlib.sha256(text.encode()).hexdigest()
            require(digest == anchors[index]['paragraph_sha256'], 'Different function source anchor: ' + pid)
            hits = list(re.finditer(r'(?<![A-Za-z0-9_])' + re.escape(function) + r'(?![A-Za-z0-9_])', text))
            require(bool(hits), 'Function is not literally named at the declared anchor: ' + pid + ' ' + function)
            witnesses.append(dict(paper_id=pid, paragraph_index=index, function=function,
                paragraph_sha256=digest, literal=function, character_offsets=[m.start() for m in hits]))
    result = dict(schema_version=1, status='PASS_FROZEN_250_SOURCE_EDGER_CENSUS_AND_EXPLICIT_FUNCTION_WITNESSES',
        frozen_census=census, explicit_functions=witnesses,
        canonical_reviews_appended=0, canonical_observations_added=0, new_family_credit=0)
    validate(result)
    return result


def validate(result):
    require(set(result) == {'schema_version', 'status', 'frozen_census', 'explicit_functions',
        'canonical_reviews_appended', 'canonical_observations_added', 'new_family_credit'}, 'Unknown source-audit fields')
    require(result['status'] == 'PASS_FROZEN_250_SOURCE_EDGER_CENSUS_AND_EXPLICIT_FUNCTION_WITNESSES', 'Different source-audit disposition')
    require(result['schema_version'] == 1 and result['frozen_census'] == frozen(), 'Different frozen source population')
    require(result['canonical_reviews_appended'] == result['canonical_observations_added'] == result['new_family_credit'] == 0,
        'A source audit cannot award unadmitted credit')
    census = result['frozen_census']
    roles = {r['paper_id']: r for r in census['roles']}
    require(len(roles) == 17 and census['papers_scanned'] == 250 and census['proposed_additions'] == ['P088'],
        'Different edgeR source roles')
    expected = [(pid, index, function) for pid, pairs in FUNCTIONS.items() for index, function in pairs]
    actual = [(w['paper_id'], w['paragraph_index'], w['function']) for w in result['explicit_functions']]
    require(actual == expected, 'Different literal function witness population or order')
    for witness in result['explicit_functions']:
        require(set(witness) == {'paper_id', 'paragraph_index', 'function', 'paragraph_sha256',
            'literal', 'character_offsets'}, 'Unknown literal function witness fields')
        role = roles[witness['paper_id']]
        anchor = next(a for a in role['anchors'] if a['paragraph_index'] == witness['paragraph_index'])
        require(witness['paragraph_sha256'] == anchor['paragraph_sha256'] and witness['literal'] == witness['function'],
            'Function witness differs from declared source identity')
        offsets = witness['character_offsets']
        require(isinstance(offsets, list) and bool(offsets) and offsets == sorted(set(offsets))
            and all(type(i) is int and i >= 0 for i in offsets), 'Invalid literal source positions')
    return result
