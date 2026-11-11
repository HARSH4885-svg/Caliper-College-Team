import { useMemo, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, tokenStore, type Material, type ThreadRow, type ThreadView } from '../api'
import { Avatar, Icon } from '../components/brand'
import { Empty, ErrorBox, Field, Loading, PageHead } from '../components/ui'
import { when } from '../labels'
import { useFetch } from '../useFetch'

const PREVIEWABLE = /\.(pdf|png|jpe?g|gif|webp|txt|md|csv|py|java|c|cpp|js|sql|json)$/i
const size = (n: number | null) => n == null ? '' : n < 1024 ? `${n} B` : n < 1 << 20 ? `${(n / 1024).toFixed(0)} KB` : `${(n / (1 << 20)).toFixed(1)} MB`

async function fileBlob(id: number) {
  const res = await fetch(`/api/materials/${id}/file`, { headers: { Authorization: `Bearer ${tokenStore.get() ?? ''}` } })
  if (!res.ok) throw new Error('Could not open the file')
  return res.blob()
}

/** Course materials grouped into modules. Staff add links, notes and files and reorder them; everyone can open and preview. */
export function MaterialsPanel({ courseId, inst }: { courseId: number; inst: boolean }) {
  const q = useFetch(() => api.get<Material[]>(`/api/courses/${courseId}/materials`), [courseId])
  const [err, setErr] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState({ module: '', title: '', kind: 'LINK' as 'LINK' | 'NOTE' | 'FILE', url: '', body: '' })
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<{ title: string; url: string; type: string } | null>(null)
  const [search, setSearch] = useState('')
  const [open, setOpen] = useState<number | null>(null)
  const items = (q.data ?? []).filter(m => !search.trim() || `${m.title} ${m.module} ${m.body}`.toLowerCase().includes(search.trim().toLowerCase()))
  const modules = useMemo(() => { const m = new Map<string, Material[]>(); for (const x of items) m.set(x.module, [...(m.get(x.module) ?? []), x]); return [...m.entries()] }, [items])
  const run = async (fn: () => Promise<unknown>) => { setErr(null); try { await fn(); await q.reload(true) } catch (e) { setErr((e as Error).message) } }

  async function add(e: FormEvent) {
    e.preventDefault()
    await run(async () => {
      if (f.kind === 'FILE') { if (!file) throw new Error('Choose a file'); const fd = new FormData(); fd.append('file', file); fd.append('module', f.module); fd.append('title', f.title); await api.upload(`/api/courses/${courseId}/materials/file`, fd) }
      else await api.post(`/api/courses/${courseId}/materials`, f)
      setF({ ...f, title: '', url: '', body: '' }); setFile(null); setAdding(false)
    })
  }
  async function openFile(m: Material) {
    try {
      const b = await fileBlob(m.id)
      if (m.fileName && PREVIEWABLE.test(m.fileName)) setPreview({ title: m.title, url: URL.createObjectURL(b), type: b.type })
      else { const a = document.createElement('a'); a.href = URL.createObjectURL(b); a.download = m.fileName ?? 'file'; a.click(); URL.revokeObjectURL(a.href) }
    } catch (e) { setErr((e as Error).message) }
  }
  const moduleNames = [...new Set((q.data ?? []).map(m => m.module).filter(Boolean))]

  return (
    <div id="panel-materials" role="tabpanel">
      <div className="toolbar">
        <div className="searchfield"><Icon name="search" size={16} /><input type="search" aria-label="Search materials" placeholder="Search materials" value={search} onChange={e => setSearch(e.target.value)} /></div>
        <span className="grow" />{inst && <button className="primary" onClick={() => setAdding(!adding)} aria-expanded={adding}><Icon name="plus" />Add material</button>}
      </div>
      <ErrorBox message={err ?? q.error} />
      {inst && adding && (
        <form className="card" onSubmit={add}>
          <div className="segmented" role="radiogroup" aria-label="Kind" style={{ maxWidth: 360 }}>{(['LINK', 'NOTE', 'FILE'] as const).map(k => <button key={k} type="button" role="radio" aria-checked={f.kind === k} className={f.kind === k ? 'on' : ''} onClick={() => setF({ ...f, kind: k })}>{k === 'LINK' ? 'Link' : k === 'NOTE' ? 'Note' : 'File'}</button>)}</div>
          <div className="grid g2">
            <Field id="m-mod" label="Module or week" hint="Items with the same module are grouped."><input id="m-mod" value={f.module} onChange={e => setF({ ...f, module: e.target.value })} list="m-mods" maxLength={100} placeholder="Week 1: Introduction" /><datalist id="m-mods">{moduleNames.map(m => <option key={m} value={m} />)}</datalist></Field>
            <Field id="m-title" label={f.kind === 'FILE' ? 'Title (optional)' : 'Title'}><input id="m-title" value={f.title} onChange={e => setF({ ...f, title: e.target.value })} maxLength={150} required={f.kind !== 'FILE'} /></Field>
          </div>
          {f.kind === 'LINK' && <Field id="m-url" label="Address"><input id="m-url" type="url" value={f.url} onChange={e => setF({ ...f, url: e.target.value })} placeholder="https://" required /></Field>}
          {f.kind === 'NOTE' && <Field id="m-body" label="Note"><textarea id="m-body" value={f.body} onChange={e => setF({ ...f, body: e.target.value })} required style={{ fontFamily: 'var(--body)', fontSize: '1rem' }} /></Field>}
          {f.kind === 'FILE' && <Field id="m-file" label="File" hint="PDF, images, text or code, Office documents, notebooks or .zip, up to 10 MiB."><input id="m-file" type="file" onChange={e => setFile(e.target.files?.[0] ?? null)} /></Field>}
          <div className="row"><button className="primary" type="submit">Add</button><button type="button" className="quiet" onClick={() => setAdding(false)}>Cancel</button></div>
        </form>)}
      {q.loading ? <Loading /> : (q.data ?? []).length === 0 ? <Empty icon="file">{inst ? 'No materials yet. Add slides, notes and links, grouped by week or topic.' : 'Your instructor has not shared any materials yet.'}</Empty>
        : items.length === 0 ? <Empty icon="search">Nothing matches.</Empty> : modules.map(([mod, list]) => (
          <section key={mod} className="module"><h3>{mod || 'General'}</h3>
            <ul className="card mlist">{list.map((m, i) => (
              <li key={m.id}>
                <span className={`mico k-${m.kind.toLowerCase()}`}><Icon name={m.kind === 'LINK' ? 'link' : m.kind === 'FILE' ? 'file' : 'edit'} size={18} /></span>
                <div className="mmain">
                  {m.kind === 'LINK' ? <a href={m.url ?? '#'} target="_blank" rel="noopener noreferrer">{m.title}</a>
                    : m.kind === 'FILE' ? <button className="linklike" onClick={() => openFile(m)}>{m.title}</button>
                    : <button className="linklike" onClick={() => setOpen(open === m.id ? null : m.id)} aria-expanded={open === m.id}>{m.title}</button>}
                  <div className="small muted">{m.kind === 'LINK' ? new URL(m.url ?? 'https://x').hostname : m.kind === 'FILE' ? `${m.fileName} · ${size(m.sizeBytes)}` : 'Note'} · {when(m.createdAt)}</div>
                  {m.kind === 'NOTE' && open === m.id && <p className="mnote">{m.body}</p>}
                </div>
                {inst && <div className="row" style={{ flexWrap: 'nowrap' }}>
                  <button className="quiet icononly" disabled={i === 0} aria-label={`Move ${m.title} up`} onClick={() => run(() => api.patch(`/api/materials/${m.id}`, { move: 'up' }))}>↑</button>
                  <button className="quiet icononly" disabled={i === list.length - 1} aria-label={`Move ${m.title} down`} onClick={() => run(() => api.patch(`/api/materials/${m.id}`, { move: 'down' }))}>↓</button>
                  <button className="quiet icononly" aria-label={`Delete ${m.title}`} onClick={() => { if (window.confirm(`Delete “${m.title}”?`)) void run(() => api.delete(`/api/materials/${m.id}`)) }}><Icon name="trash" size={15} /></button></div>}
              </li>))}</ul></section>))}
      {preview && (
        <div className="overlay" onMouseDown={e => { if (e.target === e.currentTarget) { URL.revokeObjectURL(preview.url); setPreview(null) } }}>
          <div className="dialog wide previewer" role="dialog" aria-modal="true" aria-label={preview.title}>
            <div className="row space"><h2 style={{ margin: 0 }}>{preview.title}</h2><div className="row"><a className="btn quiet" href={preview.url} download={preview.title}>Download</a><button className="quiet" onClick={() => { URL.revokeObjectURL(preview.url); setPreview(null) }}>Close</button></div></div>
            {preview.type.startsWith('image/') ? <img src={preview.url} alt={preview.title} /> : <iframe title={preview.title} src={preview.url} sandbox={preview.type === 'application/pdf' ? undefined : ''} />}
          </div>
        </div>)}
    </div>
  )
}

