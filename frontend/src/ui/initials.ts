const JAPANESE = /[\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/u

/**
 * First and last initial of a name (a single initial for one word), uppercased. A Japanese name gets one
 * character, the first of its first word: the family name comes first, and two kanji are too dense for a small
 * avatar.
 */
export function initials(name: string): string {
  const words = name.split(/\s+/).filter(Boolean)
  const first = words[0]
  const last = words.length > 1 && !JAPANESE.test(name) ? words[words.length - 1] : undefined
  return [first, last]
    .map((word) => (word ? (Array.from(word)[0] ?? '').toLocaleUpperCase() : ''))
    .join('')
}
