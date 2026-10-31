"""Verify and prepare synthetic complete-family Fisher workflows in a NEW directory."""
from pathlib import Path
import argparse
import hashlib
import json
import stat
import zipfile

ARCHIVE_SHA256 = '3d526fd7a66143ed89f5a6e7f89992d742bebbb948e3154462dec042c27b20d9'
MEMBERS = {'counts.json': '6864ce8e4e2ecc2960b57662300c758e263b03c2a89df6bdd2b885a747393198', 'exact.json': '31071f6e3a5b7a8879b83effd125dbec5ed65c8a85bda66960da1e6454d19ed9', 'input/association-families.tsv': 'fec15150556a44853c6f093688cad65fa955b0e164cea234e9da312cae24bf5c', 'input/association-members.tsv': '3df279f777784d81160f94d8ee0acd4f7ab15a2ee17976515eb7854b7f6afb5a', 'input/comparisons.tsv': 'cf080dbb871faf5e6bede7cc76f1d00e181404b5dea938a99c8160fd7e2f8713', 'input/count-families.tsv': 'bcb6e24c9ad208fe4741853ff89d8910134755e40651c6967607a92dd7fb0e21', 'input/count-plan.tsv': '3ef20b3c032cca1eb4c3a83a694eb8a99ca81135c87ac7781028a0411620f3b3', 'input/counts.tsv': 'be18468e6401be6f4958fe67198895465082e6d5b9d4de36c5d94d9dc77e01e9', 'input/fisher-families.tsv': '5c7c488ac665679e87a2ad6499134ba57adab6f1b866b76c45dfb59659e9522b', 'input/groups.tsv': '657c43179251389e8187b29a75026ec0183fb97992192dc19ae316b66d2bb6a6', 'input/plan.tsv': 'ade77d7db8cef7e74b6571191b1e634768dbaf9d15b0d20705c4ad3f169c507f', 'input/sources.tsv': '4af3ce021d8a952e27955fdab68df959c6015a6682c21783188ab60cc8b4f616', 'input/tests.tsv': '15949204723eca1267e45f403360bf309eb002521de424a56d0455cb060e0cbd', 'parent.json': '44c0787260fcfde733d9e84ec1e745ef51ed2c92832a8998ea78c1395794d895', 'pooled-strict.json': '51f893b64a427ec2b8818dacc8d7f1f9787fbd6713a3a6cce58a124ef29f58de', 'pooled.json': '25382f4673457e1f8c83471770374c4b686db85c1b5d244e04ab1bf4ca988be8', 'reference/counts/counts.tsv': '68e624d6c22cff7b75ec5f4ccd3e2c2add02c24c7d4b5c6602313f3ed268e3a9', 'reference/counts/reference.R': '00aa7656cad47dbcffab744e809602e53ced73441af364a6be7c9082a807935c', 'reference/counts/reference.tsv': 'cf6c7c06360cad4c55d76a35ed87a6c51c6bf4aeadc79c5c6288059399843a7d', 'reference/exact/group-audit.tsv': '8a7c819679e2f10776214fbfbd94fce9a3ded185cca52733c61cf22349aa940f', 'reference/exact/group-contributors.tsv': 'e302ce9706ae7b88b888a2f6c4ba5b22b1653813e12193a1ddd746d263f659fa', 'reference/exact/group-dispositions.tsv': '10883ee21aa3d99c92f6d9a60f3410927d1fb3411dad86015467bcac49a30926', 'reference/exact/group-keys.tsv': 'f50c58a4ba02395e446f090f55c9a8938f4e7501e33cbb05b0748c7bc930c03d', 'reference/exact/group-members.tsv': '30f95b4099945991c5e8175b6527ac535362385dae5838018c0935ef11f59fbb', 'reference/exact/known-association-sources.tsv': '4af3ce021d8a952e27955fdab68df959c6015a6682c21783188ab60cc8b4f616', 'reference/exact/shared-contributors.tsv': 'c521d9bfee58a87a64df2e6260ee48ab74545320e9134fc5b67846c0cca9d5d6', 'reference/exact/shared-keys.tsv': '91e97740ed14d077a63008300dd3cc180643e31c4598c087466b69cf02b0efe1', 'reference/exact/target-decisions.tsv': 'ca716d0861eeb1a6ba0b7635b975412dd967a91694b8a9a2384141cb74ec9bb6', 'reference/exact/unassigned-targets.tsv': '7d798cf9eb0cdbe4e666ea3cd437f0653330f887e99f30f472961a26c15cb631', 'reference/exact/upstream-tests.tsv': '404fd8220c0e58d933a849cdf23029514425a11df618287a4b946b4944ce587d', 'reference/generate_groups.py': 'a079823434febab5a1e40a2b1fd3f046047cbc999b4c45700f5bcd3c417a621b', 'reference/groups/counts.tsv': 'd57c48e0de2cb739fc11a9d85309e0f62130aedb48d2e982a04c31176c5a7679', 'reference/groups/reference.R': '9662aeef2f62f66ee8d018b822e7fde1b034a19c4f1b1b78e07d37d19d9a6f36', 'reference/groups/reference.tsv': '66a7b204ae8ebf53dc5850f965c23204ffd4dfc6671488703db6d8a0e319b4a2', 'reference/pooled-strict/group-audit.tsv': '2005f95960138af63128c90f5142a15ae8e9c5e7aac5bc3944ba5a173c1650a3', 'reference/pooled-strict/group-contributors.tsv': '3f648f05f00b9b5735d246909d9c91fbd12c47f2493d7513aecf781b53d55d88', 'reference/pooled-strict/group-dispositions.tsv': '9cbf47fd74d6538e1f261225897e83f0a5529abf3ab1d4d387b240f753f0d858', 'reference/pooled-strict/group-keys.tsv': '594acf1f15f40af06634a1916728f6ffec135800796a9ba83f3bcd785ec6a5bb', 'reference/pooled-strict/group-members.tsv': '30f95b4099945991c5e8175b6527ac535362385dae5838018c0935ef11f59fbb', 'reference/pooled-strict/known-association-sources.tsv': '4af3ce021d8a952e27955fdab68df959c6015a6682c21783188ab60cc8b4f616', 'reference/pooled-strict/shared-contributors.tsv': '03f882d840e9e6949a4ba61537eb0ed0c9619ced8ee28a4b24d8c803cae130ec', 'reference/pooled-strict/shared-keys.tsv': '840d82e71056984ecc9f00abf7cea8248c2ded6502038a142785daeb54ae22b2', 'reference/pooled-strict/target-decisions.tsv': '5b1a67fec97835935a27361fd6af110e21b8ee0b32b94c2596b15ccabb8a5038', 'reference/pooled-strict/unassigned-targets.tsv': '434d80edcc6261224f3f820e27b32120c44c9316defd2a854672d756ca2a8e4e', 'reference/pooled-strict/upstream-tests.tsv': '404fd8220c0e58d933a849cdf23029514425a11df618287a4b946b4944ce587d', 'reference/pooled/group-audit.tsv': '201edce0bb3eb64154318b6dc6127b734a75b4be0e9fa4bd2e5d8bc9d3c3cb1a', 'reference/pooled/group-contributors.tsv': '3f648f05f00b9b5735d246909d9c91fbd12c47f2493d7513aecf781b53d55d88', 'reference/pooled/group-dispositions.tsv': '55529b7643a4aa7f498c944140ace950cdedc4d7dc9bca150687d08f56a1bbf9', 'reference/pooled/group-keys.tsv': '71f2c34106746735319ce9915468f90ef77f4e63c3bbc230a1805ce49cc451ad', 'reference/pooled/group-members.tsv': '30f95b4099945991c5e8175b6527ac535362385dae5838018c0935ef11f59fbb', 'reference/pooled/known-association-sources.tsv': '4af3ce021d8a952e27955fdab68df959c6015a6682c21783188ab60cc8b4f616', 'reference/pooled/shared-contributors.tsv': '03f882d840e9e6949a4ba61537eb0ed0c9619ced8ee28a4b24d8c803cae130ec', 'reference/pooled/shared-keys.tsv': '840d82e71056984ecc9f00abf7cea8248c2ded6502038a142785daeb54ae22b2', 'reference/pooled/target-decisions.tsv': 'e68e50094923050b1b859f96b6693dcae66452f8936395169452da1ff07f175d', 'reference/pooled/unassigned-targets.tsv': '434d80edcc6261224f3f820e27b32120c44c9316defd2a854672d756ca2a8e4e', 'reference/pooled/upstream-tests.tsv': '404fd8220c0e58d933a849cdf23029514425a11df618287a4b946b4944ce587d', 'reference/selected/group-audit.tsv': '2178bc16ad281f492fb6ada8315b78b3b51d24d6c12bbc985468ff51d962b5e8', 'reference/selected/group-contributors.tsv': 'e302ce9706ae7b88b888a2f6c4ba5b22b1653813e12193a1ddd746d263f659fa', 'reference/selected/group-dispositions.tsv': 'cf354d57394c29f000ad8b9986ed4e317928307822fc1ddc471bb6a7eaeba024', 'reference/selected/group-keys.tsv': 'b3ccc37d2ff99ea883333330890bd65ca24c95b0105d6ba9e9bee29bd03b5079', 'reference/selected/group-members.tsv': '30f95b4099945991c5e8175b6527ac535362385dae5838018c0935ef11f59fbb', 'reference/selected/known-association-sources.tsv': '4af3ce021d8a952e27955fdab68df959c6015a6682c21783188ab60cc8b4f616', 'reference/selected/shared-contributors.tsv': 'c521d9bfee58a87a64df2e6260ee48ab74545320e9134fc5b67846c0cca9d5d6', 'reference/selected/shared-keys.tsv': '91e97740ed14d077a63008300dd3cc180643e31c4598c087466b69cf02b0efe1', 'reference/selected/target-decisions.tsv': '92d3a2ddedbb19c812824e3f41c86a7b5b6bd9bbced26013df0769e30bb1510d', 'reference/selected/unassigned-targets.tsv': '7d798cf9eb0cdbe4e666ea3cd437f0653330f887e99f30f472961a26c15cb631', 'reference/selected/upstream-tests.tsv': '404fd8220c0e58d933a849cdf23029514425a11df618287a4b946b4944ce587d', 'selected.json': 'ad38a837197c9979677ceba981a061102d312ddcdb774df90953411c6cfac14a', 'source-provenance.json': 'ee6a5141c7c34696e14ba1749309cb305df29fa8e0a934c06ed9b8d8332136d1', 'upstream-exact.json': 'e18ed130994f86de0d72e194cc93f660cbd382e2f49bbfad229cdaa8c5ffea5b', 'upstream-pooled-strict.json': 'd52f312bf56b5c8ca5b7f5735b5ed25b6838a90b2c01f65c0b9c0def370f5ddc', 'upstream-pooled.json': '50bcb52dd925c7b964fe8710ada2db1dd863fe5072cbee7b3cb81f3077216d2c', 'upstream-selected.json': 'dbb71fe4e194d6cbb3b8ada51992cd7abc776519a69223c3aeb08264b6c1ea42'}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def prepare(directory):
    archive = Path(__file__).with_name('synthetic-inputs.zip')
    if archive.stat().st_size > 16777216 or digest(archive.read_bytes()) != ARCHIVE_SHA256:
        raise ValueError('Synthetic archive size or SHA-256 differs; restore the pinned example')
    contents = {}
    with zipfile.ZipFile(archive) as source:
        entries = source.infolist()
        if len(entries) != len(MEMBERS) or {entry.filename for entry in entries} != set(MEMBERS):
            raise ValueError('Synthetic archive differs from its exact member allowlist')
        if sum(entry.file_size for entry in entries) > 67108864:
            raise ValueError('Synthetic archive exceeds its complete uncompressed byte budget')
        for entry in entries:
            if entry.file_size > 8388608 or not stat.S_ISREG(entry.external_attr >> 16):
                raise ValueError('Oversized or nonregular synthetic member: ' + entry.filename)
            data = source.read(entry)
            if digest(data) != MEMBERS[entry.filename]:
                raise ValueError('Synthetic member SHA-256 mismatch: ' + entry.filename)
            contents[entry.filename] = data
    directory = Path(directory).resolve()
    directory.mkdir(parents=True, exist_ok=False)
    for name, data in contents.items():
        path = directory / name  # Exact committed allowlist; no arbitrary archive extraction.
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    (directory / 'input-manifest.json').write_text(json.dumps({
        'archive_sha256': ARCHIVE_SHA256,
        'members': MEMBERS,
        'scope': 'Synthetic complete probability families and declared count/group comparisons. Independent raw-input BH,classification and set ledgers;original R Fisher/correction reference with a separate finite-log extension. No biological association study or inferred historical defaults.',
    }, indent=2) + '\n', encoding='utf-8')
    return directory


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    try:
        print(prepare(args.out))
    except (OSError, ValueError, zipfile.BadZipFile) as failure:
        parser.exit(2, f'INPUT PREPARATION FAILED: {failure}\nRequired action: restore the pinned example if needed and choose a NEW writable directory.\n')
