# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Actual static-parser controls, including source effects that must never run."""
from pathlib import Path
import argparse,contextlib,hashlib,io,json,subprocess,sys,unittest
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parent))
import source_audit

ROOT=Path(__file__).resolve().parents[3]
P=argparse.ArgumentParser(description=__doc__)
P.add_argument('--rscript',type=Path,required=True);P.add_argument('--out',type=Path,required=True)
OPTIONS=P.parse_args();OPTIONS.out=OPTIONS.out.resolve();OPTIONS.out.mkdir(exist_ok=False)
if not OPTIONS.rscript.is_file():raise SystemExit('SKIP3: supply an existing permitted Rscript; no parser controls were run')

class StaticAuditTest(unittest.TestCase):
    def prepare(self):
        folder=OPTIONS.out/self._testMethodName;folder.mkdir()
        source=folder/'source';(source/'shinyapps/idep96').mkdir(parents=True)
        canary=folder/'SOURCE-EXECUTION-WOULD-BE-AN-ERROR.txt'
        effect='writeLines("SOURCE MUST NOT EXECUTE", '+json.dumps(canary.as_posix())+')\n'
        (source/'shinyapps/idep96/server.R').write_text(effect+'''
iDEPversion="iDEP 0.96"
maxSamples=200;maxSamplesDefault=30;maxComparisons=50;maxFactors=6
detectGroups <- function(x,sampleInfo=NULL) {stop("must not execute");x}
DEG.DESeq2 <- function(x, y=source("NEVER_READ_DEFAULT.R")) {stop("must not execute");x}
shinyServer(function(input,output,session) {
 readData <- reactive({stop("must not execute");matrix(1)[,]})
})
''',encoding='utf-8')
        (source/'shinyapps/idep96/iDEP_core_functions.R').write_text(effect+'''
install.packages("NEVER_INSTALL_THIS_AUDIT_CANARY")
detectGroups <- function(x) x
DEG.DESeq2 <- function(x) {stop("different standalone helper");x}
''',encoding='utf-8')
        (source/'shinyapps/idep96/ui.R').write_text(effect+'''
iDEPversion="iDEP.96"
numericInput("minCounts",label="CPM",value=0.5)
checkboxInput("noIDConversion",label="ID conversion",value=FALSE)
''',encoding='utf-8')
        profile=folder/'SOURCE.json'
        self.profile(source,profile)
        return folder,source,profile,canary

    def profile(self,source,profile):
        rows={}
        for path in source.rglob('*.R'):
            raw=path.read_bytes()
            rows[path.relative_to(source).as_posix()]=dict(**source_audit.pin(path),git_blob_sha=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest())
        source_audit.write(profile,dict(repository_commit='synthetic-audit-control-not-upstream',members=rows))

    def call(self,folder,source,profile,rscript=None,hook=None):
        out=folder/'audit'
        args=argparse.Namespace(source=source,profile=profile,rscript=rscript or OPTIONS.rscript,out=out)
        if hook:
            so,se=io.StringIO(),io.StringIO()
            with contextlib.redirect_stdout(so),contextlib.redirect_stderr(se):code=source_audit.run(args,hook)
            (folder/'controller.stdout').write_text(so.getvalue(),encoding='utf-8');(folder/'controller.stderr').write_text(se.getvalue(),encoding='utf-8')
            source_audit.write(folder/'CONTROLLER.json',dict(exit_code=code,kind='actual parser with private recording-failure hook'))
        else:
            command=[sys.executable,'-B',str(Path(source_audit.__file__)),'--source',str(source),'--profile',str(profile),'--rscript',str(args.rscript),'--out',str(out)]
            run=subprocess.run(command,capture_output=True,timeout=90)
            (folder/'controller.stdout').write_bytes(run.stdout);(folder/'controller.stderr').write_bytes(run.stderr)
            code=run.returncode;source_audit.write(folder/'CONTROLLER.json',dict(command=command,exit_code=code))
        return code,out

    def test_actual_parser_never_executes_source_and_retains_default_argument_calls(self):
        folder,source,profile,canary=self.prepare();code,out=self.call(folder,source,profile)
        self.assertEqual(code,0);self.assertFalse(canary.exists())
        facts=json.loads((out/'SOURCE-FACTS.json').read_bytes())
        self.assertFalse(facts['original_app_or_scientific_expressions_evaluated'])
        self.assertFalse(facts['server_and_standalone_helpers']['DEG.DESeq2']['normalized_R_AST_equal'])
        self.assertEqual(facts['implementation_credit_added'],0)
        self.assertIn('NEVER_INSTALL_THIS_AUDIT_CANARY',(out/'ast/calls.tsv').read_text())
        self.assertIn('NEVER_READ_DEFAULT.R',(out/'ast/calls.tsv').read_text())

    def test_missing_Rscript_skips_without_launching_a_parser(self):
        folder,source,profile,canary=self.prepare();code,out=self.call(folder,source,profile,folder/'not-installed-Rscript')
        self.assertEqual(code,3);self.assertFalse((out/'LIVE.json').exists());self.assertFalse((out/'COMPLETED.json').exists());self.assertFalse(canary.exists())

    def test_source_hash_change_fails_before_parser(self):
        folder,source,profile,canary=self.prepare()
        with (source/'shinyapps/idep96/server.R').open('a') as handle:handle.write('\n# changed source\n')
        code,out=self.call(folder,source,profile)
        self.assertEqual(code,2);self.assertFalse((out/'LIVE.json').exists());self.assertFalse((out/'COMPLETED.json').exists())

    def test_missing_source_skips_without_running_available_other_sources(self):
        folder,source,profile,canary=self.prepare();data=json.loads(profile.read_bytes())
        data['members']['absent.R']=dict(bytes=0,sha256=hashlib.sha256(b'').hexdigest(),git_blob_sha='absent')
        source_audit.write(profile,data);code,out=self.call(folder,source,profile)
        self.assertEqual(code,3);self.assertFalse((out/'LIVE.json').exists());self.assertFalse(canary.exists())

    def test_malformed_R_preserves_actual_parse_error_and_admits_nothing(self):
        folder,source,profile,canary=self.prepare();(source/'shinyapps/idep96/server.R').write_text('function(\n')
        self.profile(source,profile);code,out=self.call(folder,source,profile)
        self.assertEqual(code,2);self.assertFalse((out/'COMPLETED.json').exists())
        self.assertNotEqual(json.loads((out/'PROCESS.json').read_bytes())['exit_code'],0)
        self.assertGreater((out/'process.stderr').stat().st_size,0);self.assertFalse(canary.exists())

    def test_existing_output_is_preserved(self):
        folder,source,profile,canary=self.prepare();out=folder/'audit';out.mkdir();(out/'sentinel').write_bytes(b'preserve existing data')
        code,out=self.call(folder,source,profile)
        self.assertEqual(code,2);self.assertEqual(list(out.iterdir()),[out/'sentinel']);self.assertEqual((out/'sentinel').read_bytes(),b'preserve existing data')

    def test_failure_recording_after_parser_return_keeps_no_completion(self):
        folder,source,profile,canary=self.prepare()
        def hook(phase,out):
            if phase=='AFTER_PARSER':(out/'MANIFEST.json').mkdir()
        code,out=self.call(folder,source,profile,hook=hook)
        self.assertEqual(code,2);self.assertFalse((out/'COMPLETED.json').exists())
        self.assertEqual(json.loads((out/'PROCESS.json').read_bytes())['exit_code'],0)
        self.assertTrue((out/'ast/RETURN.txt').exists());self.assertFalse(canary.exists())

    def test_failure_after_completion_promotion_revokes_completion(self):
        folder,source,profile,canary=self.prepare()
        def hook(phase,out):
            if phase=='AFTER_COMPLETE':raise OSError('actual post-completion recording control')
        code,out=self.call(folder,source,profile,hook=hook)
        self.assertEqual(code,2);self.assertFalse((out/'COMPLETED.json').exists());self.assertEqual(len(list(out.glob('COMPLETED-UNADMITTED-*.json'))),1)
        self.assertTrue((out/'FAILURE.json').exists());self.assertFalse(canary.exists())

suite=unittest.defaultTestLoader.loadTestsFromTestCase(StaticAuditTest)
result=unittest.TextTestRunner(verbosity=2).run(suite)
source_audit.write(OPTIONS.out/'TESTS.json',dict(tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped)))
if result.wasSuccessful():source_audit.write(OPTIONS.out/'COMPLETED.json',dict(status='PASS_EIGHT_ACTUAL_STATIC_SOURCE_PARSER_CANARY_AND_FAILURE_CONTROLS',tests=8,analysis_executed=False,implementation_credit_added=0))
raise SystemExit(0 if result.wasSuccessful() else 2)
