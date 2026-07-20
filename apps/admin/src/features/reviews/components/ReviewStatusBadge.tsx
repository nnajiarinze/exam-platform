export function ReviewStatusBadge({status}:{status:string}){return <span className="badge" aria-label={`Review status ${status}`}>{status.replaceAll('_',' ')}</span>}
