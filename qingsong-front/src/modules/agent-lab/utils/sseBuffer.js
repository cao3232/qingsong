const parseSseEvent = (raw) => {
  if (!raw) return null
  const dataLines = raw
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
  if (!dataLines.length) return null
  try {
    return JSON.parse(dataLines.join('\n'))
  } catch (error) {
    return null
  }
}

export const drainSseBuffer = (input, flush = false) => {
  let buffer = input.replace(/\r\n/g, '\n')
  const events = []
  let separator
  while ((separator = buffer.indexOf('\n\n')) !== -1) {
    const event = parseSseEvent(buffer.slice(0, separator))
    buffer = buffer.slice(separator + 2)
    if (event) events.push(event)
  }
  if (flush && buffer.trim()) {
    const event = parseSseEvent(buffer)
    if (event) events.push(event)
    buffer = ''
  }
  return { events, rest: buffer }
}
