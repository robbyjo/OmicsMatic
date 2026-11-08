# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Complete original record comparison, retaining raw bytes; only source-unordered LoF_info token order may differ."""
from collections import Counter
from probe_loftee_workflow import table

def compare_tables(left,right,extended=False):
    assert left['CSQ_header']==right['CSQ_header'] and left['column_header']==right['column_header'],'CSQ/sample header differs'
    assert len(left['records'])==len(right['records']),'Original record count differs'
    names=left['CSQ_header'].split('Format: ',1)[1].split('"',1)[0].split('|');info_index=names.index('LoF_info');strict=0;unordered=0
    for first,second in zip(left['records'],right['records']):
        if first==second:strict+=1;continue
        assert extended,'Original record differs outside explicitly extended-splice comparison'
        a=first.split('\t');b=second.split('\t');assert a[:7]+a[8:]==b[:7]+b[8:],'Original identity/site/sample/genotype field differs'
        def split_info(text):
            fields=text.split(';');selected=[i for i,f in enumerate(fields) if f.startswith('CSQ=')];assert len(selected)==1;index=selected[0];return fields[:index],fields[index][4:].split(','),fields[index+1:]
        before_a,csq_a,after_a=split_info(a[7]);before_b,csq_b,after_b=split_info(b[7]);assert before_a==before_b and after_a==after_b and len(csq_a)==len(csq_b),'Original INFO/annotation count or sequence differs'
        changed=False
        for x,y in zip(csq_a,csq_b):
            fields_x=x.split('|');fields_y=y.split('|');assert len(fields_x)==len(fields_y)==len(names)
            assert fields_x[:info_index]+fields_x[info_index+1:]==fields_y[:info_index]+fields_y[info_index+1:],'Consequence, transcript, allele, confidence, filter, flag or other CSQ field differs'
            assert Counter(fields_x[info_index].split('&'))==Counter(fields_y[info_index].split('&')),'Original LoF_info literal tokens or their multiplicities differ'
            changed|=fields_x[info_index]!=fields_y[info_index]
        assert changed;unordered+=1
    return dict(variants=len(left['records']),byte_exact_records=strict,records_with_only_LoF_info_token_order_difference=unordered,all_literal_tokens_and_multiplicities_equal=True,all_other_fields_and_annotation_order_exact=True,raw_outputs_modified=False,scope='LoF.pm each-hash iteration in extended splice feature reporting; no numeric tolerance, omission, identifier repair or output rewriting')

def compare_files(left,right,extended=False):return compare_tables(table(left),table(right),extended)
