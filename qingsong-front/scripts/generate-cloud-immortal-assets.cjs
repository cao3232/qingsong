const fs = require('fs')
const path = require('path')
const sharp = require('sharp')

const outputDir = path.resolve(__dirname, '../src/assets/chat-themes/cloud-immortal')
fs.mkdirSync(outputDir, { recursive: true })

const seeded = (seed) => {
  let value = seed >>> 0
  return () => {
    value = (value * 1664525 + 1013904223) >>> 0
    return value / 0xffffffff
  }
}

const mountainPath = (width, baseline, peaks, seed) => {
  const random = seeded(seed)
  const points = [`M0 ${baseline}`]
  const step = width / peaks
  for (let index = 0; index <= peaks; index += 1) {
    const x = Math.round(index * step)
    const peak = baseline - (70 + random() * 280)
    const shoulder = baseline - (24 + random() * 100)
    points.push(`L${Math.max(0, x - step * 0.34)} ${shoulder}`)
    points.push(`L${x} ${peak}`)
    points.push(`L${Math.min(width, x + step * 0.34)} ${shoulder}`)
  }
  points.push(`L${width} ${baseline + 300} L0 ${baseline + 300} Z`)
  return points.join(' ')
}

const cloudShapes = (width, height, count, seed, opacity = 0.4) => {
  const random = seeded(seed)
  return Array.from({ length: count }, (_, index) => {
    const x = Math.round(random() * width)
    const y = Math.round(height * 0.36 + random() * height * 0.46)
    const rx = Math.round(90 + random() * 240)
    const ry = Math.round(18 + random() * 54)
    const alpha = (opacity * (0.35 + random() * 0.65)).toFixed(3)
    return `<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" fill="#f8fbf5" opacity="${alpha}" filter="url(#soft)" />`
  }).join('')
}

const landscapeSvg = (width, height, mobile = false) => {
  const moonX = mobile ? width * 0.69 : width * 0.72
  const moonY = mobile ? height * 0.18 : height * 0.2
  const moonR = mobile ? width * 0.15 : height * 0.14
  const gateX = mobile ? width * 0.48 : width * 0.54
  const gateY = height * 0.56
  return `
    <svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#dce9e5" />
          <stop offset="0.48" stop-color="#eef3ed" />
          <stop offset="1" stop-color="#aebfba" />
        </linearGradient>
        <radialGradient id="moon" cx="50%" cy="50%" r="50%">
          <stop offset="0" stop-color="#fffdf1" />
          <stop offset="0.66" stop-color="#f4e8bd" stop-opacity="0.96" />
          <stop offset="1" stop-color="#d8bb68" stop-opacity="0" />
        </radialGradient>
        <linearGradient id="mountainFar" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#829b98" />
          <stop offset="1" stop-color="#c8d5cf" />
        </linearGradient>
        <linearGradient id="mountainMid" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#536f6c" />
          <stop offset="1" stop-color="#9fb4ae" />
        </linearGradient>
        <linearGradient id="mountainNear" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#294d4a" />
          <stop offset="1" stop-color="#66837d" />
        </linearGradient>
        <filter id="soft"><feGaussianBlur stdDeviation="18" /></filter>
        <filter id="glow"><feGaussianBlur stdDeviation="9" /></filter>
      </defs>
      <rect width="100%" height="100%" fill="url(#sky)" />
      <circle cx="${moonX}" cy="${moonY}" r="${moonR * 1.42}" fill="url(#moon)" />
      <circle cx="${moonX}" cy="${moonY}" r="${moonR * 0.72}" fill="#fff9dc" opacity="0.8" />
      <path d="${mountainPath(width, height * 0.64, mobile ? 7 : 11, 11)}" fill="url(#mountainFar)" opacity="0.66" />
      <path d="${mountainPath(width, height * 0.73, mobile ? 6 : 9, 23)}" fill="url(#mountainMid)" opacity="0.74" />
      ${cloudShapes(width, height, mobile ? 16 : 24, 41, 0.48)}
      <path d="${mountainPath(width, height * 0.86, mobile ? 5 : 8, 67)}" fill="url(#mountainNear)" opacity="0.92" />
      <path d="M0 ${height * 0.76} C${width * 0.28} ${height * 0.68}, ${width * 0.62} ${height * 0.94}, ${width} ${height * 0.72} L${width} ${height} L0 ${height}Z" fill="#e9efea" opacity="0.62" filter="url(#soft)" />
      <path d="M${gateX - 38} ${gateY + 112} L${gateX - 20} ${gateY - 18} L${gateX + 20} ${gateY - 18} L${gateX + 38} ${gateY + 112}" fill="none" stroke="#d6bd72" stroke-width="5" opacity="0.58" />
      <path d="M${gateX - 62} ${gateY} Q${gateX} ${gateY - 42} ${gateX + 62} ${gateY}" fill="none" stroke="#d6bd72" stroke-width="6" opacity="0.58" />
      <path d="M${gateX} ${gateY + 105} C${gateX - 90} ${gateY + 220}, ${gateX - 150} ${height * 0.94}, ${gateX - 210} ${height}" fill="none" stroke="#f4df9a" stroke-width="14" opacity="0.22" filter="url(#glow)" />
      <rect width="100%" height="100%" fill="#173b38" opacity="0.04" />
    </svg>`
}

