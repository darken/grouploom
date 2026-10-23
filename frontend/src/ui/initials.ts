/** First and last initial of a name (a single initial for one word), uppercased. */
export function initials(name: string): string {
  const words = name.split(/\s+/).filter(Boolean)
  const first = words[0]
  const last = words.length > 1 ? words[words.length - 1] : undefined
  return [first, last]
    .map((word) => (word ? (Array.from(word)[0] ?? '').toLocaleUpperCase() : ''))
    .join('')
}
