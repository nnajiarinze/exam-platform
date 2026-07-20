import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { listLearningObjectives, type StructureStatus } from '../../../api/generated';
import { contentServiceClient } from '../../../api/client/contentServiceClient';
import { unwrap } from '../../../api/client/adminApi';
import { adminQueryKeys } from '../../../api/query-keys/adminQueryKeys';
import { AsyncState } from '../../../components/AsyncState';

export function LearningObjectiveListPage() {
  const [search,setSearch]=useState(''); const [status,setStatus]=useState(''); const [page,setPage]=useState(0);
  const filters={search,status,page};
  const query=useQuery({queryKey:adminQueryKeys.objectives.list(filters),queryFn:()=>unwrap(listLearningObjectives({client:contentServiceClient,query:{page,size:20,search:search||undefined,status:(status||undefined) as StructureStatus|undefined}}))});
  return <><header className="page-header"><div><h1>Learning objectives</h1><p>Define what learners should understand.</p></div><div className="actions"><Link className="button" to="/knowledge">Knowledge facts</Link><Link className="button" to="/knowledge/objectives/new">Create objective</Link></div></header>
    <div className="filters"><label>Search<input value={search} onChange={e=>{setSearch(e.target.value);setPage(0)}} /></label><label>Status<select value={status} onChange={e=>{setStatus(e.target.value);setPage(0)}}><option value="">All</option><option>DRAFT</option><option>ACTIVE</option><option>ARCHIVED</option></select></label></div>
    <AsyncState loading={query.isPending} error={query.error}>{query.data?.items.length===0?<p>No learning objectives match these filters.</p>:<table><thead><tr><th>Code</th><th>Title</th><th>Subject / topic</th><th>Status</th><th>Updated</th></tr></thead><tbody>{query.data?.items.map(item=><tr key={item.id}><td>{item.code}</td><td><Link to={`/knowledge/objectives/${item.id}`}>{item.title}</Link></td><td>{item.subjectName} / {item.topicName}</td><td>{item.status}</td><td>{new Date(item.updatedAt).toLocaleDateString()}</td></tr>)}</tbody></table>}
      <div className="pager"><button disabled={page===0} onClick={()=>setPage(x=>x-1)}>Previous</button><span>Page {page+1} of {Math.max(query.data?.totalPages??1,1)}</span><button disabled={!query.data||page+1>=query.data.totalPages} onClick={()=>setPage(x=>x+1)}>Next</button></div></AsyncState></>;
}
