// Realtime half of the client-driver-chat QA (constitution rule 55). Speaks STOMP 1.2
// over Node's built-in WebSocket, which accepts handshake headers. Run through qa.sh.
import { writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const { BASE_URL, ANA, BRUNO, FABIO, CONV_ANA } = process.env;
const WS_URL = `${BASE_URL.replace(/^http/, 'ws')}/api/ws`;
const TOPIC = `/topic/conversations/${CONV_ANA}`;
const QUIET_MS = 1500;
let passed = 0;

function fail(id, expected, observed) {
  console.error(`FAIL | ${id} | expected: ${expected} | observed: ${observed}`);
  process.exit(1);
}

function pass(id, scenario, expected) {
  passed += 1;
  console.log(`| ${id} | ${scenario} | ${expected} | PASS |`);
}

function parseFrames(data) {
  return String(data)
    .split('\0')
    .filter((raw) => raw.trim() !== '')
    .map((raw) => {
      const [head, ...rest] = raw.replace(/^\n+/, '').split('\n\n');
      const [command, ...headerLines] = head.split('\n');
      const headers = Object.fromEntries(headerLines.map((line) => line.split(/:(.*)/s).slice(0, 2)));
      return { command, headers, body: rest.join('\n\n') };
    });
}

function frame(command, headers = {}, body = '') {
  const lines = Object.entries(headers).map(([key, value]) => `${key}:${value}`);
  return `${command}\n${lines.join('\n')}\n\n${body}\0`;
}

function handshake(token) {
  return new Promise((resolve) => {
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const ws = new WebSocket(WS_URL, { headers });
    ws.onopen = () => resolve({ ok: true, ws });
    ws.onerror = () => resolve({ ok: false });
  });
}

async function connect(token) {
  const { ok, ws } = await handshake(token);
  if (!ok) fail('connect', 'handshake accepted', 'refused');
  const session = { ws, frames: [], closed: false };
  ws.onmessage = (event) => session.frames.push(...parseFrames(event.data));
  ws.onclose = () => { session.closed = true; };
  ws.send(frame('CONNECT', { 'accept-version': '1.2', host: 'localhost' }));
  await waitFor(session, (f) => f.command === 'CONNECTED', 3000, 'CONNECTED');
  return session;
}

function waitFor(session, predicate, timeoutMs, label) {
  return new Promise((resolve, reject) => {
    const started = Date.now();
    const timer = setInterval(() => {
      const found = session.frames.find(predicate);
      if (found) {
        clearInterval(timer);
        resolve(found);
      } else if (Date.now() - started > timeoutMs) {
        clearInterval(timer);
        reject(new Error(`no ${label} within ${timeoutMs}ms`));
      }
    }, 20);
  });
}

async function nothingArrives(session, predicate) {
  await new Promise((resolve) => setTimeout(resolve, QUIET_MS));
  return !session.frames.some(predicate);
}

function subscribe(session, destination, id) {
  session.ws.send(frame('SUBSCRIBE', { id, destination }));
}

async function postMessage(token, body) {
  const response = await fetch(`${BASE_URL}/api/conversations/${CONV_ANA}/messages`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ body }),
  });
  return { status: response.status, json: response.status === 201 ? await response.json() : null };
}

const isMessage = (session) => (f) => f.command === 'MESSAGE';
const isError = (f) => f.command === 'ERROR';

console.log('| id | scenario | expected | result |');
console.log('|---|---|---|---|');

const anonymous = await handshake(null);
if (anonymous.ok) fail('W1', 'handshake refused', 'accepted');
pass('W1', 'handshake without a token is refused (HTTP 401)', 'refused');

const forged = await handshake('not-a-jwt');
if (forged.ok) fail('W2', 'handshake refused', 'accepted');
pass('W2', 'handshake with an invalid token is refused', 'refused');

const driver = await connect(FABIO);
const client = await connect(ANA);
subscribe(driver, TOPIC, 'driver-sub');
subscribe(client, TOPIC, 'client-sub');
await new Promise((resolve) => setTimeout(resolve, 300));
const sent = await postMessage(ANA, 'Saio às 7h, pode ser?');
if (sent.status !== 201) fail('W3', 'REST 201', sent.status);
const delivered = await waitFor(driver, isMessage(driver), 3000, 'MESSAGE').catch((e) => fail('W3', 'MESSAGE', e.message));
const payload = JSON.parse(delivered.body);
if (payload.token !== sent.json.token || payload.sender !== 'CLIENT' || payload.sentAt !== sent.json.sentAt) {
  fail('W3', JSON.stringify(sent.json), delivered.body);
}
pass('W3', 'the driver receives the client message live, same payload as REST', 'MESSAGE');
await waitFor(client, isMessage(client), 3000, 'MESSAGE').catch((e) => fail('W4', 'MESSAGE', e.message));
pass('W4', 'the sender also receives their own message on the topic', 'MESSAGE');

driver.frames.length = 0;
const rejected = await postMessage(ANA, '   ');
if (rejected.status !== 400) fail('W5', 'REST 400', rejected.status);
if (!(await nothingArrives(driver, isMessage(driver)))) fail('W5', 'no MESSAGE', 'MESSAGE published');
pass('W5', 'a rejected message is never published', 'no MESSAGE');

const outsider = await connect(BRUNO);
subscribe(outsider, TOPIC, 'outsider-sub');
await waitFor(outsider, isError, 3000, 'ERROR').catch((e) => fail('W6', 'ERROR frame', e.message));
await postMessage(ANA, 'Só para o Fabio');
if (!(await nothingArrives(outsider, isMessage(outsider)))) fail('W6', 'no MESSAGE', 'MESSAGE leaked');
pass('W6', 'an outsider subscribing to the conversation gets ERROR and no message', 'ERROR');

for (const [id, destination, scenario] of [
  ['W7', '/topic/anything', 'a topic outside the conversations'],
  ['W8', '/topic/conversations/', 'a conversation topic without token'],
  ['W9', `/topic/conversations/${CONV_ANA}/extra`, 'a nested destination under the conversation'],
  ['W10', '/topic/conversations/*', 'a wildcard destination'],
]) {
  const session = await connect(FABIO);
  subscribe(session, destination, `${id}-sub`);
  await waitFor(session, isError, 3000, 'ERROR').catch((e) => fail(id, 'ERROR frame', e.message));
  pass(id, `subscribing to ${scenario} is rejected`, 'ERROR');
  session.ws.close();
}

const spoofer = await connect(ANA);
const listener = await connect(FABIO);
subscribe(listener, TOPIC, 'listener-sub');
await new Promise((resolve) => setTimeout(resolve, 300));
spoofer.ws.send(frame('SEND', { destination: TOPIC, 'content-type': 'application/json' },
  JSON.stringify({ token: 'fake', body: 'spoofed', sender: 'DRIVER', sentAt: new Date().toISOString() })));
await waitFor(spoofer, isError, 3000, 'ERROR').catch((e) => fail('W11', 'ERROR frame', e.message));
if (!(await nothingArrives(listener, isMessage(listener)))) fail('W11', 'no MESSAGE', 'spoofed MESSAGE delivered');
pass('W11', 'a SEND frame from a party is rejected and never reaches the topic', 'ERROR');

for (const session of [driver, client, outsider, spoofer, listener]) session.ws.close();
writeFileSync(join(tmpdir(), 'client-driver-chat-realtime.count'), String(passed));
console.log(`realtime: ${passed} passed`);
process.exit(0);
