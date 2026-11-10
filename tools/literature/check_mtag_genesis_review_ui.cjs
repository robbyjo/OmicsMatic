// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Preserve all18 existing GENESIS assertions; update only current count metadata.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict'),Module=require('module');
const original=path.join(__dirname,'check_genesis_catalog_ui.cjs'),before=fs.readFileSync(original);let after=before.toString('utf8');
const edits=[["'upstream-adapter-validated':78","'upstream-adapter-validated':79"],["'missing':246","'missing':245"],['97 new scoped integrations/100 including existing;246 missing','98 new scoped integrations/101 including existing;245 missing']];
for(const [old,value] of edits){assert.equal(after.split(old).length-1,1);after=after.replace(old,value);}
const out=process.env.JLINALG_REVIEW_UI_OUT;assert(out);fs.mkdirSync(out,{recursive:true});fs.writeFileSync(path.join(out,'genesis-derived.cjs'),after);
fs.writeFileSync(path.join(out,'DERIVATION.json'),JSON.stringify({scope:'Only current adapter/missing totals and their report label; all existing GENESIS/SNPRelate assertions unchanged.',before_sha256:crypto.createHash('sha256').update(before).digest('hex'),after_sha256:crypto.createHash('sha256').update(after).digest('hex'),exact_replacements:edits,scientific_verifiers_changed:false},null,2)+'\n');
const instance=new Module(original,module);instance.filename=original;instance.paths=Module._nodeModulePaths(path.dirname(original));instance._compile(after,original);
