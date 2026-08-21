export const shouldShowMessageTimestamp = ({ enabled, timestamp, hasAccurateTimestamp }) =>
  Boolean(enabled && timestamp && hasAccurateTimestamp !== false)

export const shouldAutoFollowMessages = ({ enabled, distanceFromBottom, threshold = 100 }) =>
  Boolean(enabled && distanceFromBottom <= threshold)

// 提取模型推理过程，正文与推理分开渲染，兼容流式阶段尚未闭合的 <think>。
export const extractReasoning = content => {
  const raw = String(content || '')
  const reasoningParts = []
  let main = raw.replace(/<think>([\s\S]*?)<\/think>/gi, (_match, reasoning) => {
    if (reasoning.trim()) reasoningParts.push(reasoning.trim())
    return ''
  })

  const openThink = main.match(/<think>([\s\S]*)$/i)
  if (openThink) {
    if (openThink[1].trim()) reasoningParts.push(openThink[1].trim())
    main = main.slice(0, openThink.index)
  } else if (reasoningParts.length === 0) {
    const quote = main.match(/^((?:>\s*[^\n]*\n?)+)/)
    if (quote && /reasoning|推理|思考|thinking/i.test(quote[1].split('\n')[0])) {
      reasoningParts.push(quote[1].replace(/^>\s?/gm, '').trim())
      main = main.slice(quote[1].length)
    }
  }

  return {
    reasoning: reasoningParts.join('\n\n'),
    main: main.trim()
  }
}

// /ai/chat 返回原始文本流。只识别明确的错误信封，避免误拆模型按要求生成的 JSON。
export const resolveStreamResponse = content => {
  const rawContent = String(content || '')

  try {
    const response = JSON.parse(rawContent)
    if (response && !Array.isArray(response) && response.ok === 0) {
      const errorMessage = String(response.msg || '生成失败，请稍后重试。')
      return { content: errorMessage, errorMessage }
    }
  } catch {
    // 普通文本流无需解析。
  }

  return { content: rawContent, errorMessage: '' }
}