/** Course Q&A list with search and filters, and a form to ask a question. */
export function DiscussionsPanel({ courseId, inst }: { courseId: number; inst: boolean }) {
  const [filter, setFilter] = useState('all')
  const [search, setSearch] = useState('')
  const [term, setTerm] = useState('')
  const q = useFetch(() => api.get<ThreadRow[]>(`/api/courses/${courseId}/threads?filter=${filter}${term ? `&q=${encodeURIComponent(term)}` : ''}`), [courseId, filter, term])
  const reports = useFetch(() => (inst ? api.get<{ id: number; threadId: number; thread: string; reason: string; reporter: string; at: string }[]>(`/api/courses/${courseId}/reports`) : Promise.resolve([])), [courseId, inst])
  const [asking, setAsking] = useState(false)
  const [title, setTitle] = useState(''), [body, setBody] = useState('')
  const [err, setErr] = useState<string | null>(null)
  async function ask(e: FormEvent) {
    e.preventDefault(); setErr(null)
    try { await api.post(`/api/courses/${courseId}/threads`, { title, body }); setTitle(''); setBody(''); setAsking(false); await q.reload(true) } catch (x) { setErr((x as Error).message) }
  }
  return (
    <div id="panel-discuss" role="tabpanel">
      {inst && (reports.data?.length ?? 0) > 0 && <div className="card reports"><h2><Icon name="flag" />Reported posts</h2>
        <ul>{reports.data!.map(r => <li key={r.id}><Link to={`/threads/${r.threadId}`}>{r.thread}</Link><span className="small muted"> · “{r.reason}” · reported by {r.reporter}, {when(r.at)}</span>
          <button className="quiet" onClick={async () => { await api.post(`/api/reports/${r.id}/resolve`); await reports.reload(true) }}>Mark handled</button></li>)}</ul></div>}
      <div className="toolbar">
        <form className="searchfield" onSubmit={e => { e.preventDefault(); setTerm(search.trim()) }}><Icon name="search" size={16} /><input type="search" aria-label="Search discussions" placeholder="Search discussions" value={search} onChange={e => { setSearch(e.target.value); if (!e.target.value) setTerm('') }} /></form>
        <div className="segmented" role="tablist" aria-label="Show" style={{ width: 330, margin: 0 }}>{[['all', 'All'], ['unanswered', 'Unanswered'], ['mine', 'Mine']].map(([k, l]) => <button key={k} type="button" role="tab" aria-selected={filter === k} className={filter === k ? 'on' : ''} onClick={() => setFilter(k)}>{l}</button>)}</div>
        <span className="grow" /><button className="primary" onClick={() => setAsking(!asking)} aria-expanded={asking}><Icon name="chat" />{inst ? 'Start a thread' : 'Ask a question'}</button>
      </div>
      <ErrorBox message={err ?? q.error} />
      {asking && <form className="card" onSubmit={ask}>
        <Field id="t-title" label="Title" hint="One line that says what you need."><input id="t-title" value={title} onChange={e => setTitle(e.target.value)} maxLength={150} required /></Field>
        <Field id="t-body" label="Details" hint="Explain what you tried. Do not paste a full solution to graded work."><textarea id="t-body" value={body} onChange={e => setBody(e.target.value)} maxLength={10000} required style={{ fontFamily: 'var(--body)', fontSize: '1rem' }} /></Field>
        <div className="row"><button className="primary" type="submit">Post</button><button type="button" className="quiet" onClick={() => setAsking(false)}>Cancel</button></div></form>}
      {q.loading ? <Loading /> : (q.data ?? []).length === 0 ? <Empty icon="chat">{filter === 'all' && !term ? 'No discussions yet. Questions asked here help the whole class.' : 'Nothing matches.'}</Empty> : (
        <ul className="card threads">{q.data!.map(t => (
          <li key={t.id}><Link to={`/threads/${t.id}`} className="thread">
            <Avatar name={t.author} size={34} />
            <div className="tmain"><strong>{t.pinned && <Icon name="pin" size={14} />} {t.title}</strong>
              <div className="small muted">{t.author}{t.authorIsStaff && ' (staff)'} · {when(t.createdAt)}{t.locked && ' · locked'}{t.hidden && ' · hidden'}</div></div>
            <span className={`badge ${t.answered ? 't-ok' : 't-muted'}`}>{t.answered ? 'Answered' : 'Open'}</span>
            <span className="small muted treplies"><Icon name="chat" size={14} /> {t.replies}</span>
          </Link></li>))}</ul>)}
    </div>
  )
}

