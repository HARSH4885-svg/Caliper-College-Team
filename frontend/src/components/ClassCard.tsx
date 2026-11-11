import { Link } from 'react-router-dom'
import type { Course } from '../api'
import { FlagBanner, Icon } from './brand'
import { Synthetic } from './ui'

export function ClassCard({ c, instructor }: { c: Course; instructor: boolean }) {
  return (
    <Link to={`/courses/${c.id}`} className={`cclass${c.archived ? ' archived' : ''}`}>
      <div className="bannerwrap"><FlagBanner seed={c.id} /></div>
      <div className="body">
        <div className="row space" style={{ marginBottom: 4 }}>
          <span className="code">{c.code}</span>
          <div className="row" style={{ gap: 6 }}>
            {c.synthetic && <Synthetic />}
            {c.archived && <span className="badge t-muted">Archived</span>}
          </div>
        </div>
        <h3>{c.title}</h3>
        <p className="small muted" style={{ margin: 0 }}>{c.instructorName}</p>
      </div>
      <div className="foot">
        <span className="small muted">
          {c.assignments} assignment{c.assignments === 1 ? '' : 's'}
          {instructor && <span> / {c.students} student{c.students === 1 ? '' : 's'}</span>}
        </span>
        <span className="go">Open <Icon name="right" size={14} /></span>
      </div>
    </Link>
  )
}
