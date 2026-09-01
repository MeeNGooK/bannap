import { mkdirSync, writeFileSync } from 'node:fs';

const key = process.env.ALADIN_TTB_KEY;
if (!key) throw new Error('ALADIN_TTB_KEY is required');
const url = new URL('https://www.aladin.co.kr/ttb/api/ItemList.aspx');
url.search = new URLSearchParams({
  ttbkey: key,
  QueryType: 'Bestseller',
  SearchTarget: 'Book',
  MaxResults: '200',
  start: '1',
  output: 'js',
  Version: '20131101',
});
const response = await fetch(url, { headers: { 'User-Agent': 'Bannap/0.1 catalog updater' } });
if (!response.ok) throw new Error(`Aladin request failed: ${response.status}`);
const payload = await response.json();
if (!Array.isArray(payload.item)) throw new Error(payload.errorMessage || 'Unexpected Aladin response');
const books = payload.item.map((item, index) => ({
  id: item.isbn13 || item.isbn || `aladin-${item.itemId}`,
  isbn13: item.isbn13 || null,
  title: item.title,
  author: item.author || '',
  publisher: item.publisher || '',
  publishedDate: item.pubDate || '',
  thumbnail: item.cover || '',
  category: item.categoryName || '',
  rank: index + 1,
  source: 'Aladin bestseller',
  updatedAt: new Date().toISOString(),
}));
mkdirSync('public/data', { recursive: true });
writeFileSync('public/data/catalog.json', JSON.stringify({ updatedAt: new Date().toISOString(), books }, null, 2));
console.log(`Saved ${books.length} bestseller records.`);
