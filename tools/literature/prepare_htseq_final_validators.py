# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Select the final diagnostic candidate without changing validation algorithms or controls."""
from setup_deseq2_reference import ROOT,pin,save


def main():
    here=ROOT/'tools/literature';records=[]
    specifications=[
        ('validate_htseq_root_workflows.py','validate_htseq_final_workflows.py',b'from build_htseq_tools_development import JAR as TARGET',b'from build_htseq_tools_diagnostics import JAR as TARGET'),
        ('validate_htseq_root_integrity.py','validate_htseq_final_integrity.py',b'from build_htseq_tools_development import JAR as TARGET',b'from build_htseq_tools_diagnostics import JAR as TARGET'),
        ('validate_htseq_tools_file_native.py','validate_htseq_final_file_native.py',b'from build_htseq_tools_development import BASE,JAR as TARGET',b'from build_htseq_tools_diagnostics import BASE,JAR as TARGET'),
        ('validate_htseq_tools_binary_native.py','validate_htseq_final_binary_native.py',b'from build_htseq_tools_development import BASE,JAR',b'from build_htseq_tools_diagnostics import BASE,JAR'),
        ('validate_htseq_binary_example.py','validate_htseq_final_binary_example.py',b'from build_htseq_binary_development import BASE,JAR',b'from build_htseq_tools_diagnostics import BASE,JAR')]
    for old,new,a,b in specifications:
        source=here/old;target=here/new;before=source.read_bytes();assert before.count(a)==1 and not target.exists();target.write_bytes(before.replace(a,b))
        records.append(dict(before_source=old,before=pin(source),after_source=new,after=pin(target),only_change='Candidate import; all original/source/numerical/contract and root invocation checks unchanged'))
    save(ROOT/'build/literature-review/htseq-runtime/final-validator-derivation.json',dict(status='selected-final-diagnostic-candidate-only',records=records))
    print('Selected final candidate in five derived validators; parent validators unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact candidate selection checks')
    main()
