# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Real upstream execution followed by an injected receipt failure; no result admission."""
from pathlib import Path
import contextlib, io, os, subprocess, sys, unittest
from unittest.mock import patch
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parent))
import run
from Runtime import HERE,load

class RecordingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.parent=Path(os.environ['ARROW1201_RECORDING_OUT'])
        cls.parent.mkdir(exist_ok=False)
        cls.runtime=Path(os.environ['ARROW1201_TEST_RUNTIME'])
        cls.fixtures=Path(os.environ['ARROW1201_TEST_FIXTURES'])

    def command(self,name,runtime=None):
        out=self.parent/name
        return out,['run.py','--runtime',str(runtime or self.runtime),
            '--request',str(self.fixtures/'requests/read-complete.rds'),
            '--assets',str(self.fixtures/'assets'),'--out',str(out)]

    def test_missing_runtime_skips_dependent_analysis(self):
        out,args=self.command('missing-runtime',self.parent/'absent-provider')
        with patch.object(sys,'argv',args),contextlib.redirect_stdout(io.StringIO()) as console:code=run.main()
        (self.parent/'missing-runtime.console').write_text(console.getvalue())
        self.assertEqual(code,3)
        self.assertFalse((out/'LIVE.json').exists())
        self.assertFalse((out/'COMPLETED.json').exists())
        self.assertIn('SKIP3',load(out/'FAILURE.json')['status'])
        self.assertIn('NEW writable', (out/'WARNINGS.md').read_text())

    def test_real_original_return_then_manifest_permission_error_is_fail2(self):
        out,args=self.command('post-original-recording-failure')
        original=run.save
        def injected(path,value):
            if Path(path).name=='ORIGINAL-FILES.json':
                raise PermissionError('Injected manifest recording denial after REAL original Arrow return')
            return original(path,value)
        with patch.object(sys,'argv',args),patch.object(run,'save',injected),contextlib.redirect_stdout(io.StringIO()) as console:
            code=run.main()
        (self.parent/'post-original-recording-failure.console').write_text(console.getvalue())
        self.assertEqual(code,2)
        self.assertEqual(load(out/'original/R-status.json')['exit_code'],0)
        self.assertTrue((out/'original/complete-result.rds').is_file())
        self.assertFalse((out/'COMPLETED.json').exists())
        self.assertIn('FAIL2',load(out/'FINALIZATION_ERROR.json')['status'])
        self.assertFalse(load(out/'FINALIZATION_ERROR.json')['complete_result_admitted'])

    def test_install_forbidden_is_skip3_without_launching_manager(self):
        out=self.parent/'installation-forbidden'
        command=[sys.executable,'-B',str(HERE/'install_runtime.py'),
            '--manager',str(self.parent/'manager-that-must-not-be-launched'),'--out',str(out)]
        child=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,text=True)
        (self.parent/'installation-forbidden.stdout').write_text(child.stdout)
        (self.parent/'installation-forbidden.stderr').write_text(child.stderr)
        self.assertEqual(child.returncode,3)
        self.assertIn('Installation forbidden',child.stdout)
        self.assertFalse((out/'runtime').exists())
        self.assertFalse((out/'INSTALLATION-INPUTS.json').exists())

    def test_same_edition_patched_cpp_skips_before_original_execution(self):
        patched=Path(os.environ['ARROW1201_PATCHED_RUNTIME'])
        out,args=self.command('same-edition-patched-cpp',patched)
        with patch.object(sys,'argv',args),contextlib.redirect_stdout(io.StringIO()) as console:code=run.main()
        (self.parent/'same-edition-patched-cpp.console').write_text(console.getvalue())
        self.assertEqual(code,3)
        self.assertFalse((out/'LIVE.json').exists())
        self.assertFalse((out/'COMPLETED.json').exists())
        self.assertIn('Same-version patched builds are not admitted',load(out/'FAILURE.json')['cause'])

    def test_output_inside_protected_runtime_is_rejected_before_creation(self):
        ready=load(self.runtime/'READY.json')
        forbidden=Path(ready['r_prefix'])/'omicsmatic-forbidden-output-control-20261114-1'
        self.assertFalse(forbidden.exists())
        _,args=self.command('unused-output')
        args[-1]=str(forbidden)
        with patch.object(sys,'argv',args),contextlib.redirect_stdout(io.StringIO()):code=run.main()
        self.assertEqual(code,2)
        self.assertFalse(forbidden.exists())

if __name__=='__main__':unittest.main()
