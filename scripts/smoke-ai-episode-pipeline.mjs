import { spawn } from 'node:child_process';
import { writeFile } from 'node:fs/promises';

const outputPath = process.env.OUTPUT_PATH || 'tmp-ai-episode-pipeline-smoke-response.json';

const steps = [
  {
    name: 'saveDraft',
    description: 'Save existing Gemini draft response',
    args: ['scripts/save-gemini-draft.mjs'],
  },
  {
    name: 'adminUiData',
    description: 'Verify admin episode detail and readiness data',
    args: ['scripts/smoke-admin-episode-ui-data.mjs'],
  },
  {
    name: 'playerFlow',
    description: 'Verify publish, play, final unlock, and clear flow',
    args: ['scripts/smoke-player-flow.mjs'],
  },
  {
    name: 'playerContentQuality',
    description: 'Verify player-facing text quality and answer leak guardrails',
    args: ['scripts/smoke-player-content-quality.mjs'],
  },
];

function tail(value, maxLength = 4000) {
  if (!value) return '';
  return value.length > maxLength ? value.slice(value.length - maxLength) : value;
}

function runStep(step) {
  return new Promise((resolve) => {
    const startedAt = Date.now();
    const child = spawn(process.execPath, step.args, {
      cwd: process.cwd(),
      env: { ...process.env },
      stdio: ['ignore', 'pipe', 'pipe'],
    });

    let stdout = '';
    let stderr = '';

    child.stdout.on('data', (chunk) => {
      const text = chunk.toString('utf8');
      stdout += text;
      process.stdout.write(text);
    });

    child.stderr.on('data', (chunk) => {
      const text = chunk.toString('utf8');
      stderr += text;
      process.stderr.write(text);
    });

    child.on('error', (error) => {
      resolve({
        name: step.name,
        description: step.description,
        code: 1,
        durationMs: Date.now() - startedAt,
        error: error.message,
        stdoutTail: tail(stdout),
        stderrTail: tail(stderr),
      });
    });

    child.on('close', (code) => {
      resolve({
        name: step.name,
        description: step.description,
        code,
        durationMs: Date.now() - startedAt,
        stdoutTail: tail(stdout),
        stderrTail: tail(stderr),
      });
    });
  });
}

const startedAt = new Date().toISOString();
const results = [];

for (const step of steps) {
  console.log(`\n[pipeline] ${step.name}: ${step.description}`);
  const result = await runStep(step);
  results.push(result);

  if (result.code !== 0) {
    console.error(`[pipeline] ${step.name} failed with exit code ${result.code}`);
    break;
  }
}

const valid = results.length === steps.length && results.every((result) => result.code === 0);
const summary = {
  valid,
  startedAt,
  finishedAt: new Date().toISOString(),
  outputPath,
  steps: results.map((result) => ({
    name: result.name,
    description: result.description,
    code: result.code,
    durationMs: result.durationMs,
    error: result.error || null,
  })),
  failedStep: valid ? null : results.find((result) => result.code !== 0)?.name || 'unknown',
};

await writeFile(outputPath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
console.log(JSON.stringify(summary, null, 2));

if (!valid) {
  process.exitCode = 1;
}
