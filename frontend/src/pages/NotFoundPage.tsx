import { Link, useLocation } from 'react-router-dom'
import { Icon } from '../components/brand'
import { Empty } from '../components/ui'

/** Unknown addresses used to bounce silently to Home; now they say so, because a mistyped link should not look like a working one. */
export function NotFoundPage() {
  const { pathname } = useLocation()
  return (
    <div className="card" style={{ textAlign: 'center', padding: '48px 24px' }}>
      <Empty icon="flag">There is nothing at <code>{pathname}</code>. The link may be old, or the page may belong to someone else.</Empty>
      <div className="row" style={{ justifyContent: 'center', marginTop: 8 }}>
        <Link className="btn primary" to="/"><Icon name="home" />Back to Home</Link>
        <Link className="btn" to="/courses"><Icon name="classes" />Your classes</Link>
      </div>
    </div>
  )
}
