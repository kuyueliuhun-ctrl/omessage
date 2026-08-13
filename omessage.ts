import type { Plugin } from "@opencode-ai/plugin"

const TOPIC = process.env.OMESSAGE_TOPIC ?? ""
const NTFY_URL = (process.env.OMESSAGE_NTFY_URL ?? "https://ntfy.sh").replace(/\/$/, "")

function pick(obj: any, ...keys: string[]): any {
  for (const k of keys) {
    if (obj && obj[k] !== undefined && obj[k] !== null) return obj[k]
  }
  return undefined
}

function toArray(v: any): any[] {
  if (v === undefined || v === null) return []
  return Array.isArray(v) ? v : [v]
}

export const OMessagePlugin: Plugin = async ({ client }) => {
  const log = async (level: "debug" | "info" | "warn" | "error", message: string, extra?: any) => {
    try {
      await client.app.log({ body: { service: "omessage", level, message, extra } })
    } catch {
      // ignore logging failures
    }
  }

  await log("info", `plugin loaded, TOPIC=${TOPIC || "<EMPTY>"}`)

  if (!TOPIC) {
    await log("warn", "OMESSAGE_TOPIC not set, plugin disabled")
    return {}
  }

  async function push(payload: any, title: string, priority: string, tags: string) {
    try {
      const res = await fetch(`${NTFY_URL}/${TOPIC}`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Title: title,
          Priority: priority,
          Tags: tags,
        },
        body: JSON.stringify(payload),
      })
      await log("info", `push "${title}" -> HTTP ${res.status}`)
      if (!res.ok) await log("error", `push failed: ${res.status} ${await res.text()}`)
    } catch (e: any) {
      await log("error", `push error: ${e?.message ?? String(e)}`)
    }
  }

  const seenTypes = new Set<string>()

  async function onEvent(type: string, props: any) {
    if (type && !seenTypes.has(type)) {
      seenTypes.add(type)
      await log("info", `event seen: ${type}`, JSON.stringify(props))
    }
    switch (type) {
      case "permission.updated":
      case "permission.asked": {
        const permission = pick(props, "permission", "type", "action") ?? "unknown"
        const patterns = toArray(pick(props, "patterns", "pattern", "resources"))
        const payload = {
          type: "permission.asked",
          id: pick(props, "id", "requestID"),
          sessionID: props.sessionID ?? "",
          permission,
          patterns,
          title: pick(props, "title") ?? permission,
        }
      await push(payload, "permission:" + permission, "max", "lock")
        break
      }
      case "question.asked": {
        const payload = {
          type: "question.asked",
          id: pick(props, "id", "requestID"),
          sessionID: props.sessionID ?? "",
          questions: props.questions ?? [],
        }
          await push(payload, "question", "max", "grey_question")
        break
      }
      case "session.idle":
        await push({ type: "session.idle", sessionID: props.sessionID }, "idle", "default", "white_check_mark")
        break
      case "session.error": {
        const err = props.error ?? {}
        const payload = {
          type: "session.error",
          sessionID: props.sessionID,
          message: pick(err, "message") ?? pick(err?.data, "message") ?? "执行出错",
        }
          await push(payload, "error", "high", "x")
        break
      }
      default:
        break
    }
  }

  return {
    event: async ({ event }: any) => {
      await onEvent(event?.type ?? "", event?.properties ?? event?.data ?? {})
    },
  }
}
