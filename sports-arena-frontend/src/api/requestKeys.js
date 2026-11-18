// Keep an unacknowledged operation's key across page reloads and network failures.
export function persistentRequestKey(userId, kind, payload) {
    const storageKey = `sports-arena:${userId}:${kind}:${JSON.stringify(payload)}`;
    let key = localStorage.getItem(storageKey);
    if (!key) {
        key = crypto.randomUUID();
        localStorage.setItem(storageKey, key);
    }
    return { key, clear: () => localStorage.removeItem(storageKey) };
}
