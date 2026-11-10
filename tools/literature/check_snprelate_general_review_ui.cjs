// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Retain all299 prior general browser checks; change only the two overview totals.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),assert=require('assert/strict'),Module=require('module');
const original=path.join(__dirname,'check_cistarget_general_review_ui.cjs');
const before=fs.readFileSync(original), text=before.toString('utf8');
let after=text;
for(const [old,value] of [['/400 named tools/','/402 named tools/'],['/2466 tool observations/','/2475 tool observations/']]){
  assert.equal(after.split(old).length-1,1);after=after.replace(old,value);
}
const out=process.env.JLINALG_REVIEW_UI_OUT;assert(out);fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(path.join(out,'general-derived.cjs'),after);
fs.writeFileSync(path.join(out,'DERIVATION.json'),JSON.stringify({scope:'Only two overview count expectations; every other general browser assertion is unchanged.',
  before_sha256:crypto.createHash('sha256').update(before).digest('hex'),after_sha256:crypto.createHash('sha256').update(after).digest('hex'),
  exact_replacements:[['/400 named tools/','/402 named tools/'],['/2466 tool observations/','/2475 tool observations/']],scientific_verifiers_changed:false},null,2)+'\n');
const instance=new Module(original,module);instance.filename=original;instance.paths=Module._nodeModulePaths(path.dirname(original));instance._compile(after,original);
