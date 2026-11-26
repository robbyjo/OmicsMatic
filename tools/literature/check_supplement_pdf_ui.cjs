// Execute the actual inline catalog JavaScript with a small DOM substitute.
// This checks data/renderer/filter behavior; it is not a browser layout test.
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.resolve(__dirname, '../..');
const source = fs.readFileSync(path.join(root, 'site/literature-review.html'), 'utf8');
const match = source.match(/<script id="review-data" type="application\/json">(.*?)<\/script>/s);
assert(match);
const data = JSON.parse(match[1]);
for (const [key, filename] of [['papers', 'papers.json'], ['tools', 'tool-inventory.json'], ['summary', 'summary.json']]) {
  assert.deepEqual(data[key], JSON.parse(fs.readFileSync(path.join(root, 'docs/literature/omics-2023-2026', filename), 'utf8')));
}
assert.equal(data.papers.length, 250);
assert.equal(data.tools.length, 453);
const elements = new Map();
const element = id => {
  if (!elements.has(id)) elements.set(id, {value: '', textContent: '', innerHTML: '', add() {}, addEventListener() {}, setAttribute() {}});
  return elements.get(id);
};
element('review-data').textContent = match[1];
const scripts = [...source.matchAll(/<script>(.*?)<\/script>/gs)];
assert.equal(scripts.length, 1);
const context = vm.createContext({document: {getElementById: element, querySelectorAll: () => []}, Option: function() {}, URL});
new vm.Script(scripts[0][1]).runInContext(context, {timeout: 30000});
assert.equal(element('count').textContent, '250 of 250 papers');
const paper = data.papers.find(p => p.paper_id === 'P146');
const additions = paper.tools.filter(t => ['construct-ascat-files', 'preascat'].includes(t.tool_id));
assert.equal(additions.length, 2);
for (const use of additions) {
  const rendered = vm.runInContext(`primaryEvidence(${JSON.stringify(use)})`, context);
  assert.match(rendered, /Supplementary PDF/);
  assert.match(rendered, /physical page 7 of 92/);
  assert.match(rendered, /PDF SHA-256/);
  assert.match(rendered, /Page text SHA-256/);
  assert.match(rendered, /pypdf 6\.21\.4/);
  assert.match(rendered, /2 literal name occurrences; 1 retained parser diagnostics/);
  assert(!/paragraph|undefined|NaN/.test(rendered));
  assert(!('evidence_paragraph_index' in use));
  const hostile = structuredClone(use);
  hostile.evidence_section = '<img src=x onerror=alert(1)>';
  assert(!vm.runInContext(`primaryEvidence(${JSON.stringify(hostile)})`, context).includes('<img'));
}
const jats = data.papers[0].tools.find(t => !t.evidence_source_kind);
assert(jats);
assert.match(vm.runInContext(`primaryEvidence(${JSON.stringify(jats)})`, context), /Main article JATS; one-based paragraph index/);
assert.match(vm.runInContext(`primaryEvidence(${JSON.stringify(jats)})`, context), new RegExp('paragraph ' + jats.evidence_paragraph_index));
const docx = data.papers.flatMap(p => p.tools).find(t => t.evidence_source_kind === 'supplementary-docx');
if (docx) assert.match(vm.runInContext(`primaryEvidence(${JSON.stringify(docx)})`, context), /Supplementary DOCX.*zero-based Word index/);
element('tools-tab').onclick();
assert.equal(element('count').textContent, '453 named tools');
element('coverage').value = 'missing';
vm.runInContext('render()', context);
assert.equal(element('count').textContent, '252 named tools');
assert.match(element('results').innerHTML, /ConstructASCATFiles/);
assert.match(element('results').innerHTML, /PREASCAT/);
assert.match(element('results').innerHTML, /SKIP3/);
element('query').value = 'construct-ascat-files';
vm.runInContext('render()', context);
assert.equal(element('count').textContent, '1 named tools');
const warnings = fs.readFileSync(path.join(root, 'site/literature-warnings.html'), 'utf8');
assert(warnings.includes('supplement-pdf-catalog-notice') && warnings.includes('SKIP3'));
assert(source.includes('supplement-pdf-prior-catalog') && source.includes('Earlier catalog checkpoints (historical)'));
console.log(JSON.stringify({status: 'PASS_INLINE_JAVASCRIPT_PDF_LOCATOR_DATA_AND_FILTER_CHECKS', papers: 250, named_tools: 453,
  PDF_locators: additions.length, existing_DOCX_checked: Boolean(docx), browser_layout_tested: false, website_deployed: false}));
