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

export const OMessagePlugin: Plugin = async () => {
  if (!TOPIC) {
    console.log("[omessage] OMESSAGE_TOPIC not set, plugin disabled")
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
      if (!res.ok) console.error("[omessage] push failed:", res.status, await res.text())
    } catch (e) {
      console.error("[omessage] push error:", e)
    }
  }

  return {
    event: async ({ event }: any) => {
      const type: string = event?.type ?? ""
      const props: any = event?.properties ?? event?.data ?? {}

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
          await push(payload, "权限请求 · " + permission, "max", "lock")
          break
        }
        case "question.asked": {
          const payload = {
            type: "question.asked",
            id: pick(props, "id", "requestID"),
            sessionID: props.sessionID ?? "",
            questions: props.questions ?? [],
          }
          await push(payload, "opencode 提问", "max", "grey_question")
          break
        }
        case "session.idle":
          await push({ type: "session.idle", sessionID: props.sessionID }, "执行完成", "default", "white_check_mark")
          break
        case "session.error": {
          const err = props.error ?? {}
          const payload = {
            type: "session.error",
            sessionID: props.sessionID,
            message: pick(err, "message") ?? pick(err?.data, "message") ?? "执行出错",
          }
          await push(payload, "执行失败", "high", "x")
          break
        }
        default:
          break
      }
    },
  }
}
