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

  const resp = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${jwt}`,
  })
  const { access_token } = await resp.json()
  return access_token
}

Deno.serve(async (req) => {
  try {
    const body = await req.json()
    const record = body.record
    if (!record) return new Response('No record', { status: 400 })

    const { chat_id, sender_id, content, type } = record
    if (!['text', 'media', 'system'].includes(type)) {
      return new Response('Skip unsupported message type', { status: 200 })
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY)

    // Get chat members excluding sender
    const { data: members } = await supabase
      .from('chat_members')
      .select('user_id')
      .eq('chat_id', chat_id)
      .neq('user_id', sender_id)

    if (!members?.length) return new Response('No recipients', { status: 200 })

    const recipientIds = members.map((m: any) => m.user_id)

    // Get FCM tokens for all recipients
    const { data: tokenRows } = await supabase
      .from('push_tokens')
      .select('token')
      .in('user_id', recipientIds)

    if (!tokenRows?.length) return new Response('No tokens', { status: 200 })

    // Get sender profile (with avatar) and chat info in parallel
    const [{ data: sender }, { data: chat }] = await Promise.all([
      supabase.from('profiles').select('display_name, emoji, bg_color').eq('id', sender_id).single(),
      supabase.from('chats').select('name, type').eq('id', chat_id).single(),
    ])

    const senderName = sender?.display_name ?? 'Кто-то'
    const isGroup = chat?.type === 'group'
    const chatName = chat?.name ?? ''

    const notificationTitle = isGroup ? chatName || 'Групповой чат' : senderName
    let notificationBody: string
    if (type === 'system') {
      notificationBody = content
    } else if (type === 'media') {
      notificationBody = isGroup ? `${senderName}: 📷 Медиа` : '📷 Медиа'
    } else {
      notificationBody = isGroup ? `${senderName}: ${content}` : content
    }

    // Avatar: for group — first letter of chat name on dark circle; for personal — sender emoji + color
    const avatarEmoji = sender?.emoji ?? '😊'
    const avatarColor = isGroup ? '#455A64' : (sender?.bg_color ?? '#5C6BC0')
    const avatarLetter = chatName.slice(0, 1).toUpperCase()

    const accessToken = await getFcmAccessToken()
    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/messages:send`

    await Promise.all(tokenRows.map(({ token }: { token: string }) =>
      fetch(fcmUrl, {
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
              chat_id,
              sender_id,
              title: notificationTitle,
              body: notificationBody,
              avatar_emoji: avatarEmoji,
              avatar_color: avatarColor,
              avatar_letter: avatarLetter,
              is_group: isGroup ? 'true' : 'false',
            },
            android: {
              priority: 'HIGH',
              ttl: '86400s',
            },
          },
        }),
      })
    ))

    return new Response('OK', { status: 200 })
  } catch (err) {
    console.error('send-push error:', err)
    return new Response('Error', { status: 500 })
  }
})
