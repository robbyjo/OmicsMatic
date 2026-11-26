"""Exact supplementary-PDF page locators; source text remains in the private cache.

PDF pages are physical, one-based pages. Character offsets address the unmodified
UTF-8-decoded Python string returned by the explicitly recorded pypdf version.
This verifies a locator, not the accuracy/completeness of its usage interpretation.
"""
from collections import Counter
from importlib.metadata import PackageNotFoundError, version
from io import BytesIO
import hashlib
import logging
from pathlib import Path
import re
from urllib.parse import urlsplit

MAX_PDF_BYTES = 100 * 1024 * 1024
MAX_PAGES = 2000
MAX_PAGE_CHARACTERS = 2000000
MAX_MESSAGES = 10000
FIELDS = frozenset({"source_kind", "source_url", "article_version", "source_identity",
                    "page_number", "page_count", "page_text_sha256", "text_extractor",
                    "named_tool", "named_occurrences", "parser_diagnostics"})


class PrerequisiteUnavailable(RuntimeError):
    """SKIP3: an exact permitted source/parser is unavailable."""


def require(condition, message):
    if not condition:
        raise ValueError(message)


def identity(raw):
    return {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def extractor():
    try:
        installed = version("pypdf")
    except PackageNotFoundError as error:
        raise PrerequisiteUnavailable("Install the recorded pypdf version where permitted") from error
    return dict(library="pypdf", version=installed, method="PageObject.extract_text",
                extraction_mode="plain", strict=True, normalization="none")


class _Messages(logging.Handler):
    def __init__(self):
        super().__init__(logging.WARNING)
        self.messages = Counter()

    def emit(self, record):
        require(sum(self.messages.values()) < MAX_MESSAGES, "PDF parser diagnostic capacity exceeded")
        message = record.getMessage()
        require(len(message) <= 1048576, "PDF parser diagnostic length exceeded")
        self.messages[(record.levelname, hashlib.sha256(message.encode()).hexdigest())] += 1


def describe(pdf, source_url, article_version, page_number, named_tool):
    """Create an excerpt-free locator from exact local PDF bytes and a literal name."""
    parts = urlsplit(source_url)
    require(parts.scheme == "https" and parts.hostname and not parts.username and not parts.password
            and not parts.fragment, "A credential-free HTTPS source URL without fragment is required")
    require(type(article_version) is int and article_version > 0, "Invalid article version")
    require(type(page_number) is int and 1 <= page_number <= MAX_PAGES, "Invalid one-based PDF page")
    require(isinstance(named_tool, str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9 ._+/-]{0,199}", named_tool),
            "Invalid literal tool name")
    path = Path(pdf)
    try:
        size = path.stat().st_size
        require(0 < size <= MAX_PDF_BYTES, "PDF byte capacity exceeded or empty source")
        with path.open("rb") as stream:
            raw = stream.read(MAX_PDF_BYTES + 1)
    except (FileNotFoundError, PermissionError) as error:
        raise PrerequisiteUnavailable("Restore the exact permitted supplementary PDF: " + str(path)) from error
    require(len(raw) == size and raw.startswith(b"%PDF-"), "Changed/non-PDF source")
    engine = extractor()
    from pypdf import PdfReader
    messages = _Messages()
    logger = logging.getLogger("pypdf")
    fallback = logging.StreamHandler() if not logger.hasHandlers() else None
    if fallback is not None:
        logger.addHandler(fallback)  # Preserve logging's otherwise-used stderr fallback.
    logger.addHandler(messages)  # Existing stderr/log handlers remain active.
    try:
        reader = PdfReader(BytesIO(raw), strict=True)
        require(not reader.is_encrypted, "Encrypted PDF requires an explicitly supported source")
        count = len(reader.pages)
        require(1 <= count <= MAX_PAGES and page_number <= count, "PDF page outside complete source")
        text = reader.pages[page_number - 1].extract_text(extraction_mode="plain")
        require(isinstance(text, str) and 0 < len(text) <= MAX_PAGE_CHARACTERS,
                "Empty or oversized PDF text; no OCR/text normalization fallback is inferred")
    finally:
        logger.removeHandler(messages)
        if fallback is not None:
            logger.removeHandler(fallback)
    spans = [{"start": match.start(), "end": match.end()} for match in re.finditer(re.escape(named_tool), text)]
    require(spans, "Literal tool name absent from the selected original PDF page")
    return dict(source_kind="supplementary-pdf", source_url=source_url,
                article_version=article_version, source_identity=identity(raw), page_number=page_number,
                page_count=count, page_text_sha256=hashlib.sha256(text.encode()).hexdigest(),
                text_extractor=engine, named_tool=named_tool, named_occurrences=spans,
                parser_diagnostics=[dict(level=level, message_sha256=digest, count=n)
                                    for (level, digest), n in sorted(messages.messages.items())])


def verify(record, pdf):
    """Reject changed locators/bytes/parser semantics; never rewrite evidence to pass."""
    require(isinstance(record, dict) and set(record) == FIELDS, "Unknown/missing PDF locator fields")
    require(record["source_kind"] == "supplementary-pdf", "Wrong supplementary evidence kind")
    if record["text_extractor"] != extractor():
        raise PrerequisiteUnavailable("Use the exact recorded PDF parser/configuration: " + str(record["text_extractor"]))
    actual = describe(pdf, record["source_url"], record["article_version"], record["page_number"], record["named_tool"])
    require(actual == record, "Changed PDF bytes/page/text/name offsets or parser diagnostics")
    return dict(status="PASS_EXACT_SUPPLEMENTARY_PDF_LOCATOR", page_number=record["page_number"],
                named_occurrences=len(record["named_occurrences"]), source_identity=record["source_identity"])
