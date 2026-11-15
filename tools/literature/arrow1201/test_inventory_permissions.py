# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise a real denied subtree with UID 65534, rather than accepting a clipped inventory."""
from pathlib import Path
import os, subprocess, sys, unittest
sys.dont_write_bytecode=True
from Runtime import HERE,inventory

class PermissionTest(unittest.TestCase):
    def test_real_denied_subtree_is_fatal(self):
        self.assertEqual(sys.platform,'linux')
        self.assertEqual(os.geteuid(),0,'Run in the permitted private root test environment')
        root=Path(os.environ['ARROW1201_PERMISSION_ROOT'])
        root.mkdir(mode=0o755,exist_ok=False)
        (root/'visible.txt').write_text('visible input retained\n')
        denied=root/'denied';denied.mkdir(mode=0o700)
        (denied/'hidden.txt').write_text('must never silently disappear from an inventory\n')
        denied.chmod(0o000)
        script=('import sys;sys.path.insert(0,'+repr(str(HERE))+');from Runtime import inventory\n'
                'try: inventory('+repr(str(root))+')\n'
                'except PermissionError: print("PASS_REAL_DENIED_SUBTREE_REJECTED");sys.exit(0)\n'
                'raise RuntimeError("Denied subtree silently omitted")\n')
        def demote():
            os.setgroups([]);os.setgid(65534);os.setuid(65534)
        try:
            child=subprocess.run([sys.executable,'-B','-c',script],preexec_fn=demote,
                cwd='/tmp',stdin=subprocess.DEVNULL,capture_output=True,text=True)
            self.assertEqual(child.returncode,0,child.stdout+child.stderr)
            self.assertIn('PASS_REAL_DENIED_SUBTREE_REJECTED',child.stdout)
        finally:denied.chmod(0o700)
        self.assertIn('denied/hidden.txt',inventory(root))

if __name__=='__main__':unittest.main()