export function ThreadPage() {
  const { id } = useParams()
  const q = useFetch(() => api.get<ThreadView>(`/api/threads/${id}`), [id])
  const [reply, setReply] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [reporting, setReporting] = useState<number | null>(null)
  const [reason, setReason] = useState('')
  const [msg, setMsg] = useState<string | null>(null)
  const act = async (fn: () => Promise<unknown>, ok?: string) => { setErr(null); setMsg(null); try { await fn(); await q.reload(true); if (ok) setMsg(ok) } catch (e) { setErr((e as Error).message) } }
  if (q.loading) return <Loading />
  const t = q.data
  if (!t) return <ErrorBox message={q.error} />
  return (
    <>
      <PageHead back={{ to: `/courses/${t.courseId}?tab=discuss`, label: 'Discussions' }} title={<>{t.pinned && <Icon name="pin" size={20} />} {t.title}</>}
        actions={t.canModerate ? <>
          <button onClick={() => act(() => api.post(`/api/threads/${t.id}/moderate`, { pinned: !t.pinned }))}><Icon name="pin" size={16} />{t.pinned ? 'Unpin' : 'Pin'}</button>
          <button onClick={() => act(() => api.post(`/api/threads/${t.id}/moderate`, { locked: !t.locked }))}><Icon name="lock" size={16} />{t.locked ? 'Unlock' : 'Lock'}</button>
          <button className="quiet" onClick={() => act(() => api.post(`/api/threads/${t.id}/moderate`, { hidden: !t.hidden }))}>{t.hidden ? 'Unhide thread' : 'Hide thread'}</button></> : undefined} />
      <ErrorBox message={err} />{msg && <div className="notice" role="status"><Icon name="check" />{msg}</div>}
      <article className="card post-op"><header className="row"><Avatar name={t.author} size={36} /><div><strong>{t.author}</strong>{t.authorIsStaff && <span className="badge t-review" style={{ marginLeft: 8 }}>Staff</span>}<div className="small muted">{when(t.createdAt)}</div></div></header>
        <p className="postbody">{t.body}</p></article>
      <h2>{t.posts.length} {t.posts.length === 1 ? 'reply' : 'replies'}</h2>
      {t.posts.map(p => (
        <article key={p.id} className={`card reply${p.staff ? ' staff' : ''}${p.endorsed ? ' endorsed' : ''}${p.hidden ? ' hiddenpost' : ''}`}>
          <header className="row space"><div className="row"><Avatar name={p.author} size={30} /><strong>{p.author}</strong>{p.staff && <span className="badge t-review">Staff answer</span>}{p.endorsed && <span className="badge t-ok"><Icon name="check" size={12} />Endorsed by staff</span>}<span className="small muted">{when(p.createdAt)}</span></div>
            <div className="row">
              {t.canModerate && !p.staff && <button className="quiet" onClick={() => act(() => api.post(`/api/posts/${p.id}/endorse?on=${!p.endorsed}`))}>{p.endorsed ? 'Remove endorsement' : 'Endorse'}</button>}
              {(t.canModerate || (p.mine && !p.hidden)) && <button className="quiet" onClick={() => act(() => api.post(`/api/posts/${p.id}/hide?on=${!p.hidden}`))}>{p.hidden ? 'Restore' : p.mine && !t.canModerate ? 'Delete' : 'Hide'}</button>}
              {!p.mine && !t.canModerate && !p.hidden && <button className="quiet" onClick={() => { setReporting(p.id); setReason('') }}>Report</button>}
            </div></header>
          {p.hidden && !t.canModerate ? <p className="muted small"><em>Removed{p.mine ? ' by you' : ' by a moderator'}.</em></p> : <p className="postbody">{p.body}{p.hidden && <em className="small muted"> (hidden from students)</em>}</p>}
          {reporting === p.id && <form className="row" onSubmit={e => { e.preventDefault(); void act(async () => { await api.post(`/api/threads/${t.id}/report`, { postId: p.id, reason }); setReporting(null) }, 'Reported. Staff will take a look.') }}>
            <input aria-label="Reason for reporting" value={reason} onChange={e => setReason(e.target.value)} maxLength={300} required placeholder="What is wrong with this post?" style={{ flex: 1 }} /><button type="submit">Send report</button><button type="button" className="quiet" onClick={() => setReporting(null)}>Cancel</button></form>}
        </article>))}
      {t.locked && !t.canModerate ? <div className="notice"><Icon name="lock" />This thread is locked.</div> : (
        <form className="card" onSubmit={e => { e.preventDefault(); void act(async () => { await api.post(`/api/threads/${t.id}/posts`, { body: reply }); setReply('') }) }}>
          <Field id="reply" label="Your reply"><textarea id="reply" value={reply} onChange={e => setReply(e.target.value)} maxLength={10000} required style={{ fontFamily: 'var(--body)', fontSize: '1rem' }} /></Field>
          <button className="primary" type="submit" disabled={!reply.trim()}>Reply</button></form>)}
    </>
  )
}