const mistSvg = (width, height, near = false) => `
  <svg width="${width}" height="${height}" xmlns="http://www.w3.org/2000/svg">
    <defs><filter id="mist"><feGaussianBlur stdDeviation="${near ? 26 : 38}" /></filter></defs>
    ${cloudShapes(width, height, near ? 18 : 12, near ? 131 : 97, near ? 0.48 : 0.3)}
    <path d="M0 ${height * 0.62} C${width * 0.2} ${height * 0.3}, ${width * 0.36} ${height * 0.88}, ${width * 0.58} ${height * 0.54} S${width * 0.86} ${height * 0.36}, ${width} ${height * 0.64}" fill="none" stroke="#f8fbf6" stroke-width="${near ? 86 : 60}" opacity="${near ? 0.34 : 0.22}" filter="url(#mist)" />
  </svg>`

const textureSvg = (size, type) => {
  const random = seeded(type === 'jade' ? 211 : 307)
  const strokes = Array.from({ length: type === 'jade' ? 34 : 90 }, () => {
    const x1 = Math.round(random() * size)
    const y1 = Math.round(random() * size)
    const x2 = Math.round(Math.min(size, x1 + 30 + random() * 150))
    const y2 = Math.round(Math.min(size, y1 + (random() - 0.5) * 60))
    const alpha = (0.025 + random() * 0.075).toFixed(3)
    return `<path d="M${x1} ${y1} Q${(x1 + x2) / 2} ${y1 + (random() - 0.5) * 30} ${x2} ${y2}" stroke="${type === 'jade' ? '#24655d' : '#806f4d'}" stroke-width="${type === 'jade' ? 2.4 : 0.8}" opacity="${alpha}" fill="none" />`
  }).join('')
  const base = type === 'jade' ? '#dbeae3' : '#f7f1df'
  return `<svg width="${size}" height="${size}" xmlns="http://www.w3.org/2000/svg"><rect width="100%" height="100%" fill="${base}" />${strokes}</svg>`
}

const writeWebp = async (name, svg, options = {}) => {
  await sharp(Buffer.from(svg))
    .webp({ quality: options.quality || 82, effort: 6, alphaQuality: 90 })
    .toFile(path.join(outputDir, name))
}

async function main() {
  await Promise.all([
    writeWebp('cloud-mountains-desktop.webp', landscapeSvg(1920, 1080), { quality: 86 }),
    writeWebp('cloud-mountains-mobile.webp', landscapeSvg(960, 1280, true), { quality: 84 }),
    writeWebp('mist-far.webp', mistSvg(1600, 420), { quality: 78 }),
    writeWebp('mist-near.webp', mistSvg(1600, 500, true), { quality: 78 }),
    writeWebp('jade-texture.webp', textureSvg(512, 'jade'), { quality: 80 }),
    writeWebp('paper-texture.webp', textureSvg(512, 'paper'), { quality: 80 })
  ])
  console.log(`云海仙门位图已生成：${outputDir}`)
}

main().catch(error => {
  console.error(error)
  process.exitCode = 1
})
