import { mkdirSync, writeFileSync } from 'node:fs';

const authKey = process.env.DATA4LIBRARY_AUTH_KEY;
if (!authKey) throw new Error('DATA4LIBRARY_AUTH_KEY is required');

const pageSize = 100;
const endpoint = 'https://data4library.kr/api/libSrch';

async function fetchPage(pageNo) {
  const url = new URL(endpoint);
  url.search = new URLSearchParams({ authKey, pageNo: String(pageNo), pageSize: String(pageSize), format: 'json' });
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Library catalog request failed: ${response.status}`);
  const data = await response.json();
  const root = data.response || data;
  const raw = root.libs?.lib || root.libs || [];
  const libs = (Array.isArray(raw) ? raw : [raw]).map((item) => item.lib || item).filter(Boolean);
  return { total: Number(root.numFound || root.numfound || 0), libs };
}

const first = await fetchPage(1);
const pageCount = Math.max(1, Math.ceil(first.total / pageSize));
const pages = [first.libs];
for (let pageNo = 2; pageNo <= pageCount; pageNo += 1) {
  pages.push((await fetchPage(pageNo)).libs);
}

const seen = new Set();
const libraries = pages.flat().map((library) => ({
  code: String(library.libCode || library.libcode || ''),
  name: library.libName || library.libname || '',
  address: library.address || '',
  tel: library.tel || library.phone || '',
  homepage: library.homepage || library.homePage || '',
  closed: library.closed || library.close || '',
})).filter((library) => library.code && library.name && !seen.has(library.code) && seen.add(library.code))
  .sort((a, b) => a.name.localeCompare(b.name, 'ko'));

mkdirSync('public/data', { recursive: true });
writeFileSync('public/data/libraries.json', JSON.stringify({
  updatedAt: new Date().toISOString(),
  source: '도서관정보나루',
  libraries,
}, null, 2));
console.log(`Saved ${libraries.length} public libraries from ${first.total} records.`);
