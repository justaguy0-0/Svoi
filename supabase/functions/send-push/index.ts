import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const SUPABASE_URL = Deno.env.get('SUPABASE_URL')!
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
const FIREBASE_PROJECT_ID = Deno.env.get('FIREBASE_PROJECT_ID')!
const FIREBASE_SERVICE_ACCOUNT = JSON.parse(Deno.env.get('FIREBASE_SERVICE_ACCOUNT')!)

function base64url(data: Uint8Array | string): string {
  let binary: string
  if (typeof data === 'string') {
    binary = btoa(unescape(encodeURIComponent(data)))
  } else {
    let str = ''
    for (let i = 0; i < data.length; i++) str += String.fromCharCode(data[i])
    binary = btoa(str)
  }
  return binary.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '')
}

async function fetchWithTimeout(
  url: string | URL | Request,
  options: RequestInit = {},
  timeoutMs = 10000,
): Promise<Response> {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs)

  try {
    return await fetch(url, {
      ...options,
      signal: controller.signal,
    })
  } finally {
    clearTimeout(timeoutId)
  }
}

async function getFcmAccessToken(): Promise<string> {
  const { private_key, client_email } = FIREBASE_SERVICE_ACCOUNT
  const now = Math.floor(Date.now() / 1000)

  const header = base64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))
  const payload = base64url(JSON.stringify({
    iss: client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }))

  const signingInput = `${header}.${payload}`

  const keyData = private_key
    .replace(/-----BEGIN PRIVATE KEY-----/g, '')
    .replace(/-----END PRIVATE KEY-----/g, '')
    .replace(/\n/g, '')
  const binaryKey = Uint8Array.from(atob(keyData), (c) => c.charCodeAt(0))

  const cryptoKey = await crypto.subtle.importKey(
    'pkcs8', binaryKey,
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false, ['sign']
  )

  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5', cryptoKey,
    new TextEncoder().encode(signingInput)
  )

  const jwt = `${signingInput}.${base64url(new Uint8Array(signature))}`

  console.log('FCM access token request start')
  const resp = await fetchWithTimeout('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  })
  const responseBody = await resp.text()

  if (!resp.ok) {
    console.error('FCM access token request failed', {
      status: resp.status,
      body: responseBody,
    })
    throw new Error(`FCM access token request failed status=${resp.status}`)
  }

  const { access_token } = JSON.parse(responseBody)
  if (!access_token) {
    throw new Error(`FCM access token missing in response: ${responseBody}`)
  }

  console.log('FCM access token received')
  return access_token
}

function asFcmDataString(value: unknown, fallback = ''): string {
  if (value === null || value === undefined) return fallback
  return String(value)
}

