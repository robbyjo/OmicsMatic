// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Preserve every predecessor scientific/scope assertion; update current catalog totals only.
const fs=require('fs'),path=require('path'),assert=require('assert/strict'),Module=require('module');
const source=path.join(__dirname,"check_mtag_catalog_ui.cjs");let text=fs.readFileSync(source,'utf8');
for(const [before,after,count] of [["'upstream-adapter-validated':79", "'upstream-adapter-validated':80", 1], ["'missing':245", "'missing':244", 1], ["98 new scoped integrations/101 including existing;245 missing", "99 new scoped integrations/102 including existing;244 missing", 1]]){assert.equal(text.split(before).length-1,count);text=text.split(before).join(after);}
const compiled=new Module(source,module);compiled.filename=source;compiled.paths=module.paths;compiled._compile(text,source);
