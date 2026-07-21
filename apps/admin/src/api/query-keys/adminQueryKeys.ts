export const adminQueryKeys={
 exams:{all:['exams'] as const,list:(filters:unknown)=>['exams','list',filters] as const,detail:(id:string)=>['exams','detail',id] as const,versions:(id:string)=>['exams',id,'versions'] as const},
 structure:{subjects:(id:string)=>['subjects',id] as const,topics:(id:string)=>['topics',id] as const},
 sources:{all:['sources'] as const,list:(filters:unknown)=>['sources','list',filters] as const,detail:(id:string)=>['sources','detail',id] as const},
 objectives:{all:['learning-objectives'] as const,list:(filters:unknown)=>['learning-objectives','list',filters] as const,detail:(id:string)=>['learning-objectives','detail',id] as const},
 facts:{all:['knowledge-facts'] as const,list:(filters:unknown)=>['knowledge-facts','list',filters] as const,detail:(id:string)=>['knowledge-facts','detail',id] as const,versions:(id:string)=>['knowledge-facts',id,'versions'] as const},
 questions:{all:['questions'] as const,list:(filters:unknown)=>['questions','list',filters] as const,detail:(id:string)=>['questions','detail',id] as const,versions:(id:string)=>['questions',id,'versions'] as const},
 reviews:{all:['reviews'] as const,summary:['reviews','summary'] as const,list:(filters:unknown)=>['reviews','list',filters] as const,detail:(id:string)=>['reviews','detail',id] as const},
 releases:{all:['releases'] as const,list:(filters:unknown)=>['releases','list',filters] as const,detail:(id:string)=>['releases','detail',id] as const,eligible:(id:string)=>['releases','eligible',id] as const}
 ,ai:{job:(id:string)=>['ai','jobs',id] as const,proposals:(id:string)=>['ai','jobs',id,'proposals'] as const}
};
