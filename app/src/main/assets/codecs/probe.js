'use strict';
const video = document.querySelector('video');
const button = document.querySelector('#run');
const display = document.querySelector('#result');
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
window.__codecReady = true;
button.addEventListener('click', async () => {
  if (window.__codecResults) return;
  button.disabled = true;
  const report = window.__codecResults = {userAgent: navigator.userAgent, results: [], done: false};
  const types = {aac: 'mp4a.40.2', ac3: 'ac-3', eac3: 'ec-3'};
  report.mime = Object.fromEntries(Object.entries(types).map(([key, codec]) => [key, video.canPlayType(`audio/mp4; codecs="${codec}"`)]));
  // Route decoded audio to WebAudio: non-zero PCM proves more than a moving video clock.
  const context = new AudioContext();
  const analyser = context.createAnalyser();
  analyser.fftSize = 2048;
  context.createMediaElementSource(video).connect(analyser);
  analyser.connect(context.destination);
  await context.resume();
  report.audioContext = context.state;
  const samples = new Float32Array(analyser.fftSize);
  const rms = () => {
    analyser.getFloatTimeDomainData(samples);
    return Math.sqrt(samples.reduce((sum, x) => sum + x * x, 0) / samples.length);
  };
  const until = async (check, timeout = 12000) => {
    const deadline = performance.now() + timeout;
    while (!check()) {
      if (video.error) throw Error(`MEDIA_ERR_${video.error.code}: ${video.error.message}`);
      if (performance.now() > deadline) throw Error('Playback timeout');
      await sleep(80);
    }
  };
  try {
    for (const pair of ['h264-aac', 'h264-ac3', 'h264-eac3', 'hevc-eac3']) {
      for (const container of ['mp4', 'mkv']) {
        const row = {file: `${pair}.${container}`, pcmRms: 0, pcmAfterSeek: 0, passed: false};
        report.results.push(row);
        video.pause();
        video.src = `media/${row.file}`;
        video.load();
        try {
          await Promise.race([video.play(), sleep(12000).then(() => {throw Error('play() timeout');})]);
          await until(() => video.currentTime > 0.5);
          row.duration = video.duration;
          for (let i = 0; i < 12; i++) {row.pcmRms = Math.max(row.pcmRms, rms()); await sleep(80);}
          row.beforeSeek = video.currentTime;
          video.currentTime = 7;
          await until(() => !video.seeking && video.currentTime >= 7.3);
          for (let i = 0; i < 8; i++) {row.pcmAfterSeek = Math.max(row.pcmAfterSeek, rms()); await sleep(80);}
          row.afterSeek = video.currentTime;
          row.frames = video.getVideoPlaybackQuality().totalVideoFrames;
          row.passed = row.duration > 11 && row.duration < 14 && row.frames > 0 && row.pcmRms > 0.005 && row.pcmAfterSeek > 0.005;
        } catch (error) {row.error = String(error);}
        display.textContent = JSON.stringify(report, null, 2);
      }
    }
  } catch (error) {report.error = String(error);}
  finally {
    video.pause();
    await context.close();
    report.done = true;
    display.textContent = JSON.stringify(report, null, 2);
  }
});
