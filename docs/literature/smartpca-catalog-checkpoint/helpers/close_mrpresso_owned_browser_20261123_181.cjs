const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]),profile=path.resolve(process.argv[3]);
const parent=path.resolve('D:/projects/JLinAlg/build/fu-1/build/mrpresso-catalog-178');
assert(profile.startsWith(parent+path.sep)&&/^ui-[A-Za-z0-9-]+$/.test(path.basename(path.dirname(profile)))&&path.basename(profile)==='owned-edge-profile','Only the exact current task isolated profile is allowed');
const lines=fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').trim().split(/\r?\n/);assert(/^\d+$/.test(lines[0])&&lines[1].startsWith('/devtools/browser/'));
(async()=>{
 let browser;
 try{browser=await chromium.connectOverCDP('http://127.0.0.1:'+lines[0]);}
 catch(error){if(/ECONNREFUSED|ECONNRESET|socket hang up/.test(String(error))){console.log('Owned CDP endpoint already closed; OS process closure is checked separately');return;}throw error;}
 try{const session=await browser.newBrowserCDPSession();const info=await session.send('Browser.getVersion');assert(info.product);await session.send('Browser.close').catch(error=>{if(browser.isConnected())throw error;});}
 finally{await browser.close();}
 console.log('Requested graceful close of the current task isolated CDP browser');
})().catch(error=>{console.error(error);process.exitCode=1;});
