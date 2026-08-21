export const buildPdfOutline = async (items, resolvePage) => {
  const result = []
  for (const item of items || []) {
    if (!item?.title) continue
    let page = null
    if (item.dest) {
      page = await resolvePage(item.dest)
    } else if (item.url) {
      continue
    }
    if (page == null) continue
    result.push({
      title: item.title,
      page,
      children: await buildPdfOutline(item.items, resolvePage)
    })
  }
  return result
}
