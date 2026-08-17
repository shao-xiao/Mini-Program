export function downloadBlob(blob, filename, options = {}) {
  const objectUrl = window.URL.createObjectURL(blob)
  const previewWindow = options.previewWindow
  const previewRequested = options.download === false

  if (!previewRequested) {
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  const previewOpened = Boolean(previewWindow)
  if (previewWindow) {
    previewWindow.location.href = objectUrl
  } else if (previewRequested) {
    window.location.href = objectUrl
  }

  const revokeDelay = previewOpened || previewRequested ? 60_000 : 1_000
  window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), revokeDelay)

  return { objectUrl, previewOpened }
}
