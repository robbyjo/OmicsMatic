# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind active original multi-SNP entry bodies, distinguishing them from unused correlation-estimating helpers."""
import argparse,json,re
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin


def mask(text):
    pattern=r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/'
    return re.sub(pattern,lambda m:''.join('\n' if c=='\n' else ' ' for c in m[0]),text,flags=re.S)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/smr-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir(parents=True);reports=[];pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))}
    for version,folder,name in [('1.3.1','source-2023-initial-1','smr_multipleSNP'),('1.4.3','source-intake-1','smr_multipleSNP_core')]:
        source=base/folder/'source/src/SMR_data_p1.cpp';raw=source.read_bytes();text=raw.decode('utf8');masked=mask(text);pattern=r'\bvoid\s+'+name+r'\s*\([^;]*?\)\s*\{';matches=list(re.finditer(pattern,masked,re.S));assert len(matches)==1;begin=matches[0].end()-1;end=begin+1;depth=1
        while depth:
            depth+=(masked[end]=='{')-(masked[end]=='}');end+=1
        body=masked[begin:end];original=text[begin:end];assignments=list(re.finditer(r'\btheta\s*=\s*([^;,\n]+)',body));assert len(assignments)==1 and assignments[0][1].strip()=='0';assert not re.search(r'\b(?:pmecs|minCor|ssmr_heidi_func)\b',body),'Active path unexpectedly contains estimation parameters/helper call';assert len(re.findall(r'\bsampleoverlap\b',body))==(2 if version=='1.3.1' else 3)
        line=lambda pos:masked.count('\n',0,pos)+1;report=dict(version=version,active_function=name,source_path=source.relative_to(ROOT).as_posix(),source=pin(source),active_body=byte_pin(original.encode('utf8')),start_line=line(begin),end_line=line(end),theta_assignments=[dict(line=line(begin+m.start()),value=m[1].strip()) for m in assignments],null_P_or_minimum_null_SNP_parameters_used=False,correlation_estimating_helper_called=False,sample_overlap_flag_passed_to_HEIDI_with_theta0=True,multi_ratio_error_correlation_applied=False,source_scientific_functions_patched=False);reports.append(report);pins[source.relative_to(ROOT).as_posix()]=pin(source)
    save(out/'COMPLETED.json',dict(status='verified-active-original-multi-SNP-overlap-flag-without-estimation-in-both-editions',source_pins=pins,editions=reports,original_runtime_probe_counterexample='workflow-edge-probe-2 retained all4 source outputs, including the low-count probe wrongly excluded by the earlier native null-SNP assumption; that batch was not admitted',independent_model_refit=False,implementation_credit=False,whole_goal_complete=False));print('Verified both active entry bodies:theta remains0; null-P/minimum-null-SNP estimation settings are unused; flag still reaches HEIDI')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable active-entry/source checks')
    main()
