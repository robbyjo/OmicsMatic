# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Extend the unchanged predecessor gate/audit harnesses with exact current counts and Rsubread evidence."""
from setup_deseq2_reference import ROOT


def generated():
    tools=ROOT/'tools/literature';text=(tools/'run_hifiasm_catalog_checks.py').read_text(encoding='utf8')
    for old,new in [('from hifiasm_catalog import current_catalog_review_context,verify_review_extension','from rsubread_catalog import current_catalog_review_context,verify_review_extension'),("ROOT/'build/literature-review/hifiasm-runtime'","ROOT/'build/literature-review/rsubread-runtime'"),("'gseapy_current_evidence','hifiasm_execution_evidence']","'gseapy_current_evidence','hifiasm_execution_evidence','rsubread_matrix_evidence','rsubread_result_evidence','rsubread_execution_evidence']"),("checks[name]=dict(status=result['status']","if isinstance(result,tuple):result=result[0]\n   checks[name]=dict(status=result['status']"),("ROOT/'tools/literature/hifiasm_catalog.py'","ROOT/'tools/literature/rsubread_catalog.py'"),('passed-twenty-seven-frozen-and-additive-hifiasm-gates','passed-thirty-frozen-and-additive-Rsubread-gates'),('current_reviews=1000','current_reviews=1003'),('Passed27 unchanged/additive gates','Passed30 unchanged/additive gates')]:assert text.count(old)==1,old;text=text.replace(old,new)
    audit=(tools/'audit_hifiasm_catalog.py').read_text(encoding='utf8')
    changes=[('from hifiasm_catalog import current_catalog_review_context,load_catalog_registry','from rsubread_catalog import current_catalog_review_context,load_catalog_registry'),("ROOT/'build/literature-review/hifiasm-runtime'","ROOT/'build/literature-review/rsubread-runtime'"),('len(reviews)==1000','len(reviews)==1003'),('passed-250-primary-XML-DOI-and-1000-reviewed-roles-with-hifiasm-availability','passed-250-primary-XML-DOI-and-1003-reviewed-roles-with-Rsubread-availability'),("ROOT/'tools/literature/hifiasm_catalog.py'","ROOT/'tools/literature/rsubread_catalog.py'"),('all_non_hifiasm_source_roles_preserved=True,refined_existing_roles=0','all_non_rsubread_source_roles_preserved=True,refined_existing_roles=3'),('2465 observations and1000 roles','2465 observations and1003 roles'),('hifiasm has seven separately bound roles','Rsubread has six separately bound roles')]
    for old,new in changes:assert audit.count(old)==1,old;audit=audit.replace(old,new)
    return {tools/'run_rsubread_catalog_checks.py':text.encode(),tools/'audit_rsubread_catalog.py':audit.encode()}


def verify():
    for path,raw in generated().items():assert path.read_bytes()==raw,'Predecessor gate/audit derivation differs: '+str(path)


if __name__=='__main__':
    for path,raw in generated().items():
        if path.exists():assert path.read_bytes()==raw
        else:path.write_bytes(raw)
    verify();print('Prepared30 unchanged/additive gates and full250-source current-role audit')
