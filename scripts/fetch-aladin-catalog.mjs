import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const key = process.env.ALADIN_TTB_KEY;
if (!key) throw new Error('ALADIN_TTB_KEY is required');

const classics = JSON.parse(readFileSync('scripts/korean-classics.json', 'utf8'));
const broadQueries = JSON.parse(readFileSync('scripts/korean-book-queries.json', 'utf8'));
const updatedAt = new Date().toISOString();
const catalogLimit = 2000;

async function fetchAladin(endpoint, params) {
  const url = new URL(`https://www.aladin.co.kr/ttb/api/${endpoint}.aspx`);
  url.search = new URLSearchParams({
    ttbkey: key,
    output: 'js',
    Version: '20131101',
    ...params,
  });

  const response = await fetch(url, {
    headers: { 'User-Agent': 'Bannap/0.1 catalog updater' },
  });
  if (!response.ok) throw new Error(`Aladin ${endpoint} request failed: ${response.status}`);

  const payload = await response.json();
  if (!Array.isArray(payload.item)) {
    throw new Error(payload.errorMessage || `Unexpected Aladin ${endpoint} response`);
  }
  return payload.item;
}

function toBook(item, source, rank = null) {
  return {
    id: item.isbn13 || item.isbn || `aladin-${item.itemId}`,
    isbn13: item.isbn13 || null,
    title: item.title || '',
    author: item.author || '',
    publisher: item.publisher || '',
    publishedDate: item.pubDate || '',
    thumbnail: item.cover || '',
    category: item.categoryName || '',
    rank,
    source,
    updatedAt,
  };
}

const bestsellers = await fetchAladin('ItemList', {
  QueryType: 'Bestseller',
  SearchTarget: 'Book',
  MaxResults: '200',
  start: '1',
});

const classicMatches = [];
for (const classic of classics) {
  try {
    const results = await fetchAladin('ItemSearch', {
      Query: classic.title,
      QueryType: 'Title',
      SearchTarget: 'Book',
      MaxResults: '10',
      start: '1',
    });
    const match = results.find((item) =>
      (item.title || '').includes(classic.title) && (item.author || '').includes(classic.author),
    ) || results[0];
    if (match) classicMatches.push(toBook(match, 'Korean classic'));
  } catch (error) {
    console.warn(`Could not enrich ${classic.title}: ${error.message}`);
  }
}

const broadMatches = [];
for (const query of broadQueries) {
  try {
    const results = await fetchAladin('ItemSearch', {
      Query: query,
      QueryType: 'Keyword',
      SearchTarget: 'Book',
      MaxResults: '50',
      start: '1',
    });
    broadMatches.push(...results.map((item) => toBook(item, `Aladin search: ${query}`)));
  } catch (error) {
    console.warn(`Could not collect ${query}: ${error.message}`);
  }
}

const uniqueBooks = new Map();
for (const [index, item] of bestsellers.entries()) {
  const book = toBook(item, 'Aladin bestseller', index + 1);
  uniqueBooks.set(book.id, book);
}
for (const book of classicMatches) {
  if (!uniqueBooks.has(book.id)) uniqueBooks.set(book.id, book);
}
for (const book of broadMatches) {
  if (!uniqueBooks.has(book.id) && uniqueBooks.size < catalogLimit) uniqueBooks.set(book.id, book);
}

const books = [...uniqueBooks.values()];
mkdirSync('public/data', { recursive: true });
writeFileSync('public/data/catalog.json', JSON.stringify({ updatedAt, books }, null, 2));
console.log(
  `Saved ${books.length} records: ${bestsellers.length} bestsellers, ${classicMatches.length} classics, and ${broadMatches.length} broad search results.`,
);
