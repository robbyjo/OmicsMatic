# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Derive root-entry validations by exact entry-point substitutions; preserve prior validators."""
from setup_deseq2_reference import ROOT,pin,save


def main():
    here=ROOT/'tools/literature';records=[]
    pairs=[('validate_htseq_file_count_workflow.py','validate_htseq_root_workflows.py'),
        ('validate_htseq_file_count_integrity.py','validate_htseq_root_integrity.py')]
    for old,new in pairs:
        source=here/old;target=here/new;assert not target.exists();before=source.read_bytes();after=before
        changes=[(b'from build_htseq_file_count_development import TARGET',b'from build_htseq_tools_development import JAR as TARGET'),
            (b"'-cp',linux(TARGET),'org.jlinalg.cli.HtseqToolsCli'",b"'-jar',linux(TARGET),'htseq-tools'"),
            (b"'-cp',str(TARGET),'org.jlinalg.cli.HtseqToolsCli'",b"'-jar',str(TARGET),'htseq-tools'")]
        for a,b in changes:assert after.count(a)==1;after=after.replace(a,b)
        target.write_bytes(after);records.append(dict(before_source=old,before=pin(source),after_source=new,after=pin(target),only_changes='Candidate import plus Linux/Windows closed root-entry invocation'))
    pairs=[('validate_htseq_file_count_native.py','validate_htseq_tools_file_native.py',b'from build_htseq_file_count_development import BASE,TARGET',b'from build_htseq_tools_development import BASE,JAR as TARGET'),
        ('validate_htseq_binary_native.py','validate_htseq_tools_binary_native.py',b'from build_htseq_binary_development import BASE,JAR',b'from build_htseq_tools_development import BASE,JAR')]
    for old,new,a,b in pairs:
        source=here/old;target=here/new;before=source.read_bytes();assert before.count(a)==1 and not target.exists();target.write_bytes(before.replace(a,b))
        records.append(dict(before_source=old,before=pin(source),after_source=new,after=pin(target),only_changes='Candidate import; all numerical/source/contract tests and actual classloader guards unchanged'))
    save(ROOT/'build/literature-review/htseq-runtime/root-validator-derivation.json',dict(status='derived-root-validators-by-exact-entry-only-substitutions',records=records))
    print('Derived root COUNT/VERIFY/integrity and final-artifact native validators; parent validators unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact derivation checks')
    main()
