# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Finalize error naming/quarantine only; preserve numeric classes and all prior candidates."""
import argparse
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin
from build_htseq_tools_development import BASE,JAR as PARENT
from validate_deseq2_inference import JAVA21

JAR=BASE/'tools-development-final/omicsmatic-0.3.7-htseq-tools-development.jar'


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists() and not JAR.exists();out.mkdir()
    intake=BASE/'diagnostic-source-intake-1'
    changes={
        'HtseqCountRuntime.java':(b'        catch(RsemProcess.UnavailableDependency missing){throw new Unavailable("Required HTSeq process supervision helper is missing or forbidden: "+missing.getMessage(),missing);}\n',
            b'        catch(RsemProcess.UnavailableDependency missing){throw new Unavailable("Required HTSeq process supervision helper is missing or forbidden: "+missing.getMessage(),missing);}\n        catch(IOException failed){throw new IOException("Original HTSeq "+mode+" failed: "+String.valueOf(failed.getMessage()).replace("RSEM","HTSeq")+". Read "+work.resolve("process.stderr")+" and process.json; no result is admitted.",failed);}\n'),
        'HtseqFileCounting.java':(b'List.of("counts.tsv","summary.tsv","COMPLETED.json","analysis-status.json","WARNINGS.md")',
            b'List.of("counts.tsv","summary.tsv","verification.json","report.json","COMPLETED.json","analysis-status.json","WARNINGS.md")')}
    sources=[];derivation=[]
    for name,(a,b) in changes.items():
        source=ROOT/'src/main/java/org/jlinalg/cli'/name;before=(intake/name).read_bytes();after=source.read_bytes()
        assert before.count(a)==1 and before.replace(a,b)==after
        derivation.append(dict(source=source.relative_to(ROOT).as_posix(),before=byte_pin(before),after=byte_pin(after),scope='Only named error propagation or incomplete-report quarantine; complete explicit before/after source retained'))
        sources.append(source)
    classes=out/'classes';classes.mkdir()
    argv=list(map(str,[Path(JAVA21).with_name('javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',PARENT,'-d',classes,*sources]))
    result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120)
    (out/'compile.stdout').write_bytes(result.stdout);(out/'compile.stderr').write_bytes(result.stderr);save(out/'compile.process.json',dict(command=argv,exit_code=result.returncode));assert result.returncode==0
    replacements={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
    allowed={'org/jlinalg/cli/HtseqCountRuntime.class','org/jlinalg/cli/HtseqCountRuntime$Unavailable.class','org/jlinalg/cli/HtseqFileCounting.class'};assert set(replacements)<=allowed
    JAR.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(PARENT) as old,zipfile.ZipFile(JAR,'x') as new:
        for entry in old.infolist():new.writestr(entry,replacements.get(entry.filename,old.read(entry.filename)))
    with zipfile.ZipFile(PARENT) as old,zipfile.ZipFile(JAR) as new:
        assert new.testzip() is None and old.namelist()==new.namelist();changed=[n for n in old.namelist() if old.read(n)!=new.read(n)];assert set(changed)<=allowed
        entries={n:dict(before=byte_pin(old.read(n)),after=byte_pin(new.read(n))) for n in changed};unchanged=len(old.namelist())-len(changed)
    save(out/'COMPLETED.json',dict(status='built-final-HTSeq-error-naming-and-quarantine-candidate',artifact=pin(JAR),parent=pin(PARENT),
        unchanged_parent_entries=unchanged,changed_entries=entries,source_derivation=derivation,numeric_and_native_classes_preserved=True,catalog_credit=False))
    print('Built final diagnostics candidate; exact two source edits and unchanged numeric/native entries recorded')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable diagnostic-only derivation checks')
    main()
