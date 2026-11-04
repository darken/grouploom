# Translation glossary

Terms used in `ja.json`. Keep them consistent across the UI.

| English | 日本語 |
|---|---|
| Event | イベント |
| Event type | イベントの種類 |
| Member | メンバー |
| Organizer | 主催者 |
| Group | グループ |
| Reply (to an invitation) | 出欠の回答 |
| Invitation | 招待 |
| Cancel an event | 中止 |
| Schedule (a notification) | 予約 |
| Archive | アーカイブ |
| Owner | オーナー |

## Style

- Polite です/ます in sentences; short nouns on buttons (保存, 削除, キャンセル).
- Member names take さん (`{name}さんを追加しました`).
- Counts use vue-i18n plural forms (`t(key, n)`); Japanese has a single form.
- Stale edits say 他のユーザーが変更しました (the users are the organizers who edit); members are never "users".

## Japanese punctuation and counters

- Full-width `：` `！` `？` (never `:` `!` `?`), except inside placeholders, times and URLs. Tests enforce this.
- 「」 for quoted names and titles (`「{title}」を削除しますか？`).
- `〜` for ranges (`10:00〜12:00`, `5〜1440分`), `…` (not `...`) for ellipses.
- No full stop on buttons and labels; sentences end with 。
- Counters: 人 for people, 件 for notifications, errors and results, 回 for repeats, 日間 / 時間 / 分間 for durations ("valid for"), 週間 for weeks.
- Half-width parentheses after a word (`{name}(アーカイブ済み)`, `メモ(任意)`).
- The same idea gets the same words in the app and in the backend messages (`messages_ja.properties`): for example 開始日時を過去にすることはできません。 and この種類はイベントで使われています。

