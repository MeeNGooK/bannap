import { mkdirSync, writeFileSync } from 'node:fs';

const serviceId = process.env.SGIS_SERVICE_ID;
const securityKey = process.env.SGIS_SECURITY_KEY;
if (!serviceId || !securityKey) throw new Error('SGIS_SERVICE_ID and SGIS_SECURITY_KEY are required');

const api = 'https://sgisapi.mods.go.kr/OpenAPI3';
const request = async (path, params = {}) => {
  const url = new URL(`${api}${path}`);
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, value);
  });
  const response = await fetch(url);
  if (!response.ok) throw new Error(`${path} failed: ${response.status}`);
  const data = await response.json();
  if (Number(data.errCd) !== 0) throw new Error(`${path} failed: ${data.errMsg || data.errCd}`);
  return data.result;
};

const auth = await request('/auth/authentication.json', {
  consumer_key: serviceId,
  consumer_secret: securityKey,
});
const token = auth.accessToken;
const stage = (code) => request('/addr/stage.json', { accessToken: token, cd: code });
const asArray = (value) => Array.isArray(value) ? value : [];
const toNeighborhood = (item) => ({
  code: String(item.cd || ''),
  name: item.full_addr || item.addr_name || '',
});

const provinces = asArray(await stage());
const districts = (await Promise.all(provinces.map(async (province) => asArray(await stage(province.cd))))).flat();
const neighborhoods = (await Promise.all(districts.map(async (district) => asArray(await stage(district.cd))))).flat()
  .map(toNeighborhood)
  .filter((item) => item.code.length >= 7 && item.name)
  .sort((a, b) => a.name.localeCompare(b.name, 'ko'));

mkdirSync('public/data', { recursive: true });
writeFileSync('public/data/neighborhoods.json', JSON.stringify({
  updatedAt: new Date().toISOString(),
  source: 'SGIS Open Platform',
  neighborhoods,
}, null, 2));
console.log(`Saved ${neighborhoods.length} administrative neighborhoods.`);
