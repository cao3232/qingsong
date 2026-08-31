/**
 * 拷贝 pdfjs-dist 的 cmaps / standard_fonts 到 public/，
 * 供 PDFReaderViewer 渲染 CID 字体 PDF（中文字体）时按 cMapUrl 拉取。
 *
 * 用法：node scripts/copy-pdfjs-assets.cjs
 */
const fs = require('fs')
const path = require('path')

const root = path.resolve(__dirname, '..')
const copyDir = (src, dest) => {
  if (!fs.existsSync(src)) return
  fs.mkdirSync(dest, { recursive: true })
  fs.cpSync(src, dest, { recursive: true })
  console.log(`copy-pdfjs: ${src} -> ${dest}`)
}

copyDir(
  path.join(root, 'node_modules/pdfjs-dist/cmaps'),
  path.join(root, 'public/pdfjs-cmaps')
)
copyDir(
  path.join(root, 'node_modules/pdfjs-dist/standard_fonts'),
  path.join(root, 'public/pdfjs-standard-fonts')
)
