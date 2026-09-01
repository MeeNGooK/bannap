import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.json':'application/json','.webmanifest':'application/manifest+json'};
createServer(async (req,res)=>{try{let path=req.url==='/'?'index.html':req.url.split('?')[0]; const data=await readFile(join('public',path));res.writeHead(200,{'Content-Type':types[extname(path)]||'application/octet-stream'});res.end(data)}catch{res.writeHead(404);res.end('Not found')}}).listen(4173,()=>console.log('http://localhost:4173'));
