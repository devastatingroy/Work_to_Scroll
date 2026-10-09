// Render the self-contained HTML animation to a 1080x1920 H.264 clip and five PNGs.
// Usage: NODE_PATH=<directory containing playwright> node tools/export-worktowatch-reward-transition.cjs
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawn } = require('node:child_process');
const { once } = require('node:events');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const root = path.resolve(__dirname, '..');
const design = path.join(root, 'Resources', 'WorkToWatch_推广视频素材');
const html = path.join(design, 'worktowatch-tiktok-reward-transition.html');
const video = path.join(design, 'worktowatch-tiktok-reward-transition-v2.mp4');
const fps = 30;
const frames = 108;

async function main() {
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
    await page.goto(`${pathToFileURL(html).href}?export=1`, { waitUntil: 'load' });
    await page.evaluate(() => document.fonts.ready);
    if (!await page.evaluate(() => typeof window.renderAt === 'function')) throw new Error('renderAt is unavailable');

    for (const [name, second] of [['blocked', 0], ['threshold', 1], ['arrival', 2], ['impact', 2.17], ['reward', 2.9]]) {
      await page.evaluate(t => window.renderAt(t), second);
      await page.screenshot({ path: path.join(design, `worktowatch-tiktok-reward-${name}-v2.png`) });
    }

    const args = ['-hide_banner', '-loglevel', 'error', '-y', '-f', 'image2pipe', '-framerate', String(fps), '-vcodec', 'png', '-i', 'pipe:0', '-an', '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', video];
    const ffmpeg = spawn('ffmpeg', args, { stdio: ['pipe', 'inherit', 'inherit'] });
    let failed = false;
    ffmpeg.on('error', () => { failed = true; });
    for (let i = 0; i < frames; i++) {
      await page.evaluate(t => window.renderAt(t), i / fps);
      const png = await page.screenshot({ type: 'png' });
      if (!ffmpeg.stdin.write(png)) await once(ffmpeg.stdin, 'drain');
      if (i % 30 === 0) process.stdout.write(`Rendered ${i}/${frames} frames\n`);
    }
    ffmpeg.stdin.end();
    const [code] = await once(ffmpeg, 'close');
    if (failed || code !== 0 || !fs.existsSync(video)) throw new Error(`ffmpeg exited with ${code}`);
    process.stdout.write(`Saved ${video}\n`);
  } finally {
    await browser.close();
  }
}

main().catch(error => { console.error(error); process.exitCode = 1; });
