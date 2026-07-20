import { StatusBadge } from '../../../components/AdminUi';
export function ReviewStatusBadge({status}:{status:string}){return <span aria-label={`Review status ${status}`}><StatusBadge value={status}/></span>}
