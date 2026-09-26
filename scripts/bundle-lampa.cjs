const fs = require('fs');
const path = require('path');
const { rollup } = require('rollup');
const { babel } = require('@rollup/plugin-babel');
const commonjs = require('@rollup/plugin-commonjs');
const { nodeResolve } = require('@rollup/plugin-node-resolve');
const worker = require('rollup-plugin-web-worker-loader');
const sass = require('sass');
const crypto = require('crypto');

(async () => {
  const output = path.resolve(process.argv[2]);
  fs.mkdirSync(output, { recursive: true });
  fs.cpSync('public', output, { recursive: true });
  fs.mkdirSync(path.join(output, 'lang'), { recursive: true });
  fs.cpSync('src/lang', path.join(output, 'lang'), { recursive: true });

  const bundle = await rollup({
    input: 'src/app.js',
    plugins: [
      babel({ babelHelpers: 'bundled', presets: ['@babel/preset-env'] }),
      commonjs(),
      nodeResolve(),
      worker()
    ],
    onwarn(warning, warn) {
      if (warning.code !== 'CIRCULAR_DEPENDENCY') warn(warning);
    }
  });

  const generated = await bundle.generate({ format: 'iife' });
  let app = generated.output.find(item => item.type === 'chunk').code.replace(/return kIsNodeJS/g, 'return false');
  const hash = crypto.createHash('sha256').update(app).digest('hex');
  app = app.replaceAll('{__APP_HASH__}', hash).replaceAll('{__APP_BUILD__}', 'Cefrium AC3/EAC3 runtime build');
  fs.writeFileSync(path.join(output, 'app.js'), app);
  await bundle.close();

  fs.mkdirSync(path.join(output, 'css'), { recursive: true });
  for (const file of fs.readdirSync('src/sass').filter(name => name.endsWith('.scss') && !name.startsWith('_'))) {
    const css = sass.compile(path.join('src/sass', file), { style: 'compressed', logger: sass.Logger.silent }).css;
    fs.writeFileSync(path.join(output, 'css', file.replace(/\.scss$/, '.css')), css);
  }

  const inputBootstrap = `<script>
(() => {
  const mode = new URLSearchParams(location.search).get('input');
  if (mode === 'touch' || mode === 'tv') {
    localStorage.setItem('navigation_type', mode === 'touch' ? 'touch' : 'controll');
    localStorage.setItem('is_true_mobile', mode === 'touch' ? 'true' : 'false');
  }
})();
</script>`;
  const html = fs.readFileSync('public/index.html', 'utf8').replace(
    '<script src="app.js"></script>',
    inputBootstrap + '\n    <script src="app.js"></script>'
  );
  fs.writeFileSync(path.join(output, 'index.html'), html);
  fs.copyFileSync('LICENSE', path.join(output, 'LAMPA-LICENSE.txt'));

  if (!fs.statSync(path.join(output, 'app.js')).size || !fs.existsSync(path.join(output, 'css/app.css'))) {
    throw new Error('Missing Lampa build outputs');
  }
  console.log('Built full Lampa frontend, SHA256', hash);
})().catch(error => {
  console.error(error);
  process.exit(1);
});
