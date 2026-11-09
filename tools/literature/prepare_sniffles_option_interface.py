# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build isolated option-interface candidates without changing sources used by live tests.

Only the legacy segment-size versus modern alignment-length API is changed.
Candidate artifacts grant no calling or catalog availability credit.
"""
import argparse,difflib,json,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    base=ROOT/'build/literature-review/sniffles-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    folder=ROOT/'src/main/java/org/jlinalg/cli';names=['SnifflesWorkflowRequest.java','SnifflesWorkflowCli.java']
    before={name:(folder/name).read_text(encoding='utf8') for name in names};after=dict(before)
    def replace(name,old,new):
        assert after[name].count(old)==1,(name,old);after[name]=after[name].replace(old,new)
    request=names[0];workflow=names[1]
    replace(request,'Integer minAlignmentLength, String sampleId)', 'Integer minAlignmentLength, Integer legacyMinSegmentSize, String sampleId)')
    replace(request,'"minimum_alignment_length", "sample_id"', '"minimum_alignment_length", "legacy_minimum_segment_size", "sample_id"')
    replace(request,'            analyses.add(new Analysis(id, edition, inputId, tandem, genotype, phase, legacyGenotype,',
        '            Integer alignmentLength = optionalInt(value, "minimum_alignment_length", 0, 100000000);\n'
        '            Integer legacySegmentSize = optionalInt(value, "legacy_minimum_segment_size", 0, 100000000);\n'
        '            if (edition == SnifflesVcf.Edition.V1_0_7 && alignmentLength != null)\n'
        '                throw new SnifflesProcess.UnavailableDependency("Legacy --min_seq_size controls event segments, not modern query-alignment length. Use explicit legacy_minimum_segment_size for that source option; no equivalent read filter is substituted. " + SnifflesRuntimeProfile.ACTION);\n'
        '            if (edition != SnifflesVcf.Edition.V1_0_7 && legacySegmentSize != null)\n'
        '                throw new SnifflesProcess.UnavailableDependency("legacy_minimum_segment_size is only the original 1.0.7 --min_seq_size option. Modern alignment filtering uses minimum_alignment_length; no edition or flag is substituted. " + SnifflesRuntimeProfile.ACTION);\n'
        '            analyses.add(new Analysis(id, edition, inputId, tandem, genotype, phase, legacyGenotype,')
    replace(request,'optionalInt(value, "mapq", 0, 255), optionalInt(value, "minimum_alignment_length", 1, 100000000), sample));',
        'optionalInt(value, "mapq", 0, 255), alignmentLength, legacySegmentSize, sample));')
    replace(workflow,'option(result, legacy ? "--min_seq_size" : "--min-alignment-length", analysis.minAlignmentLength());',
        'option(result, "--min-alignment-length", analysis.minAlignmentLength());\n'
        '        option(result, "--min_seq_size", analysis.legacyMinSegmentSize());')
    patch=[];source=[]
    for name in names:
        relative='src/main/java/org/jlinalg/cli/'+name
        old=out/'source-before'/relative;new=out/'source-candidate'/relative;old.parent.mkdir(parents=True,exist_ok=True);new.parent.mkdir(parents=True,exist_ok=True)
        old.write_text(before[name],encoding='utf8',newline='\n');new.write_text(after[name],encoding='utf8',newline='\n');source.append(new)
        patch.extend(difflib.unified_diff(before[name].splitlines(True),after[name].splitlines(True),fromfile='a/'+relative,tofile='b/'+relative))
    (out/'option-interface.patch').write_text(''.join(patch),encoding='utf8',newline='\n')
    compilers=[('javac21','execution-build-14','C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe'),
        ('javac25','execution-build-25-5','C:/Program Files/Common Files/Oracle/Java/javapath/javac.exe')]
    artifacts={}
    for label,build,javac in compilers:
        parent=base/build/'sniffles-execution-development.jar';built=json.loads((base/build/'COMPLETED.json').read_bytes())
        assert built['status']=='PASS_ISOLATED_JAVA17_SNIFFLES_EXECUTION_BUILD_ONLY' and pin(parent)=={key:built['artifact'][key] for key in ['bytes','sha256']}
        assert all(pin(ROOT/name)==value for name,value in built['source_pins'].items())
        home=out/label;home.mkdir();classes=home/'classes';classes.mkdir()
        command=[javac,'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,source)]
        run=subprocess.run(command,capture_output=True,timeout=120);save(home/'compile.json',dict(command=command,exit_code=run.returncode))
        (home/'compile.stdout').write_bytes(run.stdout);(home/'compile.stderr').write_bytes(run.stderr);assert run.returncode==0,run.stderr.decode(errors='replace')
        with zipfile.ZipFile(parent) as original:payload={name:original.read(name) for name in original.namelist()}
        changed=[]
        for path in classes.rglob('*.class'):
            name=path.relative_to(classes).as_posix();raw=path.read_bytes();assert name.startswith(('org/jlinalg/cli/SnifflesWorkflowRequest','org/jlinalg/cli/SnifflesWorkflowCli'))
            if payload.get(name)!=raw:changed.append(name)
            payload[name]=raw
        artifact=home/'sniffles-option-interface-preview.jar'
        with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
            for name,raw in sorted(payload.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,raw)
        artifacts[label]=dict(path=artifact.relative_to(ROOT).as_posix(),**pin(artifact),base_artifact=pin(parent),changed_class_family_members=sorted(changed),all_other_class_and_resource_bytes_unchanged=True)
    assert all((folder/name).read_text(encoding='utf8')==text for name,text in before.items())
    save(out/'COMPLETED.json',dict(status='PASS_ISOLATED_SNIFFLES_OPTION_INTERFACE_PREVIEW_BUILD_ONLY',implementation_credit=False,Java_calling_workflow_validated=False,
        current_workspace_sources_unchanged={str((folder/name).relative_to(ROOT)).replace('\\','/'):pin(folder/name) for name in names},
        candidate_sources={path.relative_to(out).as_posix():pin(path) for path in source},artifacts=artifacts,source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},
        apply_requires_byte_identical_full_build_and_validated_options=True,whole_goal_complete=False))
    print('Built both isolated interface candidates; current running-test sources unchanged; no calling/catalog credit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable isolated source/artifact comparisons')
    main()