Deno.serve(async (req) => {
  try {
    console.log('send-push: request received')
    const body = await req.json()
    const record = body.record
    if (!record) return new Response('No record', { status: 400 })
    console.log('send-push: record parsed')

    const { id, chat_id, sender_id, content, type, silent, mentioned_user_ids, forwarded_from_id } = record
    if (silent) return new Response('Silent message — skip push', { status: 200 })
    const SUPPORTED_TYPES = ['text', 'photo', 'album', 'video', 'voice', 'file', 'system']
    if (!SUPPORTED_TYPES.includes(type)) {
      return new Response('Skip unsupported message type', { status: 200 })
    }

    // mentioned_user_ids may be null (old rows) or an array
    const mentionedIds: string[] = Array.isArray(mentioned_user_ids) ? mentioned_user_ids : []

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

    // Get chat members excluding sender
    const { data: members } = await supabase
      .from('chat_members')
      .select('user_id')
      .eq('chat_id', chat_id)
      .neq('user_id', sender_id)

    console.log(`send-push: recipients count = ${members?.length ?? 0}`)
    if (!members?.length) return new Response('No recipients', { status: 200 })

    const recipientIds = members.map((m: any) => m.user_id)

    // Get FCM tokens for all recipients — include user_id so we can detect mentions per device
    const { data: tokenRows } = await supabase
      .from('push_tokens')
      .select('token, user_id')
      .in('user_id', recipientIds)

    console.log(`send-push: tokens count = ${tokenRows?.length ?? 0}`)
    if (!tokenRows?.length) return new Response('No tokens', { status: 200 })

    // Get sender profile (with avatar) and chat info in parallel
    const [{ data: sender }, { data: chat }] = await Promise.all([
      supabase.from('profiles').select('display_name, emoji, bg_color').eq('id', sender_id).single(),
      supabase.from('chats').select('name, type').eq('id', chat_id).single(),
    ])

    const messageId = asFcmDataString(id)
    const senderName = asFcmDataString(sender?.display_name, 'Кто-то')
    const isGroup = chat?.type === 'group'
    const chatTitle = asFcmDataString(chat?.name)
    const isForwarded = Boolean(forwarded_from_id)

    const notificationTitle = isGroup ? chatTitle || 'Групповой чат' : senderName
    let messagePreview: string
    if (isForwarded) {
      messagePreview = 'Пересланное сообщение'
    } else if (type === 'system') {
      messagePreview = asFcmDataString(content)
    } else if (type === 'photo') {
      messagePreview = content ? `📷 Фото: ${asFcmDataString(content)}` : '📷 Фото'
    } else if (type === 'album') {
      messagePreview = content ? `📷 Фото: ${asFcmDataString(content)}` : '📷 Фото'
    } else if (type === 'video') {
      messagePreview = content ? `🎥 Видео: ${asFcmDataString(content)}` : '🎥 Видео'
    } else if (type === 'voice') {
      messagePreview = '🎤 Голосовое сообщение'
    } else if (type === 'file') {
      messagePreview = `📎 ${asFcmDataString(record.file_name, 'Файл')}`
    } else {
      messagePreview = asFcmDataString(content)
    }

    // MessagingStyle on the client side shows sender name inline — no manual prefix needed
    const notificationBody = messagePreview

    // Always use the sender's personal avatar (emoji + bg_color)
    // The group name is conveyed via conversationTitle in MessagingStyle, not the avatar
    const avatarEmoji = asFcmDataString(sender?.emoji, '😊')
    const avatarColor = asFcmDataString(sender?.bg_color, '#5C6BC0')

    const accessToken = await getFcmAccessToken()
    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`

    console.log('send-push: sending FCM messages', {
      chat_id,
      sender_id,
      'tokenRows.length': tokenRows.length,
      'recipientIds.length': recipientIds.length,
      'message type': type,
    })

    const results = await Promise.allSettled(tokenRows.map(async ({ token, user_id }: { token: string; user_id: string }) => {
      const isMention = mentionedIds.includes(user_id)
      try {
        const response = await fetchWithTimeout(fcmUrl, {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            message: {
              token,
              // Data-only message — onMessageReceived is always called (foreground + background + killed)
              data: {
                chat_id: asFcmDataString(chat_id),
                sender_id: asFcmDataString(sender_id),
                message_id: asFcmDataString(messageId),
                title: asFcmDataString(notificationTitle),
                body: asFcmDataString(notificationBody),
                sender_name: asFcmDataString(senderName),
                chat_title: asFcmDataString(chatTitle),
                msg_type: asFcmDataString(type),
                preview: asFcmDataString(messagePreview),
                is_forwarded: isForwarded ? 'true' : 'false',
                avatar_emoji: asFcmDataString(avatarEmoji),
                avatar_color: asFcmDataString(avatarColor),
                is_group: isGroup ? 'true' : 'false',
                // Flag for Android: bypass mute if the user was @-mentioned
                is_mention: isMention ? 'true' : 'false',
              },
              android: {
                priority: 'HIGH',
                ttl: '86400s',
              },
            },
          }),
        })
        const responseBody = await response.text()

        if (!response.ok) {
          console.error('FCM send failed', {
            status: response.status,
            body: responseBody,
            user_id,
          })
        }

        return { ok: response.ok, status: response.status }
      } catch (err) {
        console.error('FCM send request failed', {
          error: err,
          user_id,
        })
        return { ok: false, status: 0 }
      }
    }))

    const sent = results.filter((result) => result.status === 'fulfilled' && result.value.ok).length
    const failed = results.length - sent

    console.log(`send-push: sending completed sent=${sent} failed=${failed}`)
    return new Response(`sent=${sent} failed=${failed}`, { status: 200 })
  } catch (err) {
    console.error('send-push error:', err)
    return new Response('Error', { status: 500 })
  }
})
