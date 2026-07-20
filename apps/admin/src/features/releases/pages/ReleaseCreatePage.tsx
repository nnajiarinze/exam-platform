import {useState} from 'react';
import {useMutation,useQuery} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';
import {createRelease,listExams,listExamVersions} from '../../../api/generated';
import {contentServiceClient} from '../../../api/client/contentServiceClient';
import {unwrap} from '../../../api/client/adminApi';

export function ReleaseCreatePage(){
  const nav=useNavigate();
  const[examId,setExamId]=useState('');const[examVersionId,setExamVersionId]=useState('');
  const[releaseNumber,setReleaseNumber]=useState('');const[displayName,setDisplayName]=useState('');const[description,setDescription]=useState('');
  const exams=useQuery({queryKey:['release-create-exams'],queryFn:()=>unwrap(listExams({client:contentServiceClient,query:{page:0,size:100}}))});
  const versions=useQuery({queryKey:['release-create-versions',examId],enabled:!!examId,queryFn:()=>unwrap(listExamVersions({client:contentServiceClient,path:{examId}}))});
  const create=useMutation({mutationFn:()=>unwrap(createRelease({client:contentServiceClient,body:{examVersionId,releaseNumber,displayName,description:description||undefined}})),onSuccess:r=>nav(`/releases/${r.id}`)});
  return <><header className="page-header"><h1>Create release</h1><p>Start a draft for one exam version. Content selection happens in the workspace.</p></header><form className="form" onSubmit={e=>{e.preventDefault();create.mutate()}}><label>Exam<select required value={examId} onChange={e=>{setExamId(e.target.value);setExamVersionId('')}}><option value="">Select exam</option>{exams.data?.items.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></label><label>Exam version<select required value={examVersionId} onChange={e=>setExamVersionId(e.target.value)}><option value="">Select version</option>{versions.data?.map(x=><option key={x.id} value={x.id}>{x.displayName}</option>)}</select></label><label>Release number<input required value={releaseNumber} onChange={e=>setReleaseNumber(e.target.value)} placeholder="2026.1"/></label><label>Display name<input required value={displayName} onChange={e=>setDisplayName(e.target.value)}/></label><label>Description<textarea value={description} onChange={e=>setDescription(e.target.value)}/></label>{create.error&&<p className="error">{create.error.message}</p>}<button disabled={create.isPending}>Create draft</button></form></>;
}
