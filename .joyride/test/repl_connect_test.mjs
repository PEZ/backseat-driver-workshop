import assert from 'node:assert/strict';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { loadFile, loadString, registerModule } from 'nbb';

let state;
const vscode = {
  extensions: { getExtension: () => state.extension },
  tasks: {
    get taskExecutions() { return state.executions; },
    fetchTasks: async () => { state.events.push('fetch'); return state.tasks; },
    executeTask: async task => { state.events.push(['task', task.name]); },
  },
  commands: {
    executeCommand: async (...args) => { state.events.push(args); },
  },
};
registerModule(vscode, 'vscode');
// Supply only the Joyride logging API; execute the production source unchanged.
await loadString('(ns joyride.core) (defn output-channel [] #js {:appendLine (fn [_])})');
await loadFile(fileURLToPath(new URL('../src/repl_connect.cljs', import.meta.url)));

const start = '(repl-connect/start! {:connect-task "Try Clojure" :repl-task "Try Clojure repl" :connect-sequence "Try Clojure" :session-key "try-clojure"})';
const connect = '(repl-connect/connect! {:connect-sequence "Try Clojure" :port 1234})';

function fixture({ sessions = [], running = false, missing = false } = {}) {
  let resolve, reject;
  const activation = new Promise((yes, no) => { resolve = yes; reject = no; });
  const api = { v1: { repl: { listSessions() {
    state.events.push('sessions');
    return sessions;
  } } } };
  state = {
    events: [],
    executions: running ? [{ task: { name: 'Try Clojure repl' } }] : [],
    tasks: [{ name: 'Try Clojure' }],
    extension: missing ? undefined : {
      activate() { state.events.push('activate'); return activation; },
      get exports() { throw new Error('Extension is not known or not activated'); },
    },
  };
  return { resolve: () => resolve(api), reject };
}

async function pending(code) {
  const result = loadString(code);
  result.catch(() => {}); // Keep early regressions from becoming unhandled rejections.
  // Let evaluation reach the suspended activation promise.
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(state.events, ['activate']);
  return { result };
}

test('startup waits for activation before checking an existing session', async () => {
  const activation = fixture({ sessions: [{ replSessionKey: 'try-clojure' }] });
  const { result } = await pending(start);
  activation.resolve();
  assert.equal(await result, 'ok');
  assert.deepEqual(state.events, ['activate', 'sessions']);
});

test('startup also works when Calva is already active', async () => {
  const activation = fixture({ sessions: [{ replSessionKey: 'try-clojure' }] });
  activation.resolve();
  assert.equal(await loadString(start), 'ok');
  assert.deepEqual(state.events, ['activate', 'sessions']);
});

test('startup starts the configured task after activation when no REPL is running', async () => {
  const activation = fixture();
  const { result } = await pending(start);
  activation.resolve();
  await result;
  assert.deepEqual(state.events, ['activate', 'sessions', 'fetch', ['task', 'Try Clojure']]);
});

test('startup connects to an already running REPL after activation', async () => {
  const activation = fixture({ running: true });
  const { result } = await pending(start);
  activation.resolve();
  await result;
  assert.deepEqual(state.events, ['activate', 'sessions', 'activate',
    ['calva.connect', { connectSequence: 'Try Clojure' }]]);
});

test('direct connection waits for activation and preserves connection options', async () => {
  const activation = fixture();
  const { result } = await pending(connect);
  activation.resolve();
  assert.equal(await result, 'ok');
  assert.deepEqual(state.events, ['activate',
    ['calva.connect', { connectSequence: 'Try Clojure', host: '127.0.0.1', port: 1234 }]]);
});

for (const [name, code] of [['startup', start], ['direct connection', connect]]) {
  test(`${name} reports missing Calva without starting tasks or commands`, async () => {
    fixture({ missing: true });
    await assert.rejects(loadString(code), /Calva is unavailable/);
    assert.deepEqual(state.events, []);
  });

  test(`${name} propagates activation failure without starting tasks or commands`, async () => {
    const activation = fixture();
    const { result } = await pending(code);
    const rejected = assert.rejects(result, /activation failed/);
    activation.reject(new Error('activation failed'));
    await rejected;
    assert.deepEqual(state.events, ['activate']);
  });
}
