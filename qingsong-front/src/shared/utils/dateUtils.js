export const formattedDate = (date) => {
  try {
    let dateObject

    if (date instanceof Date) {
      dateObject = date
    } else if (typeof date === 'string') {
      const timestamp = parseInt(date)
      if (!isNaN(timestamp) && timestamp > 1000000000000) {
        dateObject = new Date(timestamp)
      } else if (!isNaN(timestamp) && timestamp > 1000000000) {
        dateObject = new Date(timestamp * 1000)
      } else {
        dateObject = new Date(date)
      }
    } else if (typeof date === 'number') {
      if (date > 1000000000000) {
        dateObject = new Date(date)
      } else if (date > 1000000000) {
        dateObject = new Date(date * 1000)
      } else {
        dateObject = new Date(date)
      }
    } else {
      dateObject = new Date(date)
    }

    if (isNaN(dateObject.getTime())) {
      return String(date)
    }

    const year = dateObject.getFullYear()
    const month = (dateObject.getMonth() + 1).toString().padStart(2, '0')
    const day = dateObject.getDate().toString().padStart(2, '0')
    const hours = dateObject.getHours().toString().padStart(2, '0')
    const minutes = dateObject.getMinutes().toString().padStart(2, '0')
    const seconds = dateObject.getSeconds().toString().padStart(2, '0')
    const value = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
    return value
  } catch (error) {
    console.error('Error processing date:', error)
    return String(date) || 'Invalid Date'
  }
}
