# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independent decimal selection of complete upstream rMATS statistical tables.

This is a reference for the explicitly requested post-fit gate, not a model fit
or a claim that the upstream workflow applied this gate. Duplicate ID headers
remain positional. No source table, identifier or missing value is repaired.
"""
import csv,decimal,io

def project(raw,maximum_raw_p='0.05',minimum_absolute_psi='0.05'):
    text=raw.decode('utf-8',errors='strict');reader=csv.reader(io.StringIO(text,newline=''),delimiter='\t',quoting=csv.QUOTE_NONE);header=next(reader);rows=list(reader)
    assert header.count('PValue')==header.count('IncLevelDifference')==header.count('FDR')==1
    p_index=header.index('PValue');psi_index=header.index('IncLevelDifference');cutoff=decimal.Decimal(maximum_raw_p);minimum=decimal.Decimal(minimum_absolute_psi);assert 0<=cutoff<=1 and 0<=minimum<=1
    selected=[];unassessable=[];decisions=[];missing={}
    for line,cells in enumerate(rows,2):
        assert len(cells)==len(header),'Ragged original result row'
        for column in ['PValue','FDR','IncLevel1','IncLevel2','IncLevelDifference']:
            tokens=cells[header.index(column)].split(',');count=tokens.count('NA')
            if count:missing[column]=missing.get(column,0)+count
        p=None if cells[p_index]=='NA'else decimal.Decimal(cells[p_index]);psi=None if cells[psi_index]=='NA'else decimal.Decimal(cells[psi_index]);assert p is None or p.is_finite()and 0<=p<=1;assert psi is None or psi.is_finite()and -1<=psi<=1
        if p is None or psi is None:decision='UNASSESSABLE';unassessable.append(cells)
        elif p<cutoff and psi.copy_abs()>=minimum:decision='SELECTED';selected.append(cells)
        else:decision='EXCLUDED'
        decisions.append([str(line),decision])
    return dict(header=header,rows=rows,selected=selected,unassessable=unassessable,decisions=decisions,source_NA_fields=missing,original_ID_column_positions_zero_based=[i for i,value in enumerate(header)if value=='ID'])
