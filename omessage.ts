import type { Plugin } from "@opencode-ai/plugin"

const TOPIC = process.env.OMESSAGE_TOPIC ?? ""
const NTFY_URL = (process.env.OMESSAGE_NTFY_URL ?? "https://ntfy.sh").replace(/\/$/, "")

function pick(obj: any, ...keys: string[]): any {
  for (const k of keys) {
    if (obj && obj[k] !== undefined && obj[k] !== null) return obj[k]
  }
  return undefined
}

export const OMessagePlugin: Plugin = async () => {
  if (!TOPIC) {
    console.log("[omessage] OMESSAGE_TOPIC not set, plugin disabled")
    return {}
  }

  return {
    event: async ({ event }: any) => {
      const type = event?.type ?? ""
      const props: any = event?.properties ?? event?.data ?? event ?? {}

      let title = "opencode"
      let priority = "default"
      let tags = "information_source"
      let payload: any = { type }

      switch (type) {
        case "permission.asked": {
          const permission = pick(props, "permission", "action") ?? "unknown"
          const patterns = pick(props, "patterns", "resources") ?? []
          payload = {
            type,
            id: pick(props, "id", "requestID"),
            sessionID: props.sessionID,
            permission,
            patterns,
          }
          title = "权限请求 · " + permission
          priority = "max"
          tags = "lock"
          break
        }
        case "question.asked": {
          payload = {
            type,
            id: pick(props, "id", "requestID"),
            sessionID: props.sessionID,
            questions: props.questions ?? [],
          }
          title = "opencode 提问"
          priority = "max"
          tags = "grey_question"
          break
        }
        case "session.idle": {
          payload = { type, sessionID: props.sessionID }
          title = "执行完成"
          tags = "white_check_mark"
          break
        }
        case "session.error": {
          const err = props.error ?? {}
          payload = {
            type,
            sessionID: props.sessionID,
            message: pick(err, "message") ?? pick(err.data, "message") ?? "执行出错",
          }
          title = "执行失败"
          priority = "high"
          tags = "x"
          break
        }
        default:
          return
      }

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
        if (!res.ok) {
          console.error("[omessage] push failed:", res.status, await res.text())
        }
      } catch (e) {
        console.error("[omessage] push error:", e)
      }
    },
  }
}
