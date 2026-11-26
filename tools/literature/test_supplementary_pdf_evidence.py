"""Scientific provenance controls using generated PDFs; no article text is copied."""
import copy
from io import BytesIO
import os
from pathlib import Path
import unittest
from unittest.mock import patch

from pypdf import PdfWriter
from pypdf.generic import DecodedStreamObject, DictionaryObject, NameObject
import supplementary_pdf_evidence as evidence


class PdfEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.root = Path(os.environ["SUPPLEMENT_PDF_TEST_ROOT"]).resolve()
        cls.root.mkdir(exist_ok=False)
        writer = PdfWriter()
        for text in ("ExampleTool alpha ExampleTool", "DifferentMethod beta"):
            page = writer.add_blank_page(width=300, height=200)
            font = DictionaryObject({NameObject("/Type"): NameObject("/Font"), NameObject("/Subtype"): NameObject("/Type1"), NameObject("/BaseFont"): NameObject("/Helvetica")})
            page[NameObject("/Resources")] = DictionaryObject({NameObject("/Font"): DictionaryObject({NameObject("/F1"): writer._add_object(font)})})
            content = DecodedStreamObject()
            content.set_data(("BT /F1 12 Tf 20 100 Td (" + text + ") Tj ET").encode())
            page[NameObject("/Contents")] = writer._add_object(content)
        stream = BytesIO()
        writer.write(stream)
        cls.pdf = cls.root / "synthetic-original.pdf"
        cls.pdf.write_bytes(stream.getvalue())
        cls.record = evidence.describe(cls.pdf, "https://example.org/supplement.pdf", 1, 1, "ExampleTool")

    def test_physical_one_based_pages_and_all_literal_occurrences(self):
        self.assertEqual((self.record["page_number"], self.record["page_count"]), (1, 2))
        self.assertEqual(len(self.record["named_occurrences"]), 2)
        self.assertEqual(evidence.verify(self.record, self.pdf)["status"], "PASS_EXACT_SUPPLEMENTARY_PDF_LOCATOR")
        second = evidence.describe(self.pdf, self.record["source_url"], 1, 2, "DifferentMethod")
        self.assertNotEqual(second["page_text_sha256"], self.record["page_text_sha256"])

    def test_no_source_excerpt_in_public_locator(self):
        self.assertEqual(set(self.record), evidence.FIELDS)
        self.assertNotIn("alpha", str(self.record))
        self.assertNotIn("evidence_paragraph_index", self.record)

    def test_reject_changed_file_bytes_even_when_page_text_is_unchanged(self):
        changed = self.root / "changed-trailing-comment.pdf"
        changed.write_bytes(self.pdf.read_bytes() + b"\n% changed bytes\n")
        with self.assertRaisesRegex(ValueError, "Changed PDF bytes"):
            evidence.verify(self.record, changed)

    def test_reject_changed_page_text_hash(self):
        record = copy.deepcopy(self.record)
        record["page_text_sha256"] = "0" * 64
        with self.assertRaises(ValueError):
            evidence.verify(record, self.pdf)

    def test_reject_changed_name_offsets(self):
        record = copy.deepcopy(self.record)
        record["named_occurrences"][0]["start"] += 1
        with self.assertRaises(ValueError):
            evidence.verify(record, self.pdf)

    def test_reject_wrong_page_without_search_or_normalization_fallback(self):
        with self.assertRaisesRegex(ValueError, "Literal tool name absent"):
            evidence.describe(self.pdf, self.record["source_url"], 1, 2, "ExampleTool")

    def test_reject_zero_boolean_and_out_of_range_page(self):
        for page in (0, True, 3):
            with self.subTest(page=page), self.assertRaises(ValueError):
                evidence.describe(self.pdf, self.record["source_url"], 1, page, "ExampleTool")

    def test_reject_credentialed_or_non_https_url(self):
        for url in ("http://example.org/file.pdf", "https://secret@example.org/file.pdf", "https://example.org/file.pdf#page=1"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                evidence.describe(self.pdf, url, 1, 1, "ExampleTool")

    def test_reject_unknown_public_fields(self):
        record = copy.deepcopy(self.record)
        record["evidence_text"] = "must not be published"
        with self.assertRaisesRegex(ValueError, "Unknown/missing"):
            evidence.verify(record, self.pdf)

    def test_missing_exact_parser_is_skip_not_a_validated_result(self):
        with patch.object(evidence, "version", return_value="different-version"):
            with self.assertRaises(evidence.PrerequisiteUnavailable):
                evidence.verify(self.record, self.pdf)

    def test_missing_source_is_skip(self):
        with self.assertRaises(evidence.PrerequisiteUnavailable):
            evidence.verify(self.record, self.root / "absent.pdf")

    def test_reject_changed_parser_diagnostic_population(self):
        record = copy.deepcopy(self.record)
        record["parser_diagnostics"] = [dict(level="WARNING", message_sha256="0" * 64, count=1)]
        with self.assertRaises(ValueError):
            evidence.verify(record, self.pdf)

    def test_reject_non_pdf(self):
        path = self.root / "non-pdf.txt"
        path.write_text("Not a PDF", encoding="utf8")
        with self.assertRaisesRegex(ValueError, "non-PDF"):
            evidence.describe(path, self.record["source_url"], 1, 1, "ExampleTool")

    def test_parser_warning_is_captured_and_remains_visible(self):
        import logging
        from pypdf import PdfReader
        def reading(*args, **kwargs):
            logging.getLogger("pypdf").warning("synthetic parser diagnostic")
            return PdfReader(*args, **kwargs)
        with self.assertLogs("pypdf", level="WARNING") as captured:
            with patch("pypdf.PdfReader", side_effect=reading):
                record = evidence.describe(self.pdf, self.record["source_url"], 1, 1, "ExampleTool")
        self.assertEqual(len(captured.output), 1)
        self.assertEqual(record["parser_diagnostics"], [dict(level="WARNING", message_sha256=evidence.identity(b"synthetic parser diagnostic")["sha256"], count=1)])


if __name__ == "__main__":
    unittest.main()
