import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import {
  createAiQuestionGenerationBatch,
  listAiQuestionGenerationBatches,
  previewAiQuestionGenerationBatch,
  type AiQuestionBatchRequest,
  type AiQuestionBatchScope,
} from "../../../api/generated";
import { contentServiceClient } from "../../../api/client/contentServiceClient";
import { unwrap } from "../../../api/client/adminApi";
import { adminQueryKeys } from "../../../api/query-keys/adminQueryKeys";

const terminal = new Set(["COMPLETED","PARTIALLY_COMPLETED","FAILED","CANCELLED"]);
const uuidPattern=/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
export function AiQuestionBatchListPage() {
  const navigate=useNavigate();const [open,setOpen]=useState(false);const [status,setStatus]=useState("");
  const [scopeType,setScopeType]=useState<AiQuestionBatchScope["type"]>("TOPIC");const [scopeId,setScopeId]=useState("");
  const [questions,setQuestions]=useState(2);const [previewed,setPreviewed]=useState(false);
  const [difficulty,setDifficulty]=useState({EASY:30,MEDIUM:50,HARD:20});const [bloom,setBloom]=useState({REMEMBER:20,UNDERSTAND:40,APPLY:40});
  const scopeIds=scopeId.split(",").map(value=>value.trim()).filter(Boolean);
  const scopeIdValid=scopeType==="MULTIPLE_KNOWLEDGE_FACTS"?scopeIds.length>0&&scopeIds.every(value=>uuidPattern.test(value)):uuidPattern.test(scopeId.trim());
  const body=():AiQuestionBatchRequest=>({scope:{type:scopeType,id:scopeId||null,knowledgeFactIds:scopeType==="MULTIPLE_KNOWLEDGE_FACTS"?scopeId.split(",").map(v=>v.trim()).filter(Boolean):[]},language:"sv",questionsPerKnowledgeFact:questions,questionTypes:["SINGLE_CHOICE"],difficultyDistribution:difficulty,bloomDistribution:bloom,idempotencyKey:null});
  const batches=useQuery({queryKey:adminQueryKeys.ai.batches({status}),queryFn:()=>unwrap(listAiQuestionGenerationBatches({client:contentServiceClient,query:{page:0,size:50,status:status||undefined}})),refetchInterval:q=>q.state.data?.items.some(b=>!terminal.has(b.status))?3000:false});
  const preview=useMutation({mutationFn:()=>unwrap(previewAiQuestionGenerationBatch({client:contentServiceClient,body:body()})),onSuccess:()=>setPreviewed(true)});
  const create=useMutation({mutationFn:()=>unwrap(createAiQuestionGenerationBatch({client:contentServiceClient,body:{...body(),idempotencyKey:crypto.randomUUID()}})),onSuccess:b=>navigate(`/ai/question-batches/${b.id}`)});
  return <div className="page-stack"><header className="page-header"><div><p className="eyebrow">AI operations</p><h1>Question Generation Batches</h1><p>Generate and review grounded question proposals across approved content scopes.</p></div><button type="button" onClick={()=>setOpen(true)}>Generate AI Questions</button></header>
    <section className="card"><div className="actions"><label>Status<select aria-label="Batch status filter" value={status} onChange={e=>setStatus(e.target.value)}><option value="">All statuses</option>{["PENDING","RUNNING","PARTIALLY_COMPLETED","COMPLETED","FAILED","CANCELLING","CANCELLED"].map(v=><option key={v}>{v}</option>)}</select></label><button type="button" className="secondary" onClick={()=>batches.refetch()}>Refresh</button></div>
      {batches.isPending&&<p role="status">Loading generation batches…</p>}{batches.error&&<p role="alert" className="error">{batches.error.message}</p>}{batches.data?.items.length===0&&<p className="muted">No question-generation batches match these filters.</p>}
      {batches.data&&batches.data.items.length>0&&<div className="table-wrap"><table><thead><tr><th>Batch</th><th>Scope</th><th>Status</th><th>Created by</th><th>Requested</th><th>Generated</th><th>Failed</th><th>Reviewed</th><th>Accepted</th><th>Generation</th></tr></thead><tbody>{batches.data.items.map(b=><tr key={b.id}><td><Link to={`/ai/question-batches/${b.id}`}>{b.id.slice(0,8)}</Link><small>{new Date(b.createdAt).toLocaleString()}</small></td><td>{b.scopeLabel??b.scopeType}</td><td><span className="status-badge">{b.status.replaceAll("_"," ")}</span></td><td>{b.createdBy}</td><td>{b.requested}</td><td>{b.generated}</td><td>{b.failed}</td><td>{Number((b as Record<string,unknown>).reviewed??0)}</td><td>{Number((b as Record<string,unknown>).accepted??0)}</td><td>{b.generationProgressPercentage}%</td></tr>)}</tbody></table></div>}
    </section>
    {open&&<section role="dialog" aria-modal="true" aria-labelledby="batch-create-title" className="card split-confirmation"><h2 id="batch-create-title">Generate AI Questions</h2><label>Scope<select value={scopeType} onChange={e=>{setScopeType(e.target.value as AiQuestionBatchScope["type"]);setPreviewed(false);}}>{["KNOWLEDGE_FACT","MULTIPLE_KNOWLEDGE_FACTS","TOPIC","SUBJECT","EXAM_VERSION"].map(v=><option key={v} value={v}>{v.replaceAll("_"," ")}</option>)}</select></label><label>{scopeType==="MULTIPLE_KNOWLEDGE_FACTS"?"Knowledge Fact IDs (comma-separated)":"Scope ID"}<input aria-invalid={scopeId.length>0&&!scopeIdValid} value={scopeId} onChange={e=>{setScopeId(e.target.value);setPreviewed(false);}} /></label>{scopeId.length>0&&!scopeIdValid&&<p role="alert" className="error">Enter {scopeType==="MULTIPLE_KNOWLEDGE_FACTS"?"valid comma-separated Knowledge Fact UUIDs":"a valid scope UUID"}.</p>}<label>Questions per Knowledge Fact<select value={questions} onChange={e=>{setQuestions(Number(e.target.value));setPreviewed(false);}}><option value={1}>1</option><option value={2}>2</option><option value={3}>3</option></select></label><p>Swedish · Single choice. Distributions must total 100%.</p>
      <fieldset><legend>Difficulty distribution</legend>{(["EASY","MEDIUM","HARD"] as const).map(key=><label key={key}>{key}<input aria-label={`${key} difficulty percentage`} type="number" min={0} max={100} value={difficulty[key]} onChange={e=>{setDifficulty(current=>({...current,[key]:Number(e.target.value)}));setPreviewed(false);}}/></label>)}</fieldset>
      <fieldset><legend>Bloom distribution</legend>{(["REMEMBER","UNDERSTAND","APPLY"] as const).map(key=><label key={key}>{key}<input aria-label={`${key} Bloom percentage`} type="number" min={0} max={100} value={bloom[key]} onChange={e=>{setBloom(current=>({...current,[key]:Number(e.target.value)}));setPreviewed(false);}}/></label>)}</fieldset>
      {preview.data&&<div className="success" role="status"><strong>{preview.data.resolvedKnowledgeFactCount} eligible facts · {preview.data.estimatedProposalCount} proposals</strong><p>{preview.data.estimatedProviderCalls} provider calls · maximum {preview.data.configuredMaximum}</p>{preview.data.excludedKnowledgeFactCount>0&&<p>{preview.data.excludedKnowledgeFactCount} facts will be excluded.</p>}</div>}{(preview.error||create.error)&&<p role="alert" className="error">{(preview.error??create.error)?.message}</p>}<div className="actions"><button className="secondary" onClick={()=>{setOpen(false);setPreviewed(false);}}>Cancel</button><button className="secondary" disabled={!scopeIdValid||preview.isPending} onClick={()=>preview.mutate()}>{preview.isPending?"Previewing…":"Preview Batch"}</button><button disabled={!previewed||create.isPending||preview.data?.withinLimit===false} onClick={()=>create.mutate()}>{create.isPending?"Creating…":"Create Batch"}</button></div></section>}
  </div>;
}
