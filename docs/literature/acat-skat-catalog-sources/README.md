# ACAT/SKAT primary-source crosswalk

CENSUS.json binds all 250 frozen XML/DOI identities and the 19 candidate papers,
95 candidate paragraphs and one P220 table hash. It records 22 individually read
tool roles and five explicit negative decisions. A candidate match does not
establish executed software or a package edition. All primary text and the real
aggregate table stay private.

The three complete P190 supplement pages were extracted with pypdf 6.21.4,
rendered with system Poppler and visually inspected. AUXILIARY.json records
full-page text and image hashes, the exact publisher PDF identity and the source
URL. No PDF text, render or publisher full text is redistributed here.

Run audit_acat_skat_source_roles.py with exact permitted primary-cache,
supplement-pdf and NEW build output paths for a fresh audit. Missing sources or
dependencies produce SKIP3; changed identities/census produce FAIL2. Retain
failures and resolve the named cause; never refresh old pins to accept changed
source. The census itself credits no family and changes no canonical metadata.
