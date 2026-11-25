"""Regression checks for source preservation, scoped admission and atomic rollback."""
from pathlib import Path
import copy
import json
import subprocess
import sys
import unittest
from types import SimpleNamespace

import crossmap_catalog as catalog
import crossmap_catalog_model as model

TEST_ROOT = None


class SourceTransition(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.before = {n: subprocess.check_output(["git", "--no-replace-objects", "show", catalog.BASE + ":" + n],
                                                 cwd=model.ROOT, stdin=subprocess.DEVNULL) for n in model.META}
        cls.roles = json.loads((model.ROOT / "docs/literature/crossmap-source-audit/ROLES.json").read_bytes())
        cls.qualification = json.loads((model.ROOT / "docs/literature/crossmap-workflow-checkpoint/QUALIFICATION.json").read_bytes())

    def test_preserves_unrelated_science_and_exact_existing_review(self):
        after = model.transition(self.before, self.roles, self.qualification)
        result = model.check(self.before, after, self.roles, self.qualification)
        self.assertEqual((result["new_reviews"], result["refined_existing_reviews"], result["reviewed_uses"]), (3, 1, 1772))
        self.assertEqual(result["unchanged_unrelated_observations"], 2720)
        self.assertFalse(result["whole_paper_reproduced"])
        paper = next(p for p in json.loads(after[model.META[0]]) if p["paper_id"] == "P072")
        use = next(u for u in paper["tools"] if u["tool_id"] == "crossmap")
        self.assertIsNone(use["version_reported_near_name"])
        self.assertEqual(use["source_role_qualification"]["reported_version"], "unreported")

    def test_cannot_invent_unreported_genotype_format_or_edition(self):
        roles = copy.deepcopy(self.roles)
        next(r for r in roles["roles"] if r["paper_id"] == "P108")["reported_format"] = "vcf"
        with self.assertRaises(ValueError):
            model.transition(self.before, roles, self.qualification)
        roles = copy.deepcopy(self.roles)
        next(r for r in roles["roles"] if r["paper_id"] == "P072")["raw_source_fields"]["version_reported_near_name"] = "0.6.3"
        with self.assertRaises(ValueError):
            model.transition(self.before, roles, self.qualification)

    def test_incomplete_workflow_cannot_receive_family_credit(self):
        qualification = dict(self.qualification, admitted_captures=25)
        with self.assertRaises(ValueError):
            model.transition(self.before, self.roles, qualification)


class AtomicOwnership(unittest.TestCase):
    def setUp(self):
        self.old_root = catalog.ROOT
        self.root = TEST_ROOT / self._testMethodName
        self.root.mkdir()
        self.out = self.root / "receipt"
        self.out.mkdir()
        catalog.ROOT = self.root

    def tearDown(self):
        catalog.ROOT = self.old_root

    def test_failed_postcheck_restores_old_and_retains_new_bytes(self):
        (self.root / "old.txt").write_bytes(b"original")
        def fail():
            raise RuntimeError("controlled failed postcheck")
        with self.assertRaises(RuntimeError):
            catalog.install(SimpleNamespace(out=self.out), {"old.txt": b"original", "new.txt": None},
                            {"old.txt": b"updated", "new.txt": b"retained failed new bytes"}, fail)
        self.assertEqual((self.root / "old.txt").read_bytes(), b"original")
        self.assertFalse((self.root / "new.txt").exists())
        self.assertEqual((self.out / "retained-failed-metadata/new.txt").read_bytes(), b"retained failed new bytes")

    def test_rollback_preserves_concurrent_unique_change(self):
        (self.root / "old.txt").write_bytes(b"original")
        def concurrent_failure():
            (self.root / "old.txt").write_bytes(b"concurrent unique bytes")
            raise RuntimeError("controlled concurrent postcheck failure")
        with self.assertRaises(RuntimeError):
            catalog.install(SimpleNamespace(out=self.out), {"old.txt": b"original"}, {"old.txt": b"updated"}, concurrent_failure)
        self.assertEqual((self.root / "old.txt").read_bytes(), b"concurrent unique bytes")
        self.assertEqual(json.loads((self.out / "ROLLBACK.json").read_bytes())["concurrent_changes_preserved"], ["old.txt"])


if __name__ == "__main__":
    TEST_ROOT = Path(sys.argv.pop(1)).absolute()
    TEST_ROOT.mkdir()
    unittest.main()
